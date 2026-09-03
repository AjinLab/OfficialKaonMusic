# Kaon Music — Target Architecture

**Status:** authoritative. Supersedes `Data_/ARCHITECTURE_ATTRIBUTED.md`, which is retained as
historical record only and must not be cited as authority for current behaviour.

**Scope:** this document is implementation-ready. Every rule is either already enforced by a test,
or names the test that must enforce it. Section 9 tracks which phase is done.

---

## 1. What Kaon is

An Android music application with a local MediaStore library **and** YouTube Music streaming,
targeting: offline playback, downloads, playlists/library, robust stream resolution, Media3
playback with system media controls, fast cold start, low track-start latency, smooth scrolling
at tens of thousands of tracks, and extensible theming.

It is maintained by a single developer. Architecture decisions optimise for that.

---

## 2. Modules

| Module | Type | Owns |
| --- | --- | --- |
| `:app` | application | Application, Activity, DI wiring, navigation, `core/*` + `feature/*` packages |
| `:core:model` | pure Kotlin/JVM | domain types, quality policy, error model. **Zero Android imports.** (planned, phase 4) |
| `:innertube` | android library | InnerTube models and page parsing — vendored from upstream Metrolist |
| `:baselineprofile` | test | baseline profile + startup macrobenchmark |

`core/data`, `core/playback`, `core/online`, `core/ui`, and `feature/*` stay **packages inside
`:app`**. Package discipline plus `ArchitectureBoundaryTest` gives the same enforcement as modules at
a fraction of the build cost. Revisit at 50k LOC or a second app target.

A separate `:core:extraction` module is no longer planned: extraction moved out of the repository
entirely into the external `innertubex` artifact (§7), leaving only a thin adapter that has no reason
to be its own module.

### 2.1 Dependency direction

```
:baselineprofile ──▶ :app ──▶ :innertube ──▶ innertubex ──▶ quickjs-kt
                       │
                       └──▶ :core:model (phase 4)
```

Inside `:app`:

```
app/ ──▶ feature/ ──▶ core/ui ──▶ core/data
                  └──▶ core/playback ──▶ core/data
                                     └──▶ core/online ──▶ :innertube
```

Rules, enforced by `ArchitectureBoundaryTest`:

1. No `core/*` package imports `com.kaon.music.app`.
2. `feature/*` does not import Room entities/DAOs or Media3 runtime types.
3. `core/ui` does not import `core.data.repository` or `KaonApplication`.
4. `core/online` does not import `core.data`, `core.playback`, `core.designsystem`, or `feature`.
5. `core/policy` is pure: no Android, no Room, no playback.

---

## 3. Domain model

One canonical representation per concept. No competing models.

### 3.1 Track identity

`TrackId` is always assigned by the database. **No identifier is ever derived from `hashCode()`.**

Source polymorphism is a sealed `MediaLocator`, never a `String` discriminator plus nullable
columns:

```
MediaLocator.LocalFile(mediaStoreId, relativePath)
MediaLocator.Remote(provider, externalId)
MediaLocator.Downloaded(fileName, origin, audio)
```

Artwork is a separate `ArtworkRef`. A playback locator never carries an artwork URL.

### 3.2 Playback state — partitioned by change frequency

This is the single most important shape rule in the codebase.

| Type | Changes | May be observed by |
| --- | --- | --- |
| `NowPlaying` | on track transition, play/pause, mode change | any ViewModel |
| `PlaybackQueue` | on queue mutation | player ViewModel only |
| `PlaybackProgress` | every 500 ms while playing | the leaf composable that draws it |

**`PlaybackProgress` must never share an emission with `PlaybackQueue`, and must never be a source
in a screen-level `combine`.** Violating this makes full-library work run twice per second on the
main thread.

`PlaybackQueue` holds `List<TrackId>`, not `List<Track>`. Resolving IDs to tracks is a repository
concern, never a per-timeline-change loop.

### 3.3 Quality

```
QualityTier   = AUTO | LOW | HIGH | HIRES | LOSSLESS
Purpose       = PLAYBACK | DOWNLOAD | PREWARM
QualityPolicy = tier + ceilingBitrate + allowedCodecs + requireLossless + allowMetered
QualityOutcome = Satisfied(spec) | Degraded(requested, chosen, reason) | Unsatisfiable
```

