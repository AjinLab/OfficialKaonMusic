# Kaon Music — Synthesized Architecture with Model Attribution

> **Status:** This is the authoritative architecture document for Kaon Music.
> Model attributions are historical provenance, not architectural authority.
> The final architecture is a synthesis — no individual model is authoritative.
> **Final Kaon interpretation > model attribution.** If a model name and a Final Kaon interpretation ever appear to conflict, the Final Kaon interpretation wins.

## Decision Classification

Every decision in this document carries one of three labels:

| Label | Meaning | Implementation guidance |
|-------|---------|------------------------|
| **SETTLED** | Architectural invariant. Changing this requires explicit review and justification. | Implement as written. |
| **PROVISIONAL** | Current best implementation choice, but subject to validation or revision as the project matures. | Implement as written, but do not treat as immutable. Revisit when named conditions are met. |
| **OPEN** | Product or design decision not yet locked. | Do not implement until decided. Flag if blocking. |

Do not treat PROVISIONAL decisions as equally immutable to SETTLED decisions. A SETTLED decision requires a strong reason to change; a PROVISIONAL decision requires only evidence that a better option exists.

---

## 1. Core Architectural Philosophy

### 1.1 Architectural Style

**Label:** SETTLED
**Take from:** K3 + GLM + Qwen + Opus

Use a modular monolith: one Gradle `:app` module with strong package boundaries.
Do not start with multiple Gradle modules.
The architecture should be composed of a small number of meaningful boundaries rather than a large Clean Architecture hierarchy.

Recommended conceptual boundaries:

- Playback
- Library
- Persistence/Data
- Artwork
- UI

No generic `core`, `common`, `kernel`, or `platform` package unless something genuinely needs it.

**Primary source:** K3
**Supporting sources:** GLM, Qwen, Opus, DeepSeek, Meta

**Why this was selected:**
K3 explicitly recommends one module and provides concrete extraction triggers — measurable conditions under which modularization earns its cost. This is stronger than a vague "split later" because it creates an auditable commitment: extraction happens when build times become painful, a boundary is violated in review, or `core.playback` needs isolated testing. Six of seven models independently arrived at the single-module recommendation, which is strong convergent evidence.

**What was not taken:**
Gemini proposes multi-module from day one (`:core:domain`, `:core:data`, `:core:playback`, `:core:designsystem`, `:feature:library`, `:feature:player`). Rejected because: for a single developer, multi-module Gradle adds build-config complexity, KSP/Hilt cross-module wiring overhead, and premature API surface stabilization. The compiler enforcement benefit does not outweigh the iteration cost at this scale.

**Rejected alternative:** Gemini — multi-module structure
**Reason for rejection:** Build complexity tax without team-scaling benefit. Package boundaries enforced by discipline are sufficient for one developer; modules can be extracted mechanically later because the package structure maps 1:1 to plausible future modules.

**Final Kaon interpretation:**
One Gradle `:app` module. Package boundaries designed so that each package could become a Gradle module through mechanical extraction. Extraction triggers (written down, honored later):
1. Incremental build times exceed tolerance (~2 min)
2. A package boundary is actually violated in review
3. `core.playback` or `core.data` needs isolated testing that the single module impedes
4. First extraction candidate: `core.playback`

---

### 1.2 Architectural Priority

**Label:** SETTLED
**Take from:** Opus

Use this priority order when architectural decisions conflict:

1. **Playback reliability**
2. → Library correctness
3. → Performance/resource discipline
4. → UI/features/customization

Do not sacrifice playback correctness for architectural elegance.

**Primary source:** Opus
**Supporting sources:** K3, Meta

**Why this was selected:**
Opus is the only model that states this hierarchy as an explicit, ranked principle. K3 implicitly aligns ("Playback reliability drives the service/facade split") but does not formalize the ranking. Meta states "If playback fails, nothing else matters" which agrees, but does not extend the hierarchy beyond playback. Opus's formulation creates a decision tiebreaker: when two approaches conflict, the one that preserves playback reliability wins, even if the alternative is architecturally cleaner.

**What was not taken:**
No model explicitly disagreed with this ordering. The risk is that without stating it, teams naturally invert the hierarchy — building sophisticated theming or recommendation systems before playback and library sync are solid. Opus names this risk directly.

**Final Kaon interpretation:**
When any architectural decision creates tension between layers, resolve in the order: playback → library → performance → UI/features. This priority is a design constraint, not a development sequence — all layers are built in parallel, but conflicts are resolved by this ranking.

---

### 1.3 Anti-Overengineering Rule

**Label:** SETTLED
**Take from:** Opus + GLM

**Rule:**
> No concrete V1 requirement + no concrete data model = no interface, module, or table.

Do not create infrastructure merely because something might exist in the future.

**Primary source:** Opus
**Supporting source:** GLM, Meta, DeepSeek

**Why this was selected:**
Opus states this principle in the most precise and actionable form: "If a feature does not have a concrete V1 requirement and a concrete data model, it should not create an interface, module, or table now." GLM reinforces it through its schema discipline — the derived-vs-user-owned data split implicitly embodies this by refusing to create tables for data that doesn't yet exist. Meta reaches the same conclusion independently: "If you build abstractions for them now, you will maintain them for 6 months before you have a real requirement."

**What was not taken:**
GLM was originally listed as the primary source in earlier drafts. However, GLM expresses this through specific examples (no event bus, no plugin registry) rather than as a general principle. Opus provides the general rule that subsumes GLM's examples. Both contributions are preserved: Opus supplies the rule, GLM supplies the specific applications.

**Final Kaon interpretation:**
Before creating any interface, module, table, or abstraction, apply this test:
1. Does this serve a concrete V1 requirement? If no → do not create it.
2. Does this have a concrete data model? If no → do not create it.
3. Can the future need be served by adding the abstraction later without schema upheaval? If yes → defer it.

---

## 2. Track Identity

**Label:** SETTLED
**Decision:** `trackId` is generated and owned by Kaon, remains stable across MediaStore `_ID` changes, and is never regenerated merely because a file was renamed, moved, or temporarily disappeared. MediaStore `_ID` is stored strictly as a synchronization/matching key, not the identity. Playable URIs are derived at playback time, never stored as identity.

**Primary source:** K3
**Supporting sources:** Qwen, Meta

**Why this was selected:**
K3 is the only model that fully addresses the "music belongs to the user" promise at the identity level. K3's design ensures that favorites, history, and playlists survive renames, moves, and MediaStore provider rebuilds — the exact scenarios that break every simpler identity scheme.

**Formal Identity Invariant:**
> **INVARIANT:** `trackId` is generated and owned by Kaon, remains stable across MediaStore `_ID` changes, and is never regenerated merely because a file was renamed, moved, or temporarily disappeared.

**Deterministic Re-linking Hierarchy (Locked Specification):**
When a stored MediaStore ID vanishes during sync, candidates in `is_missing = 1` state are evaluated against a strict 2-tier hierarchy:
1. **Tier 1 (Path + Title + Artist)**: Normalized `relativePath` + normalized `title` + normalized `artist` + duration ($\pm 1000\text{ms}$). Designed for MediaStore ID churn (OS upgrades, provider rebuilds, media storage cache clears) where file location is unchanged.
2. **Tier 2 (Metadata + Exact Size)**: Normalized `title` + normalized `artist` + normalized `album` + duration ($\pm 1000\text{ms}$) + exact `sizeBytes`. Designed for renamed or moved files.

