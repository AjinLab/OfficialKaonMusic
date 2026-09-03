# Upstream & External Dependency Pins

Tracks external and vendored upstream dependencies. See ARCHITECTURE.md §7 for the extraction
boundary and §7.1 for the re-sync procedure.

---

## InnerTube & YouTube streaming

| Component | Group & module | Pinned version / commit | Upstream |
|:---|:---|:---|:---|
| **InnerTubeX** — extraction engine: cipher, client selection, format selection | `com.github.MetrolistGroup.innertubex:innertubex` | `v0.5.1` | [innertubex](https://github.com/MetrolistGroup/innertubex) |
| **QuickJS-KT** — JS engine used for cipher solving; ships native `libquickjs.so` for 4 ABIs | `io.github.dokar3:quickjs-kt` | `1.0.14` (transitive via InnerTubeX) | [quickjs-kt](https://github.com/Dokar3/quickjs-kt) |
| **`:innertube`** — vendored module: InnerTube models + page parsing | internal module | `MetrolistGroup/Metrolist@0de0010` (2026-09-02) | [Metrolist innertube](https://github.com/MetrolistGroup/Metrolist/tree/main/innertube) |
| **`core/online/potoken`** — vendored: BotGuard attestation WebView | internal package | `MetrolistGroup/Metrolist@0de0010` (2026-09-02) | [Metrolist potoken](https://github.com/MetrolistGroup/Metrolist/tree/main/app/src/main/kotlin/com/metrolist/music/utils/potoken) |
| **Ktor** client core + OkHttp engine | `io.ktor:ktor-client-core`, `ktor-client-okhttp` | `3.5.2` | [Ktor](https://ktor.io/) |
| **Kotlinx Serialization** | `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.9.0` | [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) |
| **JDK desugaring** | `com.android.tools:desugar_jdk_libs_nio` | `2.1.4` | Android desugaring tools |

### Vendored code

`:innertube` and `core/online/potoken` are copied from upstream, not forked. The only local edits are:

- package declaration and `BuildConfig` import rewrites,
- redaction of token material at the PoToken log sites (ARCHITECTURE.md §5.3) — upstream logs the raw
  BotGuard response.

Re-sync rather than patch. The commit hash above is the pin; update it whenever the copy is refreshed.

### Removed

- **NewPipe Extractor** (`com.github.TeamNewPipe:NewPipeExtractor v0.24.4`). Its stream path was
  unreachable — `ENABLE_NEWPIPE_STREAM_INFO_EXTRACTOR` was `false` and `NewPipe.init` was never
  called, so every entry point failed on a null downloader. Upstream removed it in favour of
  InnerTubeX. This also drops its transitive Rhino dependency and `org.brotli:dec`, which was
  declared but never imported.
- **Vendored cipher engine** — roughly 4,300 lines across `YTPlayerUtils` and `core/online/cipher/*`,
  replaced by InnerTubeX's `YouTubeCipherService`, which solves the cipher in QuickJS rather than a
  WebView driving YouTube's own `player.js`.
- **`assets/solver/`** (398 KB of yt-dlp EJS solver, never referenced) and
  **`assets/player_configs.json`**. Player configuration is now fetched by InnerTubeX's
  `RemotePlayerConfigStore` and cached in SharedPreferences.

---

## Other pinned dependencies not in the version catalog

| Component | Coordinate | Note |
|:---|:---|:---|
| FFmpeg audio decoder | `org.jellyfin.media3:media3-ffmpeg-decoder:1.5.0+1` | third-party Media3 decoder fork; trails the Media3 pin (`1.5.1`) by a patch |
| Metadata enrichment | `io.github.famesjranko:musicmeta-core:0.9.2` | supplies the lyrics/artwork/metadata provider chain |
| Profile Installer | `androidx.profileinstaller:profileinstaller:1.4.1` | baseline profile installation |

---

## Build toolchain constraints

These four are coupled; changing one forces the others.

- **Gradle 9.6.1** (wrapper) caps **AGP at 8.8.0**. AGP 8.13 relies on
  `org.gradle.api.problems.internal.InternalProblems`, removed in Gradle 9.6.0.
- AGP 8.8 plus the pinned Compose BOM caps **Kotlin at 2.1.x**, while InnerTubeX ships newer Kotlin
  metadata. `:innertube` therefore passes `-Xskip-metadata-version-check`.
- InnerTubeX declares `minCompileSdk=37`, but `:innertube` compiles against SDK 36 with the
  `AarMetadata` check disabled: AGP 8.8 cannot read the local android-37 platform, whose
  `AndroidVersion.ApiLevel` is `"37.0"` rather than an integer. Safe here because the module uses no
  Android API — no `android.*` import, empty manifest, no resources.

Revisit all of the above when the wrapper moves to a Gradle release that AGP 9.x supports.

---

## Architectural & ToS notice

- YouTube streams are resolved on demand and played from volatile memory; nothing is written to
  permanent storage.
- Cipher solving is performed by InnerTubeX (QuickJS), using player configuration published by the
  Zemer cipher project.
