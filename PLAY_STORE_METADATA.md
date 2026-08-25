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
> Elegant, high-performance music player for local files and online streaming.

### Full Description
```
Kaon Music is a high-performance, privacy-first music player crafted for music lovers who value speed, elegance, and control over both local audio libraries and online streaming.

KEY FEATURES:
• Complete Local Library: Browse and play your local audio files across 6 dynamic tabs: Tracks, Albums, Artists, Favorites, Recent, and Playlists.
• Online Discovery & Streaming: Seamlessly search, explore, and stream from YouTube Music with instant playback.
• Hybrid Universal Queue: Mix your local tracks and online streaming songs in the same seamless playback queue.
• Resilient Playlists: Create and manage custom playlists with instantaneous drag-to-reorder.
• Smart History & Stats: Pure SQL-powered Recently Played, Most Played, and Recently Added views without bloated cache tables.
• Full-Permutation Shuffle: Gapless position restore, persistent queue across process death, and audio focus ducking.
• Privacy-Centric Architecture: Zero telemetry, zero analytics tracking, and no compulsory user account login.
• Premium Modern Design: Fast, fluid Jetpack Compose interface with dark-mode aesthetic and edge-to-edge support.
• Full Media3 Engine: ExoPlayer foreground service, lockscreen media controls, and audio routing.
```

---

## Google Play Data Safety Declarations

| Category / Field | Declaration | Notes |
|:---|:---|:---|
| **Data Collection** | **Yes (Ephemeral Activity)** | Search queries are sent ephemerally to YouTube endpoints to fetch search results & streams. No search history is collected by Kaon. |
| **Data Sharing** | **No** | No user data is sold or shared with analytics or advertising networks. |
| **Network Access** | **Required for Streaming** | Uses `INTERNET` and `ACCESS_NETWORK_STATE` strictly for online search & streaming. |
| **Security Practices** | **Local Storage & HTTPS** | Local metadata is kept in on-device SQLite. Network requests use TLS/HTTPS encryption. |
| **Account Creation** | **Optional / None** | App is fully usable without any account or sign-in. |

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
