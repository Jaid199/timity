# Firebase setup for Timity

Timity keeps working offline with Room. Firebase adds a cloud copy so the data survives a
lost phone and can later be shared with a second device or a web dashboard.

## 1. Create the project

1. [console.firebase.google.com](https://console.firebase.google.com) → Add project → name it `timity`.
   Analytics can stay off.
2. Add an Android app with package name `com.jaid.timity`. Download `google-services.json`
   and put it at `app/google-services.json` (it is git-ignored; never commit it).
3. **Authentication** → Sign-in method → enable **Anonymous** (zero-setup, one account per
   install) and optionally **Google** (lets you restore on a new phone).
4. **Firestore Database** → Create database → production mode → region `asia-south1` (Mumbai)
   is the closest to the Maldives.
5. **Rules** → paste the contents of `firestore.rules` from this repo → Publish.

The free Spark plan covers this app many times over: 1 GiB storage, 50k reads and 20k writes
per day. A heavy day of clocking in and out is well under a hundred writes.

## 2. Data model

Everything is scoped under the signed-in user, so the rules reduce to "you can only touch
your own documents".

```
users/{uid}
  ├─ settings/prefs                 one document
  ├─ entries/{entryId}              one document per work session
  ├─ holidays/{holidayId}           synced and custom days off
  └─ periods/{periodId}             Ramadan-style working-hour periods
```

Document ids are random Firestore ids. Each document carries `updatedAt` (epoch millis) so a
sync can resolve conflicts with "latest write wins".

### users/{uid}/settings/prefs

| field | type | notes |
|---|---|---|
| overtimeRate | number | MVR per hour |
| standardHours | number | used by the "beyond standard day" mode |
| overtimeMode | string | `OUTSIDE_WORKING_HOURS` or `BEYOND_STANDARD_HOURS` |
| workStart, workEnd | int | minutes from midnight |
| weekendDays | array of string | e.g. `["FRIDAY","SATURDAY"]` |
| theme | string | `SYSTEM`, `LIGHT`, `DARK` |
| holidaysSyncedAt | int | epoch millis |
| updatedAt | int | epoch millis |

The Calendarific key stays on the phone only.

### users/{uid}/entries/{entryId}

| field | type | notes |
|---|---|---|
| clockIn | int | epoch millis |
| clockOut | int or null | null while clocked in |
| note | string | comment entered at clock-out, max 500 chars |
| updatedAt | int | epoch millis |

Suggested index: `clockIn` descending (Firestore creates single-field indexes automatically).

### users/{uid}/holidays/{holidayId}

| field | type | notes |
|---|---|---|
| epochDay | int | days since 1970-01-01 |
| name | string | |
| type | string | Calendarific type, or `Custom`, `Sick day`, `Day off` |
| source | string | `calendarific` or `custom` |
| enabled | bool | |
| updatedAt | int | |

### users/{uid}/periods/{periodId}

| field | type | notes |
|---|---|---|
| name | string | e.g. Ramadan |
| startEpochDay, endEpochDay | int | inclusive |
| workStart, workEnd | int | minutes from midnight |
| standardHours | number | |
| enabled | bool | |
| updatedAt | int | |

## 3. How the app will sync

- Room stays the source of truth for the UI; every screen keeps reading from it.
- On each local write the row is mirrored to Firestore. Firestore's offline queue retries
  when the phone is back online.
- On sign-in and on app start the app pulls the user's collections and upserts rows whose
  `updatedAt` is newer than the local copy. Room rows get a `remoteId` column to map to the
  Firestore document id.
- Deletions are mirrored as document deletes.

## 4. Gradle pieces (added when `google-services.json` is in place)

```kotlin
// settings / root build.gradle.kts
id("com.google.gms.google-services") version "4.4.2" apply false

// app/build.gradle.kts
plugins { id("com.google.gms.google-services") }
dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
}
```
