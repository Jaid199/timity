package com.jaid.timity.data.cloud

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.jaid.timity.BuildConfig
import com.jaid.timity.data.TimityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Two-way sync between Room and Supabase.
 *
 *  * Push: every row with `dirty = 1` is upserted by its uid; the flag is cleared unless the row
 *    changed again meanwhile.
 *  * Pull: rows whose server-stamped `synced_at` is past the per-table cursor are fetched in order;
 *    a remote row replaces the local one only if its `updated_at` is newer (latest write wins).
 *  * Settings are a single row keyed by the user and follow the same two rules.
 *
 * Sync runs shortly after any local change, on app start and resume, and on demand from Settings.
 * The account is an anonymous Supabase user created on first sync and kept in SharedPreferences.
 */
@OptIn(FlowPreview::class)
object SyncManager {
    sealed interface State {
        /** No Supabase URL / key in the build. */
        data object Disabled : State
        data class Idle(val lastSyncAt: Long) : State
        data class Syncing(val lastSyncAt: Long) : State
        /** [message] is null when the device is simply offline. */
        data class Error(val message: String?, val lastSyncAt: Long) : State
    }

    private const val TAG = "TimitySync"
    private const val PAGE = 500

    private val _state = MutableStateFlow<State>(State.Disabled)
    val state: StateFlow<State> = _state

    private lateinit var repo: TimityRepository
    private lateinit var prefs: SharedPreferences
    private var api: SupabaseApi? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    @Volatile private var attached = false

