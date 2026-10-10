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
| `:core:kernel` | pure Kotlin/JVM | `KaonKernel`, `ServiceRegistry`, `PluginRegistry`, `PluginLoader`, plugin contracts. **Zero Android imports.** |
| `:app` | application | Application bootstrap, `MainActivity`, DI wiring, navigation, `core/*` + `feature/*` packages |
| `:core:model` | pure Kotlin/JVM | domain types, capability controllers (`PlayerController`, `LibraryController`), quality policy, error model. (Zero Android imports) |
| `:core:extraction` | android library | extraction adapter (`YouTubeStreamExtractor`), PoToken WebView, quality policy, redaction helpers |
| `:innertube` | android library | InnerTube models and page parsing — vendored from upstream Metrolist |
| `:baselineprofile` | test | baseline profile + startup macrobenchmark |

`core/data`, `core/playback`, `core/online`, `core/ui`, and `feature/*` stay structured packages with strict
architectural enforcement. Package discipline plus `ArchitectureBoundaryTest` gives boundary guarantees at
minimal build overhead.

Compile-time UI plugins (`DefaultUiPlugin`, `MinimalUiPlugin`) implement `UiPlugin` from `:core:kernel`,
consume capability controllers (`PlayerController`, `LibraryController`), and are bootstrapped by `KaonKernel`.

`:core:extraction` holds the extraction adapter (`YouTubeStreamExtractor`), the PoToken WebView, the quality policy,
and the redaction helpers, and depends only on `:innertube` (+ okhttp/okio for the BotGuard challenge). It must never
import `:app`, `core.data`, `core.playback`, or `feature/*` — enforced by `ExtractionModuleBoundaryTest`. The
`StreamResolver` port itself lives in `core/playback` (§7), because a port belongs to its consumer; `KaonStreamResolver`
binds it to the validated object implementation without rewriting it.

### 2.1 Dependency direction

```
:baselineprofile ──▶ :app ──▶ :core:kernel
                       │   ──▶ :innertube ──▶ innertubex ──▶ quickjs-kt
                       │          ▲
                       │          └──▶ :core:extraction
                       └──▶ :core:model
```

Inside `:app` and UI plugins:

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

Rules, enforced by `ArchitectureBoundaryTest`:

1. No `core/*` package imports `com.kaon.music.app`.
2. UI layers (`feature/*`, UI plugins) interact with playback and library ONLY through capability controllers (`PlayerController`, `LibraryController`) and domain models.
3. UI code does not import Room DAOs/entities, Media3 runtime types, or repositories directly.
4. `core/ui` does not import `core.data.repository` or `KaonApplication`.
5. `core/online` does not import `core.data`, `core.playback`, `core.designsystem`, or `feature`.
6. `core/policy` and `core/kernel` are pure: no Android, no Room, no playback.


---

## 3. Domain model

One canonical representation per concept. No competing models.

### 3.1 Track identity

`TrackId` is always assigned by the database. **No identifier is ever derived from `hashCode()`.**

Phase 4 shipped this as `core/data/model/MediaIdentity.kt` (types) + `core/data/db/TrackMapping.kt`
(the single row↔domain mapping). Schema v8 (`MIGRATION_7_8`, hand-written): `tracks` gained
`UNIQUE(provider, external_id)`, `media_store_id`/`relative_path` became nullable, `source`/
`youtube_video_id` were translated away, and `favorite_tracks`/`play_events` lost their destructive
CASCADE — user data now survives orphan purge by construction, with referential integrity enforced in
the repository (`ensurePersisted` before any favorite/history/playlist reference). A provider track is
persisted the moment something references it; `YtItemMapper` produces `TrackId.UNSAVED` tracks.
Measured limitation: v7 never stored provider thumbnails in Room, so migrated rows have NULL artwork
until the next provider upsert refreshes it.

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
  the PoToken generator is `suspend` for exactly this reason. The bridge counts every open as
  `bridge-initial` (first open of a track start) or `bridge-chunk` (a bounded-range re-open a warm
  cache entry serves in microseconds), and the service records `onMediaItemTransition` →
  `STATE_READY` as `transition-ready-*` — so queue-transition latency is measured, not assumed.
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

