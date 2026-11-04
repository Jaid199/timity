package com.jaid.timity.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TimeEntry::class, Holiday::class, WorkPeriod::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timeEntryDao(): TimeEntryDao
    abstract fun holidayDao(): HolidayDao
    abstract fun workPeriodDao(): WorkPeriodDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `holidays` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`epochDay` INTEGER NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, `enabled` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_holidays_epochDay_name` ON `holidays` (`epochDay`, `name`)",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `work_periods` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `startEpochDay` INTEGER NOT NULL, `endEpochDay` INTEGER NOT NULL, " +
                        "`workStart` INTEGER NOT NULL, `workEnd` INTEGER NOT NULL, `standardHours` REAL NOT NULL)",
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `work_periods` ADD COLUMN `enabled` INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `note` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** A random v4 uuid built in SQLite, for rows that predate sync. Postgres accepts the same text. */
        private const val SQL_UUID = "lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || " +
            "'-' || substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))"

        /** Cloud sync: stable ids, change stamps, tombstones and a dirty flag on every table. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (table in listOf("entries", "holidays", "work_periods")) {
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `deletedAt` INTEGER")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `dirty` INTEGER NOT NULL DEFAULT 1")
                }
                db.execSQL("UPDATE `entries` SET `uid` = $SQL_UUID, `updatedAt` = COALESCE(`clockOut`, `clockIn`)")
                db.execSQL("UPDATE `work_periods` SET `uid` = $SQL_UUID, `updatedAt` = strftime('%s', 'now') * 1000")
                // Holiday uids derive from (day, name) and are filled in by the repository on first sync.
                db.execSQL("UPDATE `holidays` SET `updatedAt` = strftime('%s', 'now') * 1000")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_entries_uid` ON `entries` (`uid`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_work_periods_uid` ON `work_periods` (`uid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_holidays_uid` ON `holidays` (`uid`)")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "timity.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { instance = it }
        }
    }
}