    fun attach(context: Context, repository: TimityRepository) {
        if (attached) return
        attached = true
        repo = repository
        prefs = context.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE)
        val url = BuildConfig.SUPABASE_URL
        val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        if (url.isBlank() || key.isBlank()) {
            _state.value = State.Disabled
            return
        }
        api = SupabaseApi(url, key)
        _state.value = State.Idle(lastSyncAt)
        scope.launch { requests.debounce(1_500).collect { runSync() } }
    }

    /** Coalesces bursts of local changes into one sync a moment later. */
    fun requestSync() {
        if (attached && api != null) requests.tryEmit(Unit)
    }

    /** Immediate sync, for the button in Settings. */
    fun syncNow() {
        if (attached && api != null) scope.launch { runSync() }
    }

    private var lastSyncAt: Long
        get() = prefs.getLong("last_sync_at", 0L)
        set(v) = prefs.edit().putLong("last_sync_at", v).apply()

    private suspend fun runSync() = mutex.withLock {
        val api = api ?: return
        _state.value = State.Syncing(lastSyncAt)
        try {
            repo.repairHolidayUids()
            authed(api) { token -> pushAll(api, token) }
            authed(api) { token -> pullAll(api, token) }
            authed(api) { token -> syncSettings(api, token) }
            lastSyncAt = System.currentTimeMillis()
            _state.value = State.Idle(lastSyncAt)
        } catch (e: SupabaseApi.HttpException) {
            Log.w(TAG, "sync failed: HTTP ${e.code} ${e.message}")
            _state.value = State.Error("${e.message} (HTTP ${e.code})", lastSyncAt)
        } catch (e: IOException) {
            Log.i(TAG, "offline: ${e.message}")
            _state.value = State.Error(null, lastSyncAt)
        } catch (e: Exception) {
            Log.e(TAG, "sync failed", e)
            _state.value = State.Error(e.message ?: e.javaClass.simpleName, lastSyncAt)
        }
    }

    // ---- auth ----------------------------------------------------------------------------------

    private fun loadSession(): SupabaseApi.Session? {
        val access = prefs.getString("access_token", null) ?: return null
        val refresh = prefs.getString("refresh_token", null) ?: return null
        val user = prefs.getString("user_id", null) ?: return null
        return SupabaseApi.Session(access, refresh, prefs.getLong("expires_at", 0L), user)
    }

    private fun saveSession(s: SupabaseApi.Session) {
        prefs.edit()
            .putString("access_token", s.accessToken)
            .putString("refresh_token", s.refreshToken)
            .putLong("expires_at", s.expiresAt)
            .putString("user_id", s.userId)
            .apply()
    }

    /**
     * The anonymous account is created once and then only refreshed. If refreshing ever fails the
     * error is surfaced rather than silently creating a second account, which would orphan the data.
     */
    private suspend fun ensureSession(api: SupabaseApi): SupabaseApi.Session {
        val current = loadSession() ?: return api.signUpAnonymously().also(::saveSession)
        return if (current.expiresSoon()) api.refresh(current.refreshToken).also(::saveSession) else current
    }

    private suspend fun <T> authed(api: SupabaseApi, block: suspend (token: String) -> T): T {
        val session = ensureSession(api)
        return try {
            block(session.accessToken)
        } catch (e: SupabaseApi.HttpException) {
            if (e.code != 401) throw e
            val refreshed = api.refresh(session.refreshToken).also(::saveSession)
            block(refreshed.accessToken)
        }
    }

    // ---- push ----------------------------------------------------------------------------------

    private suspend fun pushAll(api: SupabaseApi, token: String) {
        repo.entries.dirty().chunked(PAGE).forEach { chunk ->
            api.upsert("entries", JSONArray(chunk.map(CloudJson::entry)), token)
            chunk.forEach { repo.entries.clearDirty(it.uid, it.updatedAt) }
        }
        repo.holidays.dirty().filter { it.uid.isNotBlank() }.chunked(PAGE).forEach { chunk ->
            api.upsert("holidays", JSONArray(chunk.map(CloudJson::holiday)), token)
            chunk.forEach { repo.holidays.clearDirty(it.uid, it.updatedAt) }
        }
        repo.periods.dirty().chunked(PAGE).forEach { chunk ->
            api.upsert("periods", JSONArray(chunk.map(CloudJson::period)), token)
            chunk.forEach { repo.periods.clearDirty(it.uid, it.updatedAt) }
        }
    }

    // ---- pull ----------------------------------------------------------------------------------

    private suspend fun pullAll(api: SupabaseApi, token: String) {
        pull(api, token, "entries") { o ->
            val remote = CloudJson.entry(o)
            val local = repo.entries.byUid(remote.uid)
            when {
                local == null -> repo.entries.insert(remote)
                remote.updatedAt > local.updatedAt -> repo.entries.update(remote.copy(id = local.id))
            }
        }
        pull(api, token, "holidays") { o ->
            val remote = CloudJson.holiday(o)
            val local = repo.holidays.byUid(remote.uid)
            if (local == null) {
                // A row for the same day and name from before sync existed: the cloud row takes its place.
                repo.holidays.byDayAndName(remote.epochDay, remote.name)?.let { repo.holidays.hardDelete(it.id) }
                repo.holidays.insert(remote)
            } else if (remote.updatedAt > local.updatedAt) {
                repo.holidays.update(remote.copy(id = local.id))
            }
        }
        pull(api, token, "periods") { o ->
            val remote = CloudJson.period(o)
            val local = repo.periods.byUid(remote.uid)
            when {
                local == null -> repo.periods.insert(remote)
                remote.updatedAt > local.updatedAt -> repo.periods.update(remote.copy(id = local.id))
            }
        }
    }

    private suspend fun pull(api: SupabaseApi, token: String, table: String, apply: suspend (JSONObject) -> Unit) {
        val key = "cursor_$table"
        var cursor = prefs.getLong(key, 0L)
        while (true) {
            val rows = api.select(table, "select=*&synced_at=gt.$cursor&order=synced_at.asc&limit=$PAGE", token)
            if (rows.length() == 0) break
            for (i in 0 until rows.length()) {
                val o = rows.getJSONObject(i)
                try {
                    apply(o)
                } catch (e: Exception) {
                    // One bad row must not block everything behind it.
                    Log.w(TAG, "skipping $table row ${o.optString("id")}: ${e.message}")
                }
                cursor = maxOf(cursor, o.getLong("synced_at"))
            }
            prefs.edit().putLong(key, cursor).apply()
            if (rows.length() < PAGE) break
        }
    }

    // ---- settings ------------------------------------------------------------------------------

    private suspend fun syncSettings(api: SupabaseApi, token: String) {
        val userId = loadSession()?.userId ?: return
        var rows = api.select("settings", "select=*&limit=1", token)
        val snap = repo.settings.snapshot()
        // Push local changes, and on the very first sync push the current values so they are backed up too.
        if (snap.dirty || rows.length() == 0) {
            val updatedAt = if (snap.updatedAt > 0L) snap.updatedAt else System.currentTimeMillis()
            api.upsert("settings", JSONArray().put(CloudJson.settings(snap.settings, userId, updatedAt)), token)
            repo.settings.markPushed(snap.updatedAt)
            rows = api.select("settings", "select=*&limit=1", token)
        }
        if (rows.length() == 0) return
        val remote = rows.getJSONObject(0)
        val remoteUpdatedAt = remote.getLong("updated_at")
        if (remoteUpdatedAt > repo.settings.snapshot().updatedAt) {
            repo.settings.applyRemote(CloudJson.settings(remote), remoteUpdatedAt)
        }
    }
}