### 5.6 Theme and Compile-Time UI Plugins

**Runtime code loading is permanently out of scope**: loading external DEX/APKs at runtime with the app's permissions,
session tokens, and database is a critical security vulnerability, violates Google Play Developer Distribution
Agreements, and creates an impossible-to-support frozen runtime ABI.

Instead, Kaon employs a **compile-time plugin model**:
1. **UI Plugins** (`DefaultUiPlugin`, `MinimalUiPlugin`) implement `UiPlugin` from `:core:kernel` and ship
   compiled directly into the app binary.
2. Plugins are registered in `KaonKernel` and instantiated via `PluginLoader` using a deterministic build-time
   list — **zero reflection**.
3. UI plugins interact with the core player and library strictly through **capability controllers**
   (`PlayerController`, `LibraryController`, `SettingsController`) and domain models, never touching persistence
   DAOs or Media3 session controllers directly.
4. `MainActivity` acts as a minimal shell: it queries the kernel for the active UI plugin and delegates rendering
   to its `@Composable Content(...)` function. A throwaway minimal UI plugin (`MinimalUiPlugin`) confirms that UI
   presentations can be swapped without touching core code.

Within any UI plugin, palettes remain data-driven: `KaonPalette` is a serialisable data class (id, displayName,
isDark, 15 colour roles). Built-ins live in a registry map; user palettes may be imported as JSON. Adding a theme
is adding data.


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

### 7.1 The fast path

A normal track start must not retrieve `player.js`, execute JavaScript, or mint a PoToken. That is a
property of InnerTubeX's client catalog, not of Kaon code: its highest-scoring playback client
(`VISIONOS_0_1`) declares `signatureCipher=NEVER`, `nTransform=NEVER`, `webView=NONE`, and
`poToken=NONE`, and returns a progressive URL Media3 can open directly.

```
Track (already carries its videoId from search)
  → StreamSourceCache hit?                    → done, no network
  → rate-limit + Wi-Fi gate
  → InnerTubeX: VISIONOS direct URL           ← no player.js, no QuickJS, no WebView
  → absolute expiry from the CDN `expire` param
  → DataSpec (+ bounded range when required)  → Media3
```

Cipher and PoToken clients remain in the ladder below it; they are reached only when the cheap client
fails. `ExtractionFastPathTest` asserts this ordering, because a dependency bump could reorder the
catalog and reintroduce hundreds of milliseconds per resolve with no compile error.

Measured, not assumed (moto g05, 2026-09): a warm serve is ~0–1 ms; a steady-state VISIONOS
extraction is ~300–700 ms; the per-client histogram (`client:*` counters in `ResolutionMetrics`) is
the fallback-frequency observable — a run dominated by non-VISIONOS clients means the catalog
regressed even when every resolve still succeeds. A cold process can pay a session-change retry
(see §7.3).

Kaon does not implement a track→video search step: a YouTube `Track` is only ever produced from a
search result, so it already carries its `videoId` (`YtItemMapper`). The durable half of that identity
is persisted in the queue snapshot, so a streamed queue survives process death.

### 7.2 Resolution purpose

Foreground playback and speculative pre-resolution share one extractor and one rate-limit window, so
they are distinguished by `ResolvePurpose`:

- `PLAYBACK` — the user is waiting. Never yields, never skipped.
- `PREFETCH` — skipped entirely while any foreground resolution is in flight, and skipped when fewer
  than 8 rate-limit slots remain.

