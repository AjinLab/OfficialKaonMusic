# Official Kaon Music

[![Android CI](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/Playback-Media3%20ExoPlayer-orange.svg)](https://developer.android.com/guide/topics/media/media3)

A fast, reliable, local-first Android music player with seamless streaming capabilities, built on a modular **microkernel architecture** with **compile-time UI plugins** and **capability controllers**.

---

## Architecture Overview

Kaon is engineered with a strict separation of concerns, prioritizing playback reliability, library truth, and architectural honesty:

```
MainActivity ──▶ KaonKernel (active UiPlugin) ──▶ Capability Controllers
                                                        │
                      ┌─────────────────────────────────┴─────────────────────────────────┐
                      ▼                                                                   ▼
               PlayerController                                                   LibraryController
                      │                                                                   │
               PlaybackFacade                                                       TrackRepository
                      │                                                        Playlist / History Repos
                      ▼                                                                   │
           KaonPlaybackService                                                            ▼
           MediaSession / Media3                                                      Room Database
```

### 1. Pure Microkernel (`:core:kernel`)
- **`KaonKernel`**: Central bootstrap engine that coordinates core services and plugin lifecycles.
- **`ServiceRegistry`**: High-performance, thread-safe service container with typed keys (`ConcurrentHashMap<KClass<*>, Any>`).
- **`PluginRegistry` & `PluginLoader`**: Deterministic build-time plugin loader (no runtime reflection, no dynamic DEX/APK security risks).

### 2. Capability Controllers
Presentation layers (`feature/*`, UI plugins) never directly access raw Room DAOs, Media3 playback session controllers, or implementation repositories. All operations flow through clear capability boundaries:
- **`PlayerController`**: Transport control, playback commands, partitioned playback state streams (`NowPlaying`, `PlaybackQueue`, `PlaybackProgress`).
- **`LibraryController`**: Local & remote media queries, playlists, favorites, library synchronization, and context queue generation.
- **`SettingsController`**: User preferences, audio/theme settings, and streaming options.

### 3. Compile-Time UI Plugin Architecture
- UI plugins implement `UiPlugin` and are registered into the kernel at build time.
- `MainActivity` acts as a host shell that delegates rendering to the active plugin (`DefaultUiPlugin`, `MinimalUiPlugin`, etc.).
- Swapping UI implementations requires zero changes to core playback, library, or data layers.

### 4. Playback State Partitioning
To maintain smooth 60fps scrolling on massive libraries (50k+ tracks), playback state emissions are partitioned by update frequency:
- `NowPlaying`: Emits only on track transitions, play/pause, or playback mode changes.
- `PlaybackQueue`: Emits only on queue reordering or mutations.
- `PlaybackProgress`: High-frequency ticks (500ms) observed strictly by the leaf player composable, never at screen level.

### 5. Stable Track Identity & Local Truth
- **`TrackId`**: Assigned deterministically by Room. No identifier is ever derived from `hashCode()`.
- **`MediaLocator`**: Type-safe sealed hierarchy (`LocalFile`, `Remote`, `Downloaded`) distinguishing audio location from artwork (`ArtworkRef`).
- **Data Integrity**: Soft-delete semantics (`is_missing`) ensure user playlists, history, and favorites survive MediaStore rescans and orphan purges.

### 6. Streaming Provider Contract
Streaming endpoints are isolated behind the `StreamingProvider` abstraction with explicit capability reporting (`supportsLossless`, `requiresAuthentication`, `supportsOfflineDownloads`):
- **YouTube Music**: Implemented as an extraction adapter via vendored `:innertube` and external `innertubex`. Hi-Res/Lossless tiers report `Unsatisfiable` honestly when requested over lossy streaming feeds.
- **Extensible**: Alternate providers (Subsonic, Jellyfin, official Web APIs) can be added without altering playback orchestration.
- **Spotify**: Metadata-only playlist import matched to local or provider tracks; unresolved tracks are surfaced explicitly.

---

## Project Structure

| Module | Type | Description |
| --- | --- | --- |
| **`:core:kernel`** | Pure Kotlin/JVM | Microkernel (`KaonKernel`, `ServiceRegistry`, `PluginRegistry`, `PluginLoader`, plugin lifecycles). |
| **`:app`** | Android Application | App entry point, `MainActivity`, DI containers, capability controller implementations, UI components, Media3 service. |
| **`:core-extraction`** | Android Library | Stream extraction adapter, PoToken WebView, Quality Policy. |
| **`:innertube`** | Android Library | Vendored Metrolist parsing models for YouTube Music metadata. |
| **`:baselineprofile`** | Test / Profile | Macrobenchmarks, startup profiles, and frame timing instrumentation. |

---

## Building and Testing

### Prerequisites
- JDK 17 (Gradle build daemon) with JDK 21+ toolchain for test runner
- Android SDK 35 (Android 15)

### Running Unit Tests
```bash
# Run pure JVM microkernel tests
./gradlew :core:kernel:test

# Run all Android and domain unit tests
./gradlew testDebugUnitTest
```

### Architecture Boundary Tests
Architecture boundaries are automatically verified on every build via `ArchitectureBoundaryTest`:
```bash
./gradlew :app:testDebugUnitTest --tests "com.kaon.music.core.ArchitectureBoundaryTest"
```

---

## License
Open source under the Apache 2.0 License.
