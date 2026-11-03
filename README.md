# Timity

A minimal Android time tracker for one employee: clock in, clock out, and see overtime pay.

## Features

- **Today**: one-tap clock in / clock out with a live elapsed timer and today's sessions.
- **History**: filter by month or year, see hours worked, overtime hours, the hourly overtime rate and total overtime pay in MVR. Tap an entry to edit or delete it, or add one manually.
- **Reports**: overtime pay for the month or year, total and average hours, time worked inside vs outside your working-hours window, and a per-day (month) or per-month (year) breakdown.
- **Settings**: overtime rate (MVR per hour), standard hours per day (anything beyond counts as overtime), working-hours window, and System / Light / Dark theme.

## Stack

- Kotlin + Jetpack Compose (Material 3)
- Room (SQLite) for time entries, stored on the device only
- DataStore for settings
- minSdk 26, targetSdk 36

## Build

Requires a JDK 17+ and the Android SDK (`ANDROID_HOME` set, or a `local.properties` with `sdk.dir`).

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Overtime calculation

For each session: `overtime = max(0, worked - standardHours)`. Overtime pay is `overtime hours x rate`. Totals on the History screen sum every session in the selected month or year.