**Ambiguity & Duplicate Protection Rule:**
If multiple candidates match at the qualifying tier, or if no candidate meets the metadata confidence threshold, **re-linking is aborted**. The scan item is inserted as a **brand-new track** with a fresh `trackId`. Two tracks with identical duration and size can never be silently merged or cross-linked.

**Sync-time rule:**
1. Unambiguous match in hierarchy → re-link (update stored MediaStore ID, set `is_missing = 0`).
2. Ambiguous or none → mark orphaned/missing, retain all user data, hide from library UI.
3. Purge orphaned entries after a 30-day retention window.

---

## 3. Playback State Ownership

**Label:** SETTLED
**Decision:** The Media3 `Player` inside the `MediaSessionService` is the single source of truth for runtime playback state. UI communicates only through a process-scoped playback facade exposing immutable `StateFlow`s and intent methods. Media3 owns audio focus (`setAudioAttributes(..., true)`) and becoming-noisy (`setHandleAudioBecomingNoisy(true)`) — no custom focus code.

**Primary source:** K3
**Supporting sources:** GLM, Qwen, Opus, DeepSeek, Gemini

**Why this was selected:**
All models except Meta converge on Media3 as the runtime source of truth. K3 provides the most complete implementation specification: the process-scoped facade pattern, the `StateFlow` exposure, the hard rule that the facade exposes player-derived state and intents only (preventing god-object bloat), and the explicit delegation of audio focus and becoming-noisy to Media3's built-in handlers.

GLM contributes a critical framing that strengthens the K3 design: "Kaon observes Media3; Kaon never mirrors Media3." This statement is more than an implementation choice — it is an architectural principle that prevents the most common failure mode in Media3 apps (building a parallel `PlayerState` in Room or a ViewModel that mirrors position/isPlaying/queue, then drifts out of sync).

**What was not taken:**

**Rejected alternative:** Meta — Kaon-owned `PlaybackStateFlow` as source of truth, Media3 as dumb renderer
**Reason for rejection:** Meta proposes that "Kaon owns PlaybackStateFlow, pushes single item or small queue to Media3. Media3 events update Kaon state via single reducer." This creates exactly the dual-state-machine that six other models identify as the highest architectural risk. Media3 already handles MediaSession, notification, external controls, audio focus, queue timeline, and position state. Building a Kaon-owned state machine that commands Media3 doubles the state surface and guarantees eventual desync, especially across process death boundaries.

**Synthesis:**
K3 supplies the runtime ownership rule and facade pattern. GLM supplies the "observe, never mirror" principle. The final Kaon rule combines both: the facade observes the Player via `Player.Listener` and exposes derived `StateFlow`s — it does not maintain independent state.

**Final Kaon interpretation:**
- Media3 `Player` owns: current item, position, play/pause state, shuffle/repeat modes, the live queue (timeline), audio focus, becoming-noisy.
- The playback facade is a process-scoped singleton exposing immutable `StateFlow`s and intent methods (play, pause, enqueue, playNext, move, remove, seek).
- UI never touches `MediaController` or `Player` directly.
- The facade has a hard scope rule: it exposes player-derived state and intents only, no library logic. If it starts growing, the design is wrong.
- Position/isPlaying are observed at UI-appropriate cadence via `Player.Listener` + polling for position — a solved problem.

---

## 4. Queue Persistence

**Label:** SETTLED
**Decision:** The service persists a queue snapshot to Room: ordered track IDs, current index, position, repeat/shuffle modes. Writes are debounced on queue change and flushed on pause/stop. Restore rule: on service start, restore only if the player is empty AND no controller request has started playback — restore always loses to explicit user action. Orphaned tracks are skipped during restore.

**Primary source:** K3
**Supporting sources:** GLM, Qwen, Opus

**Why this was selected:**
K3 provides the most complete queue persistence design, including the critical race rule: "restore only if the player is empty and no controller request has started playback." This rule prevents the most common queue-restore bug — user taps play, then the restore completes and overwrites their intent. No other model specifies this race condition or its resolution.

GLM contributes the foundational ownership direction: "Media3 playlist is the runtime queue. Kaon persists a snapshot — not a competing runtime model." Qwen contributes a two-table schema concept (singleton `queue_state` + ordered `queue_item`) as one clean Room-native expression of the snapshot — this is a reasonable implementation candidate but the exact schema belongs to the database design phase, not to this architectural document.

**What was not taken:**

| Model | Proposal | Reason for rejection |
|-------|----------|---------------------|
| DeepSeek | Defer queue persistence entirely | Violates the "queue survives process death" requirement. Users expect this. |
| Meta | Persist QueueSnapshot as JSON in DataStore via kotlinx.serialization | DataStore is the wrong tool for a potentially large ordered list. Room provides transactional writes, query capability, and natural integration with existing entities. |

**Rejected alternative:** DeepSeek — defer queue persistence
**Reason for rejection:** Queue persistence is an explicit V1 requirement. A foreground service reduces process death likelihood during active use, but does not eliminate it. Users expect queue state to survive app restarts.

**Synthesis:**
K3 supplies the restore race rules and debounced write strategy. GLM supplies the ownership principle (snapshot, not competing model). Qwen supplies one candidate schema structure (singleton state + ordered items). The restore rules and ownership principle are architectural; the exact schema is an implementation-level decision to be finalized during database design.

**Final Kaon interpretation:**
- The service owns the snapshot writer — no second writer.
- Writes are debounced on queue change events and force-flushed on pause/stop.
- **Restore rule:** Restore only if player is empty AND no controller request has started playback. Restore always loses to explicit user action.
- Orphaned tracks (tracks whose `trackId` no longer resolves) are silently skipped during restore.
- Exact shuffle-order restoration semantics need verification in implementation — the requirement is "reproduce the timeline exactly," validated by tests.

---

## 5. Library Synchronization

**Label:** SETTLED
**Decision:** Query-based full reconcile, run on app start and on `ContentObserver` notification (debounced) while the process is alive. Fetch the full audio projection, diff in memory against stored rows, apply only the delta in one transaction. Deletions route through the re-linking algorithm (§2) before orphaning.

**Primary source:** K3
**Supporting sources:** GLM, DeepSeek

**Why this was selected:**
K3's sync design is the simplest correct approach. A full metadata query from MediaStore is expected to be cheap even at large library sizes (it's a cursor read, not file I/O); the writes are incremental because only the diff is applied. Idempotency means interrupted sync recovers for free — the next run simply reconciles again. This eliminates an entire class of bugs around interrupted syncs, partial states, and sync tokens.

> **⚠ Performance assumption:** "Full query is cheap at 50k tracks" is a design hypothesis, not a verified fact. This must be benchmarked on representative hardware before treating it as architectural truth. If benchmarking shows the full query is too expensive, incremental strategies become necessary.

GLM contributes the eventual-consistency framing: "MediaStore change notifications are inconsistent across OEMs, sometimes batched, sometimes missing. The honest guarantee Kaon can make is: eventually consistent within a bounded time." This framing sets correct expectations and prevents over-engineering for perfect real-time sync that Android cannot deliver.

**What was not taken:**