`Unsatisfiable` is load-bearing. YouTube serves no lossless audio to these clients, so a
"Lossless" setting is only honest if selection can refuse. Quality selection exists in **exactly
one** function.

---

## 4. Subsystem ownership

| Concern | Owner | Rule |
| --- | --- | --- |
| Player, audio focus, becoming-noisy, notification | `KaonPlaybackService` | Media3 owns focus; never re-implement |
| Transport truth | Media3 `Player` | the facade **derives**; it never writes state it did not observe from a callback |
| Queue truth | Media3 timeline | facade exposes `List<TrackId>` derived from it |
| Queue durability | `QueueSnapshotManager` | debounced write, flush on pause, restore only when player is empty |
| Library truth | Room | source of truth for **all** tracks — local, remote, downloaded |
| MediaStore | read-only upstream | never written to |
| Settings truth | DataStore, one `SettingsRepository` from `AppContainer` | no component constructs its own |
| Stream resolution | `:core:extraction` behind the `StreamResolver` port | playback never constructs a resolver |
| Quality policy | `:core:model` type, evaluated in `:core:extraction` | one function |
| Provider session | one `YouTubeSessionManager` with a readiness signal | first playback awaits it |
| Artwork | `core/data` repository + one configured Coil `ImageLoader` | `core/ui` receives a URL, never a repository |
| Downloads | `DownloadRepository` + WorkManager | never stored in or evictable by the Media3 stream cache |
| Fire-and-forget work | one `AppContainer.appScope` | no `object` owns a `CoroutineScope` |

---

## 5. Cross-cutting models

### 5.1 Errors

Failures cross layer boundaries as typed values, not exceptions — except `IOException` into
Media3, whose load-error policy requires it.

```
KaonError.Resolution = RateLimited | CipherStale | WifiOnlyBlocked | ProviderRejected(status) | NoPlayableFormat
KaonError.Playback   = SourceExpired | Unplayable(title) | DecoderUnsupported
KaonError.Network    = Offline | Http(code) | Timeout
KaonError.Library    = PermissionDenied | ScanFailed(cause)
KaonError.Unexpected(cause)
```

- `CancellationException` is rethrown before any generic catch, without exception.
- No `throw Exception(String)`. Untyped throws defeat retry classification.
- **No error is rendered as an empty result.** A failed search must not look like zero results.

### 5.2 Events

Two channels only. There is no EventBus.

- **State** — `StateFlow` for anything durable and observable.
- **Transient user-visible messages** — one app-level `UserMessageBus` (`SharedFlow`, `replay = 1`)
  held in `AppContainer` and collected once by the root scaffold.

Commands are method calls on the facade. Nothing else may be an event.

### 5.3 Logging

- `Timber.plant(if (BuildConfig.DEBUG) DebugTree() else ReleaseTree())`; `ReleaseTree` keeps
  WARN/ERROR only.
- `-assumenosideeffects` strips Timber `v`/`d` from release.
- Fixed tags: `playback`, `resolve`, `sync`, `queue`, `artwork`, `db`, `ui`.
- **Never log** poTokens, integrity tokens, BotGuard responses, `visitorData`, cookies,
  `signatureCipher`, or full stream URLs — in any build. Use `Redact` helpers.
- Every resolution log line carries the video id so failures correlate across layers.
- Enforced by `LoggingBoundaryTest`.

### 5.4 Concurrency

- Three scope kinds only: `viewModelScope`, `serviceScope`, one `AppContainer.appScope`.
- `runBlocking` appears **once** in production: the Media3 `ResolvingDataSource.Resolver` bridge,
  which is a synchronous callback on a loader thread. Nothing blocking may be called from inside it —
  the PoToken generator is `suspend` for exactly this reason.
- Repositories own no scopes. CPU mapping uses `flowOn(Dispatchers.Default)`; I/O uses
  `Dispatchers.IO`.
- `Application.onCreate` performs no file I/O, no JSON parsing, and no network calls. Initializers
  that need any of those store a context reference and do the work on `appScope`.

### 5.5 UI state

- Every ViewModel is obtained from a `ViewModelStore` (`viewModel(factory = …)`).
- Flows are collected with `collectAsStateWithLifecycle`.
- Navigation destinations and selections survive configuration change and process death
  (`rememberSaveable` / `SavedStateHandle`).
- Business policy — sorting, ranking, recommendation, queue construction — lives in pure functions,
  not ViewModels.