Every prefetch outcome is counted in `ResolutionMetrics` (`prefetch-warm`, the skip reasons,
`prefetch-failed`), so a single benchmark run reports the conversion rate: prefetches that became a
warm serve at the next track start versus prefetches that warmed nothing. A prefetch takes the same
`wifiOnly` facts the foreground resolve will use — the key is option-suffixed and the gate must not
be bypassed by the speculative request.

Purpose is deliberately **not** part of the coalescing key: a prefetch and a playback request for the
same track must converge on one extraction, and the prefetch result must be reusable.

### 7.3 The session-change race

On a cold process the InnerTube session bootstrap (`YouTubeSessionManager` visitor-data fetch,
`YouTubeStreamExtractor.prewarm`) runs concurrently with playback. innertubex's session guard
aborts any in-flight extraction when the session advances, surfacing as `CancellationException
("InnerTube session changed")` — observed on device. Because that cancellation does not cancel the
resolver's own job, `extractWithRetry` classifies it as transient and takes the one bounded retry
under the new session. Measured effect on the moto g05: a cold first resolve that would previously
fail now completes in ~4.2 s (the retry pays a second full extraction); a steady-state resolve is
~300–700 ms.

### 7.4 Re-syncing from upstream
`:innertube`, `core/online/potoken`, and the `innertubex` version are re-synced from
`github.com/MetrolistGroup/Metrolist` rather than patched locally. On re-sync:

1. Copy `innertube/src/main/kotlin` and `app/src/main/kotlin/.../utils/potoken` verbatim; rewrite only
   the package declaration and the `BuildConfig` import.
2. Re-apply Kaon's redaction at the PoToken log sites (§5.3). Upstream logs the raw BotGuard response.
3. Re-check `YouTubeStreamExtractor` against the `InnerTubeExtractor` / `TokenProvider` signatures.
4. Run `ExtractionFastPathTest` — it fails if the client ordering regressed.
5. Copy upstream's `innertube` tests; do not maintain Kaon-local forks of them.

### 7.5 Streaming Provider Abstraction & Honest Provider Review

Kaon defines an explicit `StreamingProvider` contract with clear capability flags:

```kotlin
interface StreamingProvider {
    val id: ProviderId
    val displayName: String
    val capabilities: StreamingCapabilities
    suspend fun resolveStream(locator: MediaLocator.Remote, purpose: ResolvePurpose): ResolvedStreamData
}

data class StreamingCapabilities(
    val supportsLossless: Boolean,
    val requiresAuthentication: Boolean,
    val supportsOfflineDownloads: Boolean,
    val rateLimitBudgetPerMinute: Int
)
```

#### Honest Assessment of YouTube Extraction:
1. **Extraction Fragility:** YouTube Music streaming depends on reverse-engineered endpoints, cipher solving via QuickJS,
   and BotGuard/PoToken attestation via an off-screen WebView. Upstream rotates client player scripts monthly, which
   inevitably causes extraction breakage until patched.
2. **Quality Reality:** YouTube Music serves opus/aac codecs capped at ~256 kbps. It does not provide lossless or
   hi-res streams to third-party scrapers. The quality policy reports `Unsatisfiable` when lossless is requested over
   YouTube.
3. **Policy and Distribution Risk:** Reverse-engineering proprietary streaming platforms presents ongoing Terms of
   Service and Google Play compliance liabilities.
4. **Architectural Isolation:** By placing YouTube behind `StreamingProvider` and `StreamResolver`, Kaon isolates
   these liabilities. Alternative providers (Subsonic, Jellyfin, Navidrome, official Web APIs, or local NAS streaming)
   can be plugged in without changing playback infrastructure, Room schemas, or UI plugins.
5. **Spotify Integration Scope:** Spotify integration is strictly **playlist metadata import only**. Tracks are matched
   against local library tracks or provider tracks by ISRC / title / artist / duration. Unresolved tracks are surfaced
   transparently to the user; Kaon never attempts to spoof proprietary Spotify audio encryption.

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

