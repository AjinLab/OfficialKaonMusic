# Google Play Store Listing & Data Safety Metadata

## Application Details
* **App Name:** Kaon Music
* **Package Name:** `com.kaon.music`
* **Version Code:** `1`
* **Version Name:** `1.0.0`
* **Category:** Music & Audio
* **Content Rating:** Everyone (PEGI 3, USK 0)

---

## Store Descriptions

### Short Description (Max 80 characters)
> Lightweight, private, and powerful offline music player with smart playlists.

### Full Description
```
Kaon Music is a high-performance, privacy-first local music player crafted for music lovers who value speed, beauty, and control.

KEY FEATURES:
• Complete Local Library: Browse and play your audio files across 6 dynamic tabs: Tracks, Albums, Artists, Favorites, Recent, and Playlists.
• Resilient Playlists: Create and manage custom playlists with instantaneous drag-to-reorder. Your playlist memberships survive SD card unmounts and file moves without data loss.
• Smart History & Stats: Pure SQL-powered Recently Played, Most Played, and Recently Added views without bloated cache tables or background battery drain.
• Universal Playback Queue: Persistent playback state with full-permutation shuffle, gapless position restore, and crash-resilient queue persistence across process death.
• Pure Offline Privacy: 100% offline. Zero Internet permission (android.permission.INTERNET is completely absent). No telemetry, no ads, no background tracking.
• Premium Modern Design: Fast, fluid Jetpack Compose interface with dark-mode aesthetic, dynamic micro-animations, and full Android 15 edge-to-edge support.
• Full Media3 Engine: ExoPlayer foreground service, notification playback controls, and seamless audio focus handling.

Enjoy your music the way it was meant to be heard—uninterrupted, offline, and completely private.
```

---

## Google Play Data Safety Declarations

| Question | Declaration | Notes |
|:---|:---|:---|
| **Data Collection** | **No** | App does not collect any user data. |
| **Data Sharing** | **No** | App does not share data with any third parties. |
| **Network Access** | **None** | App does not declare `INTERNET` permission. |
| **Security Practices** | **Local Storage Only** | All data (playlists, preferences) is stored locally on the user's device in SQLite. |
| **Account Creation** | **Not Required** | App functions immediately without any login or account creation. |

---

## Keystore Backup & Release Procedure

1. **Keystore Location:** `kaon-release.jks` in project root (ignored by `.git`).
2. **Offline Backup Requirement:**
   - Copy `kaon-release.jks` and `release-keystore.properties` to an offline, encrypted storage device (e.g. encrypted USB drive / password manager vault).
   - **WARNING:** Losing `kaon-release.jks` permanently prevents releasing app updates to Google Play.
3. **Building Production Release Artifacts:**
   ```bash
   # Build Signed Release AAB (Google Play Store)
   ./gradlew bundleRelease
   # Output: app/build/outputs/bundle/release/app-release.aab

   # Build Signed Release APK (Direct GitHub Releases / Sideloading)
   ./gradlew assembleRelease
   # Output: app/build/outputs/apk/release/app-release.apk
   ```