- Colour and typography come only from the theme. No `Color(0x…)` literal outside `core/ui/theme`.

### 5.6 Theme

Data-driven palettes, not plugins. **A runtime plugin/UI-extension system is permanently out of
scope**: it means loading third-party code with the app's permissions, session tokens, and
database; it requires a frozen public Compose API; and it conflicts with Play policy. All seven
prior model analyses reached the same conclusion independently.

`KaonPalette` is a serialisable data class (id, displayName, isDark, 15 colour roles). Built-ins
live in a registry map; user palettes may be imported as JSON. Adding a theme is adding data.

Prerequisite: eliminate hardcoded colours so a palette actually retextures the app.

---

## 6. Persistence

Room is the source of truth for **all** tracks.

- `tracks` gains `provider` + `external_id` with `UNIQUE(provider, external_id)`. Remote tracks are
  persisted with real autoincrement primary keys.
- `artwork_url` is its own column, so `content_uri` means one thing.
- **User-owned tables never CASCADE from a derived table.** `favorite_tracks` and `play_events`
  must not be destroyed by rebuilding `tracks`. `is_missing` soft-delete is the only lifecycle;
  hard purge is explicit and user-initiated.
- Every multi-statement write is inside `@Transaction`.
- No DAO query returns an unbounded row set to the UI. Lists are paged or `LIMIT`-ed.
- Every column in a `WHERE`/`ORDER BY` is indexed, or its absence is justified in a comment.
- Every schema bump ships a migration test asserting user-owned data survives.

---

## 7. Resolution boundary

Extraction is **not** Kaon code. `:innertube` is a vendored copy of upstream Metrolist's parsing
module, and the extraction engine below it (`YouTubeCipherService`, `InnerTubeExtractor`,
`PlayerClientDirector`, `ClientHealthMonitor`) lives in the external `innertubex` artifact. YouTube
rotates its player roughly monthly; Kaon does not maintain a competing cipher implementation.

The split:

| Layer | Owner | Responsibility |
| --- | --- | --- |
| `innertubex` (external) | upstream | cipher solving (QuickJS), client selection, per-client health, format selection, player config fetch |
| `:innertube` (vendored) | upstream, re-synced | InnerTube request/response models and page parsing |
| `core/online/YouTubeStreamExtractor` | Kaon | adapter: builds the extractor per transport generation, supplies the PoToken provider and config store, maps to Kaon's `PlaybackData` |
| `core/online/potoken` | upstream, adopted | BotGuard attestation WebView — stays app-side because it needs an Android WebView |
| `core/playback/YouTubeStreamResolver` | Kaon | resolution **policy**: coalescing, deadline budget, retry classification, generation-guarded cache, rate limit, Wi-Fi gate |

Rules:

- Kaon does not reimplement client fallback rotation or format selection. `innertubex` does both with
  health tracking; a parallel fixed rotation in the resolver was redundant and blind to that signal.
- Stream expiry is an **absolute timestamp**, computed from the CDN `expire` parameter when present.
  Expiry parsing must not depend on the Android framework, so it stays unit-testable.
- `PlaybackData` and `ResolvedStreamData` are Kaon-owned types. Neither `innertubex` nor InnerTube DTO
  types cross into `core/playback` or above.
- The extractor never provides the app's `Context`. The resolver receives a `ConnectivityManager`
  provider from `AppContainer`.
- Range-chunk metadata (`requireBoundedRange`, `rangeChunkSizeBytes`, `useRangeChunks`) must reach the
  `DataSpec`. Some CDN responses reject an unbounded GET; dropping these fails playback partway
  through with an error that looks like an expired URL.
- A second provider requires only a second implementation behind the same resolver policy.

### 7.1 Re-syncing from upstream

`:innertube`, `core/online/potoken`, and the `innertubex` version are re-synced from
`github.com/MetrolistGroup/Metrolist` rather than patched locally. On re-sync:

1. Copy `innertube/src/main/kotlin` and `app/src/main/kotlin/.../utils/potoken` verbatim; rewrite only
   the package declaration and the `BuildConfig` import.
2. Re-apply Kaon's redaction at the PoToken log sites (§5.3). Upstream logs the raw BotGuard response.
3. Re-check `YouTubeStreamExtractor` against the `InnerTubeExtractor` / `TokenProvider` signatures.
4. Copy upstream's `innertube` tests; do not maintain Kaon-local forks of them.


