# SpendTrack v1 — completed core
Includes Room persistence, ₹9,000 dashboard, manual add/edit, automatic category list, Kotak notification parsing, full-notification SHA-256 duplicate protection, and an Add/Ignore confirmation activity.
IMPORTANT: verify the exact Kotak app package on your phone before enabling automatic detection. The placeholder is `com.kotak811`.
Android notification listener access must be enabled manually in Settings. The service only processes the allow-listed package.
# SpendTrack

SpendTrack is a local-first Android expense tracking app built with Jetpack Compose and Room Database. It automates expense logging by listening to banking transaction notifications and provides manual entry options with monthly budget calculations.

---

## Features

- **Automated Expense Detection**: Background `NotificationListenerService` parses incoming debit notifications and triggers an Add/Ignore dialog.
- **SHA-256 Deduplication**: Hash check prevents duplicate expense entries from repeated notifications.
- **Monthly Budget Dashboard**: Tracks monthly limit (₹9,000 baseline), displaying real-time spent vs. remaining balances.
- **Local Persistence**: Full offline support using SQLite via Android Room Database.
- **Material 3 UI**: Clean user interface built using modern Jetpack Compose components.

---

## Tech Stack & Architecture

- **Language**: Kotlin 2.0
- **UI Framework**: Jetpack Compose (Material 3)
- **Database**: Room Database 2.6.1 with KSP (Kotlin Symbol Processing)
- **Background Service**: Android `NotificationListenerService`
- **CI/CD**: GitHub Actions workflow generating debug APK artifacts on push

---

## Setup & Installation

### 1. Build via GitHub Actions
1. Fork or clone this repository.
2. Push any commit to the `main` branch to trigger `.github/workflows/build.yml`.
3. Download `SpendTrack-debug-apk` from the completed workflow run's **Artifacts** section.
4. Extract the `.zip` archive and install `app-debug.apk`.

### 2. Enable Permissions on Device
1. Open SpendTrack and tap **Kotak access** (or open *Settings → Special App Access → Notification Access*).
2. Enable **SpendTrack** to allow notification reading.
3. If restricted by Android/ColorOS, go to *App Info → Three Dots (⋮) → Allow restricted settings*.

---

## Configuration

To listen to other banking apps or SMS packages, edit the allow-list and regex in:
`app/src/main/java/com/example/spendtrack/KotakNotificationListener.kt`

- **Allowed Packages**:
  ```kotlin
  private val allowed = setOf("com.kotak811", "com.msf.kopl")
  
Pattern.CASE_INSENSITIVE: Ensures it matches whether the bank notification writes "SENT", "sent", or "Sent".
.matcher(full): Feeds the complete title and body text of the notification into the pattern to find a match.
