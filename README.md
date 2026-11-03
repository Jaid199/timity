# Timity

A minimal Android time tracker for one employee: clock in, clock out, and see overtime pay.

## Screenshots

| Today | History | Reports | Settings | Holidays |
|---|---|---|---|---|
| ![Today, light](docs/screenshots/light_today.png) | ![History, light](docs/screenshots/light_history.png) | ![Reports, light](docs/screenshots/light_reports.png) | ![Settings, light](docs/screenshots/light_settings.png) | ![Holidays, light](docs/screenshots/light_holidays.png) |
| ![Today, dark](docs/screenshots/dark_today.png) | ![History, dark](docs/screenshots/dark_history.png) | ![Reports, dark](docs/screenshots/dark_reports.png) | ![Settings, dark](docs/screenshots/dark_settings.png) | ![Holidays, dark](docs/screenshots/dark_holidays.png) |

## Features

- **Today**: one-tap clock in / clock out with a live elapsed timer and today's sessions.
- **History**: filter by month or year, see hours worked, overtime hours, the hourly overtime rate and total overtime pay in MVR. Tap an entry to edit or delete it, or add one manually.
- **Reports**: overtime pay for the month or year, total and average hours, time worked inside vs outside your working-hours window, and a per-day (month) or per-month (year) breakdown.
- **Settings**: overtime rate (MVR per hour), standard hours per day (anything beyond counts as overtime), working-hours window, System / Light / Dark theme, weekly days off (Friday and Saturday by default), and holidays.
- **Holidays**: every hour worked on a holiday or weekly day off is overtime. Enter a free [Calendarific](https://calendarific.com) API key and tap Sync to pull Maldives holidays for this year and next. National and public holidays are on by default; government holidays and observances are listed but off. Toggle any holiday, tap one to edit its name or date, or add your own.

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

For each session: `overtime = max(0, worked - standardHours)`, except on an enabled holiday or weekly day off, where `overtime = worked`. Overtime pay is `overtime hours x rate`. Totals on the History and Reports screens sum every session in the selected month or year.

Holiday data is fetched once per sync and stored locally; the app makes no other network requests.
