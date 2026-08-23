# Privacy Policy for Kaon Music

**Last Updated:** August 23, 2026

Kaon Music is built from the ground up as a **100% offline, privacy-first local audio player**. Your privacy is not merely a policy—it is an architectural guarantee enforced by our code.

---

## 1. Zero Network Access (No `INTERNET` Permission)

Kaon Music does **not** declare or request the `android.permission.INTERNET` permission in its Android Manifest. 

Because the application has zero network capabilities at the operating system level, it is physically impossible for the app to transmit your audio files, playlists, playback statistics, or metadata to any external server, cloud provider, or third party.

---

## 2. Information We Access & Why

Kaon Music operates entirely on local device storage:

* **Audio Files & Media Library**:
  * **On Android 13+ (API 33+)**: The app requests `READ_MEDIA_AUDIO` to scan and index local audio tracks on your device.
  * **On Android 8.0–12 (API 26–32)**: The app requests `READ_EXTERNAL_STORAGE` strictly to discover audio tracks and display album artwork.
* **Playback Notifications**:
  * **On Android 13+ (API 33+)**: The app requests `POST_NOTIFICATIONS` solely to display the media playback notification with interactive controls.
* **Wake Lock**:
  * Used to maintain uninterrupted audio playback while your screen is off.

---

## 3. Data Storage & Retention

* All playlists, favorites, play event counts, and cached metadata are stored locally in an on-device SQLite database (`kaon_music.db`).
* Data remains on your device and is never uploaded, synced, or shared externally.
* Deleting the app or clearing its application data immediately deletes all locally saved playlists, preferences, and play statistics.

---

## 4. Third-Party Services & Telemetry

* **Zero Analytics**: No Google Analytics, Mixpanel, or telemetry SDKs are included in the app.
* **Zero Crash Reporting SDKs**: No cloud-based crash reporters (such as Firebase Crashlytics or Sentry) are bundled.
* **Zero Advertising**: The application contains no ads, tracking pixels, or marketing libraries.

---

## 5. Contact & Source Code Verification

Kaon Music is open and transparent. You can inspect the source code, verify dependencies, and review the Android Manifest permissions directly in our repository.

For questions or feedback, open an issue on our GitHub project page or contact the project maintainers.