| Model | Proposal | Reason for rejection |
|-------|----------|---------------------|
| Qwen | WorkManager-backed synchronization | WorkManager adds complexity for a problem that doesn't require it. Nothing needs the library fresh while the app isn't running. K3's trigger to revisit: "a concrete feature that does." |
| Meta | WorkManager periodic rescan every 24h | Same reasoning — no V1 feature requires background sync when the app is not running. |
| DeepSeek | Incremental via `DATE_MODIFIED` since last sync | `DATE_MODIFIED` is unreliable across OEMs. A full query is cheap; incremental writes achieve the same performance benefit without the fragility of timestamp-based delta detection. |
| Opus | `ContentObserver` + WorkManager periodic diff on startup | Adds WorkManager dependency without proven need. |

**Rejected alternative:** Qwen/Meta — WorkManager for library sync
**Reason for rejection:** K3 explicitly defers WorkManager with a named trigger: "proven need for sync when app is not running." No V1 feature requires the library to be fresh while the app is dead. The full-query-plus-memory-diff approach handles all sync scenarios within the app process. WorkManager can be added later if a concrete requirement emerges.

**Final Kaon interpretation:**
- On app start: full reconcile (query MediaStore, diff against Room, apply delta).
- While alive: `ContentObserver` triggers debounced reconcile.
- Deletions: route through re-linking (§2) before orphaning.
- Sync is idempotent by construction — interruption recovers automatically on next run.
- Must be verified with a large-library benchmark (50k tracks) — per the measurement principle.
- **WorkManager trigger:** Revisit if a concrete feature requires the library to be fresh when the app is not running.

---

## 6. Sources of Truth

**Label:** SETTLED
**Decision:** Explicit source-of-truth map for every major concern.

**Primary source:** GLM
**Supporting sources:** K3, Qwen, Opus

**Why this was selected:**
GLM provides the most structured and explicit source-of-truth table. No other model presents this as a first-class architectural artifact — most embed ownership rules implicitly within individual decisions. Making the source-of-truth map explicit prevents the most common architectural failure: two subsystems both believing they own the same concern.

| Concern | Source of Truth | Notes |
|---------|----------------|-------|
| What music exists | MediaStore (external world) | Never modified by Kaon |
| Normalized library model | Room (derived tables) | Rebuildable from MediaStore at any time |
| Playlists, favorites, history | Room (user-owned tables) | Kaon's irreplaceable data — migrations are sacred |
| Settings, theme preferences | DataStore | Small, preferences-shaped |
| **Live runtime queue** | **Media3 `Player` inside the service** | **Kaon only observes. Kaon does not own a second live queue.** |
| Queue persistence (process death) | Room (snapshot) | Restoration snapshot only. Restored into Player on cold start, then discarded as live truth. |
| Artwork pixels | Coil cache | Never a source of truth, only a rendering concern |

**What was not taken:**
No model disagreed with this mapping. The contribution is the formalization, not the content.

### Formal Queue Invariant

> **INVARIANT:** Media3 Player = live runtime queue. Room = persisted restoration snapshot. Kaon does not own a second live queue. This invariant must hold throughout every section of this document and every implementation decision.

This invariant is a formal restatement of the ownership rule from §3 (Playback State Ownership) and §4 (Queue Persistence). It is repeated here because the distinction between "live queue" and "restoration snapshot" is the single most important ownership boundary in the application, and any violation of it reintroduces the dual-state-machine risk.

**Final Kaon interpretation:**
This table is a binding architectural constraint. Any proposed change that introduces a second source of truth for any concern in this table requires explicit justification and review.

---

## 7. Derived Data vs. User-Owned Data

**Label:** SETTLED
**Decision:** The Room schema explicitly separates derived tables (rebuildable from MediaStore) from user-owned tables (irreplaceable, migration-sacred).

**Primary source:** GLM
**Supporting source:** K3

**Why this was selected:**
GLM makes this the "most important schema decision": "derived tables can always be dropped and rescanned (which makes sync-strategy evolution safe); user-owned tables must be migrated with care forever." This distinction has profound practical consequences: it means the sync strategy can evolve freely (because derived tables are disposable), while user data (favorites, playlists, history) requires careful migration discipline from the very first release.

K3 reinforces this through its orphan handling policy — user data (favorites, history attached to a track) survives even when the track's MediaStore row vanishes, precisely because user data is owned independently of derived library data.

**What was not taken:**
No model explicitly disagreed, but several (Opus, DeepSeek) do not make this separation a first-class concept, treating all Room tables as equal. The risk of not making this distinction explicit is that a developer may casually add a column to a user-owned table without realizing it requires migration discipline.

**Final Kaon interpretation:**

| Table Type | Examples | Rules |
|------------|----------|-------|
| **Derived** (rebuildable) | `track` library rows, normalized album/artist keys | Can be dropped and rescanned. Sync strategy changes are safe. |
| **User-owned** (irreplaceable) | `favorite_track`, `playlist`, `playlist_entry`, `play_event` (history) | Migrations are sacred. Schema export committed to repo. Migration tests required. |
| **Operational** (restorable) | `queue_state`, `queue_item` | Loss is inconvenient but not catastrophic. Best-effort persistence. |

---

## 8. History Schema

**Label:** SETTLED — conditional on history/play-event capture being confirmed as a V1 requirement
**Decision:** Append-only `play_event` table: `id, trackId, eventType (play | skip), playedAt, playedMs`. "Recently played" and "most played" are queries, not tables. No aggregation tables in V1.

**Primary source:** K3
**Supporting sources:** GLM, Qwen

**Why this was selected:**
K3 provides the most complete play-event schema. The key insight is that past listening is impossible to backfill — if Kaon ships V1 without recording play events, early users' data is simply gone. K3's raw-event design preserves every future option: aggregates (most played, listening streaks, recommendation signals) can always be derived from raw events, but raw events cannot be reconstructed from aggregates.

GLM independently identifies this as the one "future feature" with a present-day cost if skipped: "Recording minimal play events is cheap and is the one 'future feature' with a present-day cost if skipped." This reinforces K3's schema decision with GLM's characteristic decision discipline.

Qwen contributes the skip event recording concept, though K3 includes it in the `eventType` column.

**What was not taken:**
Opus proposes deferring history/favorites schema entirely from V1: "No `play_history`, `favorites`, `playlists` in V1 schema."

**Rejected alternative:** Opus — defer history schema to post-V1
**Reason for rejection (if history is in V1 scope):** Play events cannot be retroactively created. Early users' listening data would be permanently lost. The schema is minimal (one table, five columns) and the recording logic is trivial. This meets the §1.3 test: it has a concrete requirement (data insurance) and a concrete data model.

> **⚠ Scope dependency:** This decision is SETTLED only if history/play-event capture is confirmed as part of V1 scope. If V1 ships without history features, this schema can be deferred — but the cost of deferral is that early users' listening data is permanently lost. This is a product decision, not an architectural one.

**Final Kaon interpretation:**
- Table: `play_event(id, trackId, eventType, playedAt, playedMs)`
- `eventType`: `play` or `skip`
- Play-count threshold: record after 30s or 50% played (confirm or adjust).
- "Recently played" = query on `play_event` ordered by `playedAt`.
- "Most played" = aggregation query on `play_event`.
- **Aggregation table trigger:** measured query cost on play_event exceeds tolerance.