---

## 8. Caching

Every cache has one owner, a documented key, a documented invalidation trigger, and a size bound.

| Cache | Owner | Key | Lifetime |
| --- | --- | --- | --- |
| Resolved stream URLs | `:core:extraction` | locator + policy + purpose | CDN `expire` − 60 s |
| Provider config + player.js | `:core:extraction` | player hash | 6 h TTL + ETag |
| Metadata enrichment | `core/data` | provider + entity key | persisted; **negatives cached** |
| Artwork | one configured Coil `ImageLoader` | `albumId + sizeBucket` | app-managed, sized |
| Media3 streaming cache | `core/playback` | Media3-internal | evictable, size-capped |
| Permanent downloads | `core/data` | `TrackId` + `AudioSpec` | until user deletes |

**The download directory is never inside the Media3 stream cache.** LRU eviction must not be able
to delete a file the user explicitly downloaded.

A negative lookup is cached with a short TTL. Misses must not re-hit the network forever.

---

## 9. Migration phases

Each phase leaves the repository buildable and shippable.

| Phase | Objective | Status |
| --- | --- | --- |
| 0 | Release-log safety | **done** |
| 1 | Presentation ownership: `ViewModelStore`, saveable state, lifecycle collection | **done** |
| 2 | Playback state shape: split `NowPlaying`/`PlaybackQueue`/`PlaybackProgress` | **done** |
| — | Extraction re-sync: adopt upstream `:innertube` + InnerTubeX, delete the vendored cipher engine | **done** |
| 3 | Library scale: `flowOn`, SQL search/sort, Paging 3, composite index | pending |
| 4 | Identity: `:core:model`, `MediaLocator`, schema v7, drop CASCADE, transactions | pending |
| 5 | Quality policy: `QualityTier`/`Purpose`/`QualityOutcome` over the extractor | pending |
| 6 | Navigation, error model, theme registry, UI structure | pending |
| 7 | Downloads + offline | pending |

Feature prerequisites:

| Feature | Requires |
| --- | --- |
| Robust streaming | 0, 2, extraction re-sync |
| Downloads | 4, 5 → then 7 |
| Large-library support | 3 (needs 2 first) |
| Advanced quality selection | 5 |
| Themes | 6 |
| Spotify-derived import | 4 |
| Android Auto | 6 + `MediaLibraryService` |

---

## 10. Deliberately not built

| Item | Reason |
| --- | --- |
| Plugin-based UI / runtime code loading | security, Play policy, unmaintainable API surface. **Never.** |
| Modules beyond `:core:model` + `:core:extraction` | 14k LOC; package discipline suffices |
| Hilt/Dagger | `AppContainer` holds 10 singletons; revisit at ~15 |
| Room FTS | indexed `LIKE` with `LIMIT` exists and is not yet the bottleneck |
| Hi-Res/Lossless UI options | dishonest before `QualityOutcome.Unsatisfiable` exists |
| Media3 `CacheDataSource` | must not precede the download boundary |
| Crossfade | wired through settings with no player implementation; real Media3 work |
| WorkManager sync / `ContentObserver` | sync is not yet chunked, streamed, or cancellable |
| Repository interfaces for their own sake | one impl, one consumer each |
| A use-case layer | pure functions in `:core:model` solve ViewModel bloat without a layer |
| Crash reporting / analytics | privacy policy commits to none |

---

## 11. Testing boundaries

| Layer | Kind | Target |
| --- | --- | --- |
| `:core:model` | pure JVM | quality policy incl. `Unsatisfiable`; sort/mix/rank functions; locator mapping |
| `:core:extraction` | pure JVM | config parser (security boundary), recovery policy, format selection, expiry math |
| `core/data` | Room in-memory | migration per version pair, transaction atomicity, 50k-track benchmark |
| `core/playback` | fakes behind `StreamResolver` | state derivation, identical-queue detection, snapshot restore with mixed sources |
| `feature/*` | coroutines-test + fake repos | ViewModel state |
| `:app` | boundary tests | `ArchitectureBoundaryTest`, `LoggingBoundaryTest` — all packages, not just `feature/` |
| device | instrumented | live streaming, Macrobenchmark startup + frame timing during playback |

No test is excluded from running as a workaround for a build problem. Fix the build.