### 8.1 Library access shape (phase 3)

The UI never observes the whole track table. Every library list is one of:

- **Paged** (`Paging 3` + Room `PagingSource`) — tracks, favorites, albums, artists, playlist
  members. The `TrackRow` projection resolves favorite state in SQL (`EXISTS`), so a favorite toggle
  does not re-emit a full id list through a `combine`. Sorting and search are SQL over the
  `is_missing`-prefixed composite indexes (schema v7); the DAO returns `PagingSource` per sort order
  rather than binding a runtime `ORDER BY` column.
- **Bounded by `LIMIT`** — the Home feed rows (`observeRecentAlbums/Artists`, favorite sample), the
  recent carousel, and the queue built from a list context (`getQueueForContext`, capped at
  `MAX_QUEUE_SIZE = 2_000` so a tap in a 50k library never materialises the library into the
  Media3 timeline).
- **A count** — header totals and empty-state decisions come from `COUNT(*)` queries, never from
  `.size` on a materialised list.

`observeAllActiveTracks` remains, but is sync-only: `SyncEngine` genuinely must diff every row.
Entity→domain mapping runs on `Dispatchers.Default` via an injected `mappingDispatcher`
(`runTest`-injected in tests); Paging loads are already off-main, and its `PagingData` flow is not
`flowOn`-ed. Measured on moto g05: first page (~50 rows) costs 13–30 ms whether the library holds
1k or 50k tracks — bounded access is the point, not a faster full scan.

### 8.2 Deliberately absent in phase 3

Room FTS (the indexed-LIKE search measured 10–79 ms per page up to 50k tracks; FTS buys nothing the
benchmark justifies), a `LibraryRepository` interface (one implementation, several consumers), and
paging for fixed-size carousels.

---

## 9. Migration phases

Each phase leaves the repository buildable and shippable.

| Phase | Objective | Status |
| --- | --- | --- |
| 0 | Release-log safety | **done** |
| 1 | Presentation ownership: `ViewModelStore`, saveable state, lifecycle collection | **done** |
| 2 | Playback state shape: split `NowPlaying`/`PlaybackQueue`/`PlaybackProgress` | **done** |
| — | Extraction re-sync: adopt upstream `:innertube` + InnerTubeX, delete the vendored cipher engine | **done** |
| 3 | Library scale: `flowOn`, SQL search/sort, Paging 3, composite index | **done** |
| 4 | Identity: `TrackId`/`MediaLocator`/`ProviderId`, schema v8, drop CASCADE, transactions | **done** |
| 5 | Quality policy: `QualityTier`/`Purpose`/`QualityOutcome` over the extractor | **done** |
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
| Runtime dynamic code loading (DEX/APK) | security, Play policy, sandbox hazards. Compile-time UI plugins via `:core:kernel` are supported instead. |
| Modules beyond `:core:kernel`, `:core:model`, `:core:extraction` | package discipline plus boundary tests suffice for internals |
| Hilt/Dagger | `AppContainer` + `KaonKernel.services` holds singletons cleanly |
| Room FTS | indexed `LIKE` with `LIMIT` exists and is not yet the bottleneck |
| Hi-Res/Lossless UI options | dishonest before `QualityOutcome.Unsatisfiable` exists |
| Media3 `CacheDataSource` | must not precede the download boundary |
| Crossfade | wired through settings with no player implementation; real Media3 work |
| WorkManager sync / `ContentObserver` | sync is not yet chunked, streamed, or cancellable |
| Repository interfaces for their own sake | capability controllers (`PlayerController`, `LibraryController`) define the boundary |
| A use-case layer | pure functions and capability controllers solve ViewModel bloat without a layer |
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
| device | instrumented | live streaming, prefetch→cache-hit conversion (`PrefetchConversionInstrumentedTest`), Macrobenchmark startup + frame timing during playback |

No test is excluded from running as a workaround for a build problem. Fix the build.
