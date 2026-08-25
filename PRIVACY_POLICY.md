# Privacy Policy for Kaon Music

**Last Updated:** August 25, 2026

Kaon Music is a privacy-conscious hybrid music player designed for seamless local playback and optional online YouTube Music streaming.

---

## 1. Network Usage & Online Streaming

Kaon Music connects to the internet strictly when you perform online features (such as searching YouTube Music, discovering online tracks, or streaming online audio):

* **YouTube Music Search & Streaming**: When searching or streaming from YouTube Music, search terms, selected video IDs, and stream playback requests are transmitted directly to YouTube's public endpoints (via the open-source InnerTube API).
* **No Account Required**: The app operates anonymously by default and does not require you to provide a Google or YouTube account.
* **No Tracking or Telemetry**: We do not log, collect, track, or share your search queries, playback history, or listening habits on any remote servers.

---

## 2. Local Device Data & Storage

* **Local Audio Files**: The app requests media storage permissions (`READ_MEDIA_AUDIO` on Android 13+, `READ_EXTERNAL_STORAGE` on Android 8–12) strictly to index and play audio files residing on your local device.
* **Local Database**: All playlists, favorites, play history, and cached metadata are stored exclusively on your device in a local SQLite database (`kaon_music.db`).
* **Zero Cloud Syncing of Personal Files**: Your local audio files and personal database never leave your device.

---

## 3. Terms of Service & Streaming Notice

* YouTube streaming is provided for personal, non-commercial use.
* Kaon Music is an independent open-source player and is not affiliated with, endorsed by, or sponsored by YouTube or Google LLC.
* Online audio streams are played live without unauthorized permanent downloading or offline caching.

---

## 4. Third-Party Services & Analytics

* **Zero Analytics**: No third-party tracking, profiling, or telemetry SDKs are included.
* **Zero Advertising**: The application contains no ads or advertising identifiers.

---

## 5. Contact & Open Source Verification

Kaon Music is open and transparent. The source code, network clients, and permission declarations can be verified at any time on our GitHub repository.

