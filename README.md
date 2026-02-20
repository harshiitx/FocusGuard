# FocusGuard

An Android app that monitors your app usage and blocks
distracting websites, enforcing screen time limits with
escalating timeouts and strong anti-cheat protections.

## Timer Escalation (Apps)

| Open # | Timeout      |
|--------|--------------|
| 1st    | 5 seconds    |
| 2nd    | 30 seconds   |
| 3rd    | 2 minutes    |
| 4th+   | 3 minutes    |

Blocked websites are **instant** — screen locks immediately
when a monitored domain is detected in any browser.

Open counts reset daily at midnight, or manually (protected
by the paragraph challenge).

## Features

### App Monitoring
- Lists all installed launchable apps with icons
- Search and filter by name or package
- Tap to toggle monitoring — removing requires paragraph
  challenge
- Escalating timer: 5s → 30s → 2m → 3m

### Website Blocking
- Add domains to block (e.g. youtube.com, instagram.com)
- Detects URLs in 18+ supported browsers (Chrome, Firefox,
  Brave, Edge, Samsung Internet, Opera, DuckDuckGo, etc.)
- Instant screen lock when a blocked site is detected
- Removing a blocked website requires paragraph challenge

### Paragraph Challenge (Anti-Cheat)
- All "destructive" actions require typing a long paragraph
  (minimum 200 characters) word-for-word
- Protected actions: removing apps/websites from monitoring,
  resetting counts, disabling cheating prevention,
  customizing the paragraph itself
- Default paragraph provided; fully customizable
- Creates enough friction to prevent impulsive overrides

### Cheating Prevention Mode
- When enabled, blocks access to Android Accessibility
  Settings and Device Admin Settings
- Prevents the user from disabling FocusGuard's permissions
- Disabling cheating prevention requires the paragraph
  challenge
- Combined with Device Admin (prevents uninstall), makes
  the app very hard to bypass

### Stats Dashboard
- Daily open counts per monitored app
- Daily block counts per website
- Shows what the next timeout will be for each app

### Other
- Material 3 / Material You dynamic theming
- Adaptive launcher icon (shield design)
- Daily auto-cleanup of old tracking data

## Permissions Required

| Permission            | Why                                       |
|-----------------------|-------------------------------------------|
| Accessibility Service | Detects foreground apps and browser URLs   |
| Device Admin          | Locks the screen when timer expires        |
| Query All Packages    | Lists all installed apps for selection     |

> **Note**: This app is for personal use. The QUERY_ALL_PACKAGES
> permission would require justification for Google Play, but
> is fine for sideloaded APKs.

## How to Build the APK

### Option 1: GitHub Actions (Easiest — No Local Setup)

1. Create a new GitHub repository
2. Push this project:
   ```bash
   cd FocusGuard
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/YOU/FocusGuard.git
   git push -u origin main
   ```
3. Go to **Actions** tab in your GitHub repo
4. The "Build APK" workflow runs automatically on push
5. Click the workflow run → download **FocusGuard-debug**
   artifact (contains the APK)
6. Transfer the APK to your phone and install

### Option 2: Android Studio

1. Install [Android Studio](https://developer.android.com/studio)
2. Open this project folder
3. Wait for Gradle sync to complete
4. **Build > Build Bundle(s) / APK(s) > Build APK(s)**
5. APK at `app/build/outputs/apk/debug/app-debug.apk`

### Option 3: Command Line

```bash
cd FocusGuard
gradle wrapper --gradle-version 8.4
chmod +x gradlew
./gradlew assembleDebug
```

### Online Build Services

- **Codemagic** (codemagic.io) — free tier available
- **Bitrise** (bitrise.io) — free tier available

### Why NOT PWA-to-APK Converters

Sites like PWABuilder, AppsGeyser, WebIntoApp only wrap web
pages. They cannot access Accessibility Service, Device Admin,
or read browser content — which this app requires.

## Installing & Setup

1. Transfer APK to your Android phone
2. Enable "Install from Unknown Sources"
3. Install and launch FocusGuard
4. **Setup tab**: Enable both permissions
   - Accessibility Service → find FocusGuard, toggle on
   - Device Admin → confirm
5. **Setup tab**: Enable Cheating Prevention (recommended)
6. **Apps tab**: Select apps to monitor
7. **Web tab**: Add website domains to block
8. Done!

## Customizing Timer Durations

Edit `getDelayForOpenCount` in `AppMonitorService.kt`:

```kotlin
private fun getDelayForOpenCount(count: Int): Long = when (count) {
    1 -> 5_000L       // 5 seconds
    2 -> 30_000L      // 30 seconds
    3 -> 120_000L     // 2 minutes
    else -> 180_000L  // 3 minutes
}
```

## Project Structure

```
FocusGuard/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/focusguard/app/
│   │   ├── MainActivity.kt          # Compose UI (4 tabs)
│   │   ├── AppMonitorService.kt     # Accessibility service
│   │   ├── ScreenLockAdmin.kt       # Device admin receiver
│   │   ├── AppUsageTracker.kt       # SharedPreferences store
│   │   └── InstalledApp.kt          # Data class
│   └── res/
│       ├── xml/                     # Service configs
│       ├── values/                  # Strings, colors, theme
│       ├── drawable/                # Vector icon
│       └── mipmap-anydpi-v26/       # Adaptive launcher icon
├── .github/workflows/build.yml      # Auto-build on push
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Known Limitations

- Website detection relies on reading the browser's URL bar
  via accessibility — if a browser uses a non-standard
  resource ID, it may not be detected
- Cheating prevention blocks Accessibility/Device Admin
  settings but a determined user could force-stop the app
  from the App Info page and then access settings
- Domain matching uses substring contains — "a.com" would
  match any URL containing "a.com" (e.g. "banana.com").
  Use specific full domains to avoid false positives

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Build**: Gradle 8.4 + AGP 8.2.2