---

## 9. Artwork

**Label:** SETTLED
**Decision:** Coil is the only decode/cache pipeline. One internal artwork-source component hides the platform split (`ContentResolver.loadThumbnail` on 29+, legacy album-art path on 26–28). Requests are size-aware: thumbnails for lists, larger for the full player. Failures resolve to placeholders and never propagate to playback or library.

**Primary source:** K3
**Supporting sources:** Meta, Qwen

**Why this was selected:**
K3 provides the most actionable artwork specification. The size-aware request pattern addresses the top memory/jank risk in music apps — repeated full-size decoding. The platform-split abstraction is the one place K3 acknowledges a small interface is justified, because `ContentResolver.loadThumbnail` (API 29+) and the legacy album-art path are genuinely different code paths that should not leak into callers.

Meta contributes the cache-key strategy: `albumId + sizeBucket`. This is a clean, collision-free key that integrates naturally with Coil's cache.

Qwen identifies artwork as a design-system-level concern with explicit error/fallback behavior, reinforcing that artwork failures must never block or degrade other subsystems.

**What was not taken:**
External artwork providers are explicitly deferred. No model proposes including them in V1.

**Final Kaon interpretation:**
- Coil handles all decode/cache operations.
- Artwork source: platform-aware component (29+ API vs. legacy).
- Requests specify size bucket (thumbnail vs. full).
- Cache key: `albumId + sizeBucket`.
- Failures → placeholder. Never propagate errors to playback or library.
- **Defer:** External artwork providers, online artwork lookup.

---

## 10. Navigation

**Label:** SETTLED (with verification gate)
**Decision:** Adopt Navigation 3, pending a verification spike before the first screen. The mini-player/full-player is an app-level overlay, not a navigation destination.

**Primary source:** K3
**Supporting sources:** GLM, Qwen

**Why this was selected:**
K3 provides both the recommendation and the critical qualification: adopt Nav3 "pending a verification spike" and treat the player as an overlay, not a destination. The player-as-overlay rule is architecturally important regardless of navigation library choice — it prevents the back stack from swallowing playback UI, which is a common failure mode in music apps.

GLM and Qwen independently reach the same conclusion: use Nav3 if stable, verify before committing, keep the mini-player outside the normal screen back stack.

**What was not taken:**

| Model | Proposal | Reason |
|-------|----------|--------|
| Meta | Use Navigation Compose for V1, wrap behind interface for future swap | Conservative but reasonable. The interface wrapper adds indirection that may not be justified if Nav3 proves stable. |
| Opus | Wrap Nav3 behind a `Navigator` interface | Adds abstraction without proven need. With ~8-10 destinations, switching navigation libraries is a contained refactor, not a rewrite. |

**Rejected alternative:** Meta — Navigation Compose with abstraction layer
**Reason for rejection:** The destination count is small (~8-10). If Nav3 proves unsuitable, switching to Nav2 is a localized change. Adding an abstraction layer to future-proof a choice that affects ~10 files is premature.

**Final Kaon interpretation:**
- **Verification spike:** Before the first screen, verify Nav3's stability and its interaction with the persistent mini-player/expanded player pattern.
- **Player-as-overlay rule:** The mini-player + full player is an app-level overlay (sheet, scaffold slot, or similar). It is NOT a navigation destination. This rule holds regardless of navigation library.
- **Fallback:** If Nav3 creates friction around back stack, sheets, or state restoration during the spike, fall back to Navigation Compose. The switching cost is low at this stage.
- **Decision deadline:** Firm commitment within the first milestone. Switching cost grows with screen count.

---

## 11. Module and Package Structure

**Label:** SETTLED
**Decision:** One Gradle module (`:app`), with packages designed for future mechanical extraction.

**Primary sources:** K3 + Qwen (synthesized)

**Why this was selected:**
K3 provides the package naming convention and the extraction triggers. Qwen provides the most detailed package responsibility breakdown and the explicit separation of `core/*` (boundaries) from `feature/*` (screens). The synthesis combines K3's discipline triggers with Qwen's organizational depth.

**Synthesis:**
K3 supplies the naming (`kaon.core.playback`, `kaon.core.data`, `kaon.feature.*`) and the extraction triggers. Qwen supplies the detailed package responsibility descriptions and the `core/library` vs `core/database` separation (pipeline logic vs. storage). The final structure uses K3's naming with Qwen's responsibility assignments.

```
kaon.app              Application, MainActivity, Hilt wiring
kaon.core.designsystem    tokens, theme, shared components
kaon.core.playback        MediaSessionService, playback facade, queue snapshot
kaon.core.data            db (entities/DAOs), DataStore, MediaStore source, sync engine
kaon.core.artwork         Coil setup, artwork keys, platform-aware source, fallback
kaon.feature.library      tracks/albums/artists screens + ViewModels
kaon.feature.player       mini-player + full player overlay
kaon.feature.search       search screen + ViewModel
kaon.feature.playlists    playlist screens (when implemented)
kaon.feature.settings     settings screen
```

**What was not taken:**
- Qwen's `core/common` package (dispatchers, result helpers, logging helpers, time/format utilities). Deferred per §1.3 — no generic common package unless something genuinely needs it. If shared utilities emerge, they should be placed in the most specific package that owns them.
- Qwen's `core/model` package (cross-boundary plain models). For a single module, model classes can live in the package that owns them. A shared model package is extracted only when the same model class is genuinely imported by 3+ packages.
- Gemini's multi-module structure (rejected in §1.1).
- GLM's `library` package separated from `data` (GLM puts sync logic in `library/` and storage in `data/`). K3 and Qwen merge these more naturally.

**Final Kaon interpretation:**
- From day one, independent of module count: Gradle version catalog, KSP, Room schema export directory committed to the repo, R8 on release builds, and a minimal CI workflow (assemble + unit tests) once the first code lands.
- Repository root stays conventional: single `app` module, `gradle/libs.versions.toml`, committed `schemas/`. No build-logic module, no convention plugins until a second module actually exists.

---

## 12. Dependency Direction

**Label:** SETTLED
**Decision:** Strict unidirectional dependency flow. No cycles.

**Primary sources:** K3 + GLM (synthesized)

```
feature/*  ──▶  playback facade  ──▶  Media3 (MediaController)
    │                                      │
    └──────▶  repositories  ──▶  Room / DataStore / MediaStore
                   ▲
playback service ──┘  (reads queue snapshot only)
```

**Queue ownership (restated from §6 invariant):**
- `Media3 Player` = live runtime queue. Kaon observes it.
- `Room` = persisted restoration snapshot. Not a second live queue.
- Kaon does not own a competing runtime queue.

**Rules (from K3):**
- Media3 owns: current item, position, play/pause state, shuffle/repeat modes, the live queue (timeline), audio focus, becoming-noisy.
- Kaon owns: favorites, history, playlists, persisted queue snapshot (restoration only), settings, and UI-derived state.
- Nothing in data depends on UI or playback. Nothing in playback depends on Room except the queue snapshot store. No cycles.
- All queue mutations go through granular timeline APIs (`addMediaItem`, `moveMediaItem`, `removeMediaItem`) — never re-`setMediaItems` the full list after initial set.

**Rule (from GLM):**
- No event bus. State flows upward as `StateFlow`; commands go down as plain function calls. If we ever feel we need an event bus, that is a design smell to investigate, not a mechanism to add.

**Primary source:** K3 (dependency diagram and queue mutation rule)
**Supporting source:** GLM (event-bus-as-design-smell principle)

**Why this was selected:**
K3 provides the concrete dependency diagram and the critical queue mutation rule: granular timeline APIs, never bulk-reset. This rule is what guarantees "mutations don't restart playback" — a requirement explicitly stated in the vision. GLM contributes the event-bus prohibition, which prevents the most common escape hatch developers use to bypass dependency direction.

**What was not taken:**
DeepSeek proposes that "Kaon owns the queue order and queue operations" while "Media3 owns player state," with a `PlaybackController` that merges both. This creates a bidirectional dependency between Kaon's queue and Media3's player — rejected because it introduces the dual-state-machine risk and violates the queue invariant (§6).

**Final Kaon interpretation:**
- Dependencies point inward toward stability.
- Feature packages never import each other's ViewModels or screens.
- Core packages never depend on feature packages.
- Playback never writes to library tables. Library never knows about the Player.
- No event bus, no message broker. Flows and direct calls only.
- The queue invariant (§6) applies here: the persisted queue snapshot is a restoration artifact, not a live data source.

### Strict Boundary Encapsulation Invariant

> **INVARIANT:** Feature screens and ViewModels **MUST NOT** directly depend on Room entities (`TrackEntity`, `FavoriteTrackEntity`), MediaStore objects (`Uri`, `Cursor`), or ExoPlayer objects (`ExoPlayer`, `MediaItem`). All feature screens and UI components consume only clean domain models (`Track`, `PlaybackState`) exposed by repositories and the `PlaybackFacade`. This prevents UI coupling to persistence and platform details and protects the architecture from structural collapse.

---

## 13. Domain / Use-Case Layer

**Label:** SETTLED (temporary)
**Decision:** No dedicated domain/use-case layer. ViewModels call repositories and the playback facade directly. Extract a use-case class only when logic is genuinely shared or complex.

**Primary sources:** GLM + Qwen (synthesized)
**Supporting sources:** K3, Opus, DeepSeek, Meta

**Why this was selected:**
All models except Gemini reject a blanket use-case layer. GLM and Qwen provide the most precise extraction criteria:

GLM: "Extract a use-case class only when logic is genuinely shared between screens or complex enough to isolate."

Qwen adds specific criteria:
- It coordinates multiple repositories meaningfully
- It contains non-trivial business rules
- It is reused across multiple features
- It improves testability in a concrete way

**What was not taken:**
Gemini proposes a pure Kotlin domain layer with interfaces for repositories and playback controllers. Rejected for V1: in a single-module app, repository interfaces with one implementation are ceremony. `internal` visibility and package boundaries achieve the same isolation with less code.

**Rejected alternative:** Gemini — domain layer with repository interfaces
**Reason for rejection:** With one module and one developer, interfaces-with-one-implementation add indirection without benefit. Repositories are concrete classes. If a second implementation appears (e.g., a test fake that is genuinely different from the real implementation), an interface is introduced then, for that seam.

**Final Kaon interpretation:**
- ViewModels → repositories/facade directly.
- Likely first use-case candidates: scan orchestration, "play album with shuffle semantics," queue restoration with missing-item policy.
- Unlikely to need use-cases: get albums, get songs, toggle favorite, add to queue, open settings.
- This is a **temporary decision**, not a permanent stance. Revisit at first sign of ViewModel bloat.

---

## 14. Search

**Label:** SETTLED
**Decision:** Plain Room `LIKE` queries with case-folded columns in V1. No FTS, no search engine, no premature indexing infrastructure.

**Primary source:** K3
**Supporting sources:** GLM, Qwen, DeepSeek, Opus

**Why this was selected:**
All models converge on this. K3 provides the explicit trigger for escalation: "multi-token matching, ranking, or measured query slowness." This makes the decision reviewable — FTS is not permanently rejected, just deferred until a named condition is met.

**Final Kaon interpretation:**
- Normalized title/artist/album fields, indexed columns.
- Debounced queries, limited result counts, separate result groups.
- **FTS trigger:** Multi-token matching requirements, ranking requirements, or measured query slowness at realistic library sizes.

---

## 15. Background Work Policy

**Label:** SETTLED
**Decision:** Match the mechanism to the work's durability requirement. Do not use WorkManager by default.

**Primary source:** K3
**Supporting sources:** Qwen, DeepSeek

**Why this was selected:**
K3 explicitly defers WorkManager with a named trigger. Qwen provides the most detailed background-work decision matrix, categorizing work into immediate coroutine, lifecycle-aware collection, WorkManager, and MediaSessionService. The synthesis uses K3's deferral discipline with Qwen's categorization.

| Work Type | Mechanism | Examples |
|-----------|-----------|----------|
| Short-lived operations | Immediate coroutine | Search queries, preference changes, UI mutations |
| State observation | Lifecycle-aware collection | Room flows, playback state, sync status |
| Durable background work | WorkManager (when needed) | **Not used in V1.** Trigger: sync must survive process death |
| Playback | MediaSessionService | Media controls, notification, external controls |

**What was not taken:**
Qwen and Meta recommend WorkManager for library sync from V1. Rejected per K3's reasoning: nothing in V1 needs the library fresh while the app isn't running.

**Final Kaon interpretation:**
- V1: No WorkManager dependency. Library sync runs as coroutine work within the app process.
- **WorkManager trigger:** A concrete feature that requires the library to be fresh when the app is not running, OR sync that must survive process death.

---

## 16. Dependency Injection

**Label:** PROVISIONAL — verify after the initial dependency graph exists
**Decision:** Start with Hilt, but validate that it earns its cost.

**Source:** UNCERTAIN / MULTIPLE MODELS

This decision has significant disagreement across models:

| Model | Position |
|-------|----------|
| K3 | Hilt (stated without elaboration) |
| Qwen | Hilt (implied by package structure) |
| Gemini | Hilt (explicit) |
| GLM | Hilt (implied) |
| Opus | Challenge Hilt — consider manual DI |
| DeepSeek | Defer Hilt, use manual `AppContainer` |
| Meta | Manual `AppContainer`, migrate to Hilt at ~15 deps |

**Why Hilt is the current default:**
The vision document lists Hilt in the preferred technology stack. Hilt provides ViewModel injection + Service injection (both relevant for Kaon's playback service architecture) with minimal boilerplate once KSP is configured. The `@HiltViewModel` and `@AndroidEntryPoint` patterns eliminate connection-lifecycle boilerplate that is otherwise hand-written.

**Why this is PROVISIONAL, not SETTLED:**
The anti-overengineering rule (§1.3) says: "no concrete V1 requirement + no concrete data model = no interface, module, or table." Adopting Hilt primarily because the dependency graph is *expected* to grow is exactly the kind of speculative reasoning this document otherwise warns against. Manual constructor injection is a valid V1 implementation. Hilt earns its cost only when the actual dependency graph demonstrates that manual wiring creates real friction — not when it is anticipated to do so.

Opus, DeepSeek, and Meta raise valid concerns: Hilt hides dependency direction, adds KSP build overhead, and increases complexity for a small initial graph. These concerns do not permanently disqualify Hilt, but they prevent it from being SETTLED before the graph exists.

**What was not taken (yet):**
Meta's suggestion to "start with manual `AppContainer`, migrate to Hilt when graph exceeds ~15 dependencies" is a reasonable alternative. The migration cost is real but bounded.

**Final Kaon interpretation:**
- Start with Hilt if the project stack already makes it worthwhile (e.g., Hilt is already configured and ViewModel/Service injection is immediately needed).
- If starting fresh, manual constructor injection is equally valid for the initial dependency graph.
- **Revisit trigger:** If manual wiring becomes painful (graph exceeds ~15 singletons, or Service/ViewModel injection boilerplate is substantial), adopt Hilt. If Hilt creates unexpected friction (KSP build times, debugging difficulty), revert to manual DI.
- This is a pragmatic implementation choice, not an architectural invariant.

---

## 17. Logging and Observability

**Label:** SETTLED
**Decision:** Timber (or equivalent thin wrapper) from day one. Tagged, verbose logging for playback and sync in debug builds. No crash reporting in V1.

**Primary source:** K3
**Supporting sources:** GLM, Qwen

**Why this was selected:**
K3 provides the concrete recommendation: Timber for debug logging, no crash reporting because it conflicts with the privacy identity. GLM reinforces the privacy stance: "no analytics or crash reporting by default." Qwen identifies observability as a design-system-level concern that should not be forgotten.

**Final Kaon interpretation:**
- Timber (or similar) from day one. Tagged logging for `playback`, `sync`, `queue`, `artwork`.
- Debug builds: verbose. Release builds: warnings and errors only.
- No crash reporting in V1. Open decision for later (opt-in only, consistent with privacy identity).
- No analytics in V1. (**OPEN:** Whether analytics are permanently excluded or conditionally allowed as opt-in is a product/privacy policy decision, not an architectural constraint. The architectural stance is: no silent telemetry, and any data collection must be opt-in.)

---

## 18. minSdk

**Label:** PROVISIONAL — until product support range is explicitly locked
**Decision:** minSdk 26 (Android 8.0) is the current working assumption.

**Primary source:** K3
**Supporting sources:** GLM, Qwen, Meta

**Why this is the current default:**
K3 provides the most thorough analysis of minSdk trade-offs: "33 would collapse permissions to one path but excludes exactly the 'modest hardware' devices the vision targets. 29 barely simplifies anything. 26 covers virtually the entire music-playing market at the cost of one isolated legacy-permission branch and one legacy artwork branch."

**Why this is PROVISIONAL:**
minSdk is ultimately a product support decision, not a pure architectural one. It determines the testing surface, permission code paths, and artwork API availability. The architectural analysis (K3) informs the trade-offs, but the final number must be a deliberate product commitment.

**Final Kaon interpretation:**
- Working assumption: minSdk 26, compileSdk/targetSdk 36.
- This creates two version-split code paths: permissions (legacy storage ≤32 vs `READ_MEDIA_AUDIO` 33+) and artwork (legacy album-art path 26-28 vs `ContentResolver.loadThumbnail` 29+).
- These splits are contained within their respective packages and do not leak into feature code.
- **Lock trigger:** This becomes SETTLED when the product support range is explicitly decided.

---

## 19. Permissions and Platform Handling

**Label:** SETTLED
**Decision:** Handle platform permission splits within contained components. Never leak version-specific code into feature logic.

**Primary source:** K3
**Supporting sources:** Qwen

**Why this was selected:**
K3 identifies the specific permissions needed: `READ_MEDIA_AUDIO` (33+), legacy storage permission (≤32), `POST_NOTIFICATIONS` (33+), `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (34+). Qwen adds notification permission behavior and background restrictions as testing considerations.

**Final Kaon interpretation:**
- Permission handling is a contained component in the `app` package.
- Version-specific code paths are isolated behind simple conditional checks, not abstracted into interfaces.
- Permission UX is an open product decision (request at launch vs. on first library access).

---

## 20. Android Auto

**Label:** SETTLED (deferred)
**Decision:** Compatible now, build later.

**Primary source:** GLM
**Supporting source:** K3

**Why this was selected:**
GLM states it most precisely: "A correctly structured MediaSessionService makes Auto possible later without redesign. Building the Auto browse tree, testing, and compliance is its own project." K3 adds that the session class choice (`MediaSession` now vs. `MediaLibrarySession`) is a contained change inside the service.

**Final Kaon interpretation:**
- Do not build Android Auto features in V1.
- Do not structure the session in a way that blocks it.
- The `MediaSessionService` architecture naturally supports Auto later.
- **Trigger:** When Android Auto becomes a concrete requirement, the change is contained to the service package.

---

## 21. Privacy

**Label:** SETTLED (architectural default) — specific policy details are product decisions
**Decision:** Local-first, private by default. No silent telemetry. No crash reporting in V1.

**Primary source:** GLM
**Supporting sources:** K3, Qwen

**Why this was selected:**
GLM explicitly concretizes the privacy stance: "local-first implies no analytics/crash reporting by default. Silent telemetry would contradict the vision." K3 reinforces: "No crash reporting in v1 — it conflicts with the privacy identity unless it's opt-in." Qwen asks the right questions ("Is any telemetry allowed? Are network features opt-in?") and recommends "the default should be private/local."

**Final Kaon interpretation:**
- **Architectural default:** Private by default. No silent telemetry. No data leaves the device without explicit user consent.
- No crash reporting in V1.
- Any future online feature (metadata enrichment, artwork lookup) must be opt-in.
- Network requests, if ever added, must identify themselves minimally.
- Listening history is private and local.
- **OPEN:** Whether opt-in analytics or opt-in crash reporting are ever offered is a product/privacy policy decision. The architecture supports either answer. The architectural constraint is: nothing is sent without explicit opt-in.

---

## 22. Room Schema Discipline

**Label:** SETTLED
**Decision:** Export Room schemas to a committed directory from day one. Write migration tests.

**Primary source:** K3
**Supporting sources:** GLM

**Why this was selected:**
K3 identifies this as "expensive to retrofit, free now." GLM ranks "Room migration discipline" as a top risk and prescribes "export schemas, test migrations, from the first release." Both models independently recognize that a library database with user data (playlists, favorites, history) makes migrations sacred once real users exist.

**Final Kaon interpretation:**
- Room schema export directory committed to the repo from the first database version.
- Migration tests written for every schema change after the first release.
- This is non-negotiable. The cost is near-zero now; the cost to retrofit later is high.

---

## 23. Accessibility

**Label:** NOTED (not fully decided)
**Decision:** Treat accessibility as a design-system requirement, not an afterthought.

**Primary source:** Qwen
**Supporting sources:** None (other models do not address accessibility)

**Why this was selected:**
Qwen is the only model that identifies accessibility as an architectural concern. Touch targets, content descriptions, TalkBack behavior, dynamic text, contrast, and keyboard/switch access should be built into the design system, not retrofitted.

**Final Kaon interpretation:**
- Accessibility requirements are part of the design-system package.
- Touch targets, content descriptions, and contrast ratios are defined in the design tokens.
- Player controls are specifically called out as requiring accessibility attention.
- Detailed accessibility specifications are deferred to the design-system round.

---

## 24. Backup and Restore

**Label:** NOTED (not fully decided)
**Decision:** User-owned data should survive device migration. Derived data does not matter.

**Primary source:** Qwen
**Supporting source:** GLM

**Why this was selected:**
Qwen provides the backup/restore checklist: preferences, history, playlists, database participation in Android backup. GLM's derived-vs-user-owned split (§7 of this document) determines what matters: playlists, favorites, history = must survive; derived library = doesn't matter.

**Final Kaon interpretation:**
- User-owned tables (favorites, playlists, history): should be backed up.
- Derived tables (library cache): do not need backup (rebuildable from MediaStore).
- Settings/preferences: should be backed up.
- Queue state: best-effort (loss is inconvenient, not catastrophic).
- Detailed backup/restore implementation is deferred but should not be forgotten.

---

## 25. Customization

**Label:** SETTLED
**Decision:** V1 customization is limited to design tokens (light/dark/accent). No layout customization, no runtime themes, no plugin system.

**Primary source:** GLM
**Supporting sources:** K3, Qwen, Opus, DeepSeek, Meta

**Why this was selected:**
GLM provides the customization cost ladder that makes the decision boundary clear:

| Level | Description | Cost | V1? |
|-------|-------------|------|-----|
| 1 | Design tokens / theming | Cheap | ✅ Yes |
| 2 | User-selectable presets / accent colors | Cheap | ✅ Yes |
| 3 | Alternative player layouts / rearrangeable UI | Expensive (layout system) | ❌ No |
| 4 | Runtime plugins / theming packs | Very expensive (platform) | ❌ No |

All models agree on this boundary. GLM provides the clearest articulation of why levels 3-4 should not influence V1: "Levels 3–4 should not influence any architectural decision for a long time, and when level 3 arrives it should be built as specific configurable features, not a general customization framework."

**Final Kaon interpretation:**
- V1: Material 3 token-based theming. Light/dark theme. User-selectable accent color.
- No layout engines, no downloadable themes, no UI modules, no runtime plugin system.
- When level 3 customization is eventually needed, build it as specific configurable features (e.g., "choose player layout from 3 options"), not a general framework.

---

## 26. What Must NOT Influence the Initial Architecture

**Label:** SETTLED
**Decision:** Explicit exclusion list for V1.

**Primary sources:** K3 + GLM + Opus (synthesized)

All models independently produce similar exclusion lists. The synthesis captures the union:

- **Plugin/kernel system** — no indirection "for future plugins." If enrichment providers ever land, an interface is introduced then, for that seam.
- **Streaming** — no local/remote media abstraction. Media3's `MediaItem` already handles arbitrary URIs; that free seam is the only concession.
- **Recommendation engine** — the only concession is the play-event schema (§8), justified on its own merits.
- **Runtime UI customization beyond design tokens** — no layout engines, no downloadable themes, no UI modules.
- **Online services, accounts, cloud sync** — none.
- **Event bus / message-driven architecture** — Flows and direct calls only.
- **Use-case-per-class layer** — ViewModels call repositories/facade directly (§13).
- **Multi-module structure** — per §1.1, with written extraction triggers.
- **Cross-platform anything.**
- **Audio fingerprinting / hashing infrastructure.**
- **User-correctable metadata as source of truth** — V1 metadata is read-only from MediaStore.
- **Advanced full-text search engine.**
- **Android Auto implementation** (§20 — structure stays compatible; nothing built).

---

## 27. Vertical Slice Validation

**Label:** SETTLED
**Decision:** The first milestone validates the architectural spine, not features.

**Primary source:** GLM
**Supporting source:** K3

**Why this was selected:**
GLM provides the vertical-slice-first validation strategy: "app shell with theme tokens → scan MediaStore into minimal Room tables → flat track list → tap plays via PlaybackService with media notification and surviving backgrounding → queue snapshot survives process death. This exercises every boundary in the architecture with almost no feature code. If this slice feels awkward, the architecture is wrong before features are built on it."

K3 reinforces by defining the first milestone as the structural skeleton, not feature delivery.

**Essential Verification Suite (10 Critical Scenarios):**
1. **Duplicate Protection**: Two tracks with identical duration and file size cannot be silently merged.
2. **Missing Track Retention**: A temporarily missing track keeps its `trackId`, favorite state, and play history.
3. **Re-linking on Return**: A file returning after being missing re-links to the original track.
4. **False Attachment Prevention**: A genuinely new file does not accidentally attach to an old missing track.
5. **MediaStore ID Stability**: MediaStore `_ID` changes while the actual track remains the same.
6. **Queue Precedence**: Queue restore does not overwrite a queue the user has already modified during startup.
7. **Process Recovery**: Playback state and snapshot remain correct across process recreation.
8. **Timeline Mutation Safety**: Granular timeline mutations preserve the intended current item and position without bulk resets.
9. **Idempotent Sync**: Repeated syncs produce identical state without duplicates or unnecessary writes.
10. **Permission Revocation Safety**: Permission denial or empty scan preserves database integrity without wiping user data.

---

## 28. Queue Mutation API

**Label:** SETTLED
**Decision:** All queue mutations go through granular Media3 timeline APIs. Never bulk-reset the media items list after initial set.

**Primary source:** K3

**Why this was selected:**
K3 is the only model that specifies the granular queue mutation rule: "All queue mutations go through `addMediaItem`, `moveMediaItem`, `removeMediaItem` — never re-`setMediaItems` the full list after initial set. This is what guarantees 'mutations don't restart playback.'" No other model addresses this specific implementation constraint, which prevents the most common user-facing queue bug in Media3 apps.

**Final Kaon interpretation:**
- `setMediaItems` is used only for initial queue load and cold restore.
- All runtime mutations use granular APIs: `addMediaItem`, `moveMediaItem`, `removeMediaItem`.
- This rule prevents playback restart on queue mutation — a critical UX requirement.

---

## 29. Soft Delete / Missing Track Handling

**Label:** SETTLED
**Decision:** Prefer marking missing tracks instead of hard-deleting them. Orphaned tracks retain user data.

**Primary sources:** K3 + Qwen (synthesized)

**Why this was selected:**
K3 provides the orphan handling policy as part of the identity model: "mark orphaned, keep the user data, hide from UI, purge after a retention window." Qwen independently arrives at the same principle: "Prefer marking missing tracks instead of hard-deleting them immediately" with a `missing/unavailable` flag on the track entity.

**Synthesis:**
K3 supplies the orphan lifecycle (mark → retain → purge after window). Qwen supplies the entity-level flag. Combined: when a track's MediaStore row vanishes and re-linking fails, the track row is marked as missing/unavailable rather than deleted. User data (favorites, history, playlist entries) is preserved. The track is hidden from library browsing UI.

**Final Kaon interpretation:**
- Track entity includes a `missing`/`unavailable` flag and a `lastSeen` timestamp.
- Sync marks tracks as missing rather than deleting them.
- User data attached to missing tracks is preserved.
- Missing tracks are hidden from library browsing but retained in history/playlists (with visual indicator).
- Purge after a configurable retention window. (**OPEN:** Retention duration is a product decision, not an architectural constant. ~30 days is a reasonable default.)

---

## 30. Albums and Artists as Entities

**Label:** PROVISIONAL — this is the biggest schema decision that should still be validated
**Decision:** For V1, album and artist browsing is provisionally derived from normalized track keys rather than dedicated tables. This hypothesis must be validated during database design.

**Primary source:** Qwen
**Supporting source:** GLM

**Why this is the current default:**
Qwen explicitly proposes derived album/artist browsing as a temporary decision: "Start with track-centric normalization. Add album/artist entities when enrichment or user corrections become real requirements." This avoids premature album/artist table complexity and the sync/migration burden that comes with it.

**Why this is PROVISIONAL:**
This is the biggest open schema decision. Deriving albums and artists from track keys is a reasonable starting hypothesis, but it may prove insufficient during database design if:
- Query performance for album/artist browsing is poor with derived queries.
- The UI requires album/artist-level metadata (artwork keys, display names) that is awkward to derive per-query.
- Grouping logic (album splitting, artist disambiguation) becomes complex enough to warrant entities.

K3 and others assume album/artist tables exist from day one. That approach has real engineering merit — it provides a cleaner schema for browsing and simplifies future enrichment. The derived approach is not architecturally superior; it is a deferral strategy.

**Trigger for dedicated tables:** Album enrichment, artist images, user corrections, external IDs, relationship data, better duplicate grouping, or measured query performance problems with derived browsing.

**Final Kaon interpretation:**
- Working assumption: Albums and artists are derived from normalized track keys at the query level.
- No separate `album` or `artist` tables initially — but this is a provisional implementation hypothesis, not an architectural invariant.
- The database design phase should validate this choice against realistic query patterns before committing.
- This is the decision most likely to be revised during implementation. Treat it accordingly.

---

---

# Final Attribution Summary

| Model | Role in Final Architecture | Main Contributions | Major Rejections |
|-------|---------------------------|-------------------|-----------------|
| **K3** | Technical chassis | Track identity with re-linking + orphan handling; granular Media3 queue mutation APIs; queue restore race rules; service-owned queue persistence; play-event schema; size-aware artwork with platform abstraction; process-scoped playback facade; module extraction triggers; Nav3 verification spike; player-as-overlay; Room schema export discipline; minSdk analysis | None — K3's proposals survive nearly intact |
| **GLM 5.3** | Schema + decision discipline | Derived-vs-user-owned data separation; explicit sources-of-truth table; "observe, never mirror" playback principle; eventual-consistency framing for MediaStore; customization cost ladder; vertical-slice-first validation; privacy-default stance; Android Auto "compatible now, build later"; event-bus-as-design-smell principle | Track identity approach (MediaStore URI as primary — too fragile) |
| **Qwen** | Operational coverage | Accessibility as design-system requirement; backup/restore checklist; device compatibility considerations; performance-target checklist; background-work decision matrix; derived album/artist browsing (PROVISIONAL); queue schema candidate (implementation-level); soft-delete/missing-track flag; selective use-case extraction criteria; detailed package responsibilities | WorkManager for V1 library sync (no proven need); `core/common` and `core/model` packages (premature generalization) |
| **Meta** | Implementation details | Cache-key strategy (`albumId + sizeBucket`); per-dependency deferral reasoning; composite identity hash concept (informed final design); identifies artwork as #1 OOM risk | Kaon-owned playback state (dual state machine risk); manual DI (valid alternative — Hilt is PROVISIONAL, not settled); Navigation Compose over Nav3 (overruled pending spike) |
| **DeepSeek** | Scope discipline | Strongest scope-reduction voice; clearest "smallest first slice" definition; explicit Hilt/WorkManager/Ktor deferral reasoning | Queue persistence deferral (users expect it); MediaStore ID as sole identity (fragile); Hilt deferral reasoning partially vindicated (Hilt is now PROVISIONAL) |
| **Opus** | Boundary/process discipline | Architectural priority hierarchy (playback → library → performance → UI); anti-overengineering rule; foundation principles for single-module discipline; explicit exclusion of speculative abstraction | History/favorites schema deferral (data cannot be backfilled, if history is in V1 scope); Hilt challenge partially vindicated (Hilt is now PROVISIONAL) |
| **Gemini** | Supporting reasoning | Multi-module motivation (rejected but useful as future reference); domain-layer reasoning; UDF framing; composite primary key concept | Multi-module from day one (build complexity); domain layer with repository interfaces (ceremony without benefit); strict Use Cases everywhere |

### Decision Classification Summary

| Decision | Label |
|----------|-------|
| Modular monolith (§1.1) | SETTLED |
| Architectural priority (§1.2) | SETTLED |
| Anti-overengineering rule (§1.3) | SETTLED |
| Track identity + re-linking (§2) | SETTLED |
| Re-link retention duration (§2) | OPEN |
| Playback state ownership (§3) | SETTLED |
| Queue persistence (§4) | SETTLED |
| Queue schema (§4) | Implementation-level |
| Library sync strategy (§5) | SETTLED |
| Sync performance at scale (§5) | Must be benchmarked |
| Sources of truth (§6) | SETTLED |
| Queue invariant (§6) | SETTLED |
| Derived vs user-owned data (§7) | SETTLED |
| History schema (§8) | SETTLED (conditional on V1 scope) |
| Artwork (§9) | SETTLED |
| Navigation (§10) | SETTLED (with verification gate) |
| Package structure (§11) | SETTLED |
| Dependency direction (§12) | SETTLED |
| Domain/use-case layer (§13) | PROVISIONAL |
| Search (§14) | SETTLED |
| Background work policy (§15) | SETTLED |
| Dependency injection (§16) | PROVISIONAL |
| Logging/observability (§17) | SETTLED |
| Analytics policy (§17) | OPEN |
| minSdk (§18) | PROVISIONAL |
| Permissions (§19) | SETTLED |
| Android Auto (§20) | SETTLED (deferred) |
| Privacy default (§21) | SETTLED |
| Privacy policy details (§21) | OPEN |
| Room schema discipline (§22) | SETTLED |
| Accessibility (§23) | OPEN |
| Backup/restore (§24) | OPEN |
| Customization (§25) | SETTLED |
| V1 exclusion list (§26) | SETTLED |
| Vertical slice (§27) | SETTLED |
| Queue mutation API (§28) | SETTLED |
| Soft delete/missing tracks (§29) | SETTLED |
| Orphan retention duration (§29) | OPEN |
| Albums/artists as entities (§30) | PROVISIONAL |

---

## Final Provenance Rule

> The final Kaon architecture is a synthesized architecture. No individual model is authoritative. K3 supplies most of the technical chassis; GLM supplies much of the schema and decision discipline; the remaining models contribute selected refinements only where they survive independent engineering evaluation.
>
> **Final Kaon interpretation > model attribution.**
>
> When reading this document:
> - **"Primary source: K3"** means "K3 supplied the strongest version of the idea that Kaon ultimately adopted." It does NOT mean "copy K3" or "K3 is correct."
> - If a model name and a Final Kaon interpretation ever appear to conflict, **the Final Kaon interpretation wins unconditionally.**
> - Model names exist only to explain where an idea originated and why alternatives were considered. They have no architectural authority.
> - Attribution is historical provenance for decision traceability. The Final Kaon interpretation is the binding source of truth for implementation.
> - SETTLED decisions are architectural invariants. PROVISIONAL decisions are current best choices subject to validation. OPEN decisions are not yet locked. Do not treat all three equally.
