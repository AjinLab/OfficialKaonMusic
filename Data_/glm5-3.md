# Kaon Music — Initial Architectural Analysis

Before anything else: this vision document is unusually disciplined. It already contains most of the warnings I would normally have to raise (anti-speculation, no plugin system by default, Media3 ownership questions). So my job here is not to add discipline, but to sharpen the few places where the vision contains real tension, and to reduce the rest to a minimal starting structure.

---

## 1. Analysis of the Vision: Strengths and Tensions

**What's coherent:** playback reliability as the foundation, local-first identity, metadata as a first-class concern, explicit rejection of speculative infrastructure. The priorities are in the right order.

**Tensions I need to flag:**

**T1 — Customization depth vs. single-developer maintainability.**
"Highly customizable UI" spans at least four very different cost levels:
1. Design tokens / theming (cheap, do it)
2. User-selectable theme presets / accent colors (cheap)
3. Alternative player layouts / rearrangeable UI (expensive — this is a layout system, not a feature)
4. Runtime plugins / theming packs (very expensive — this is a platform)

The vision treats these as one concept. They are not. Levels 1–2 are v1-compatible. Levels 3–4 should not influence any architectural decision for a long time, and when level 3 arrives it should be built as *specific configurable features*, not a general customization framework.

**T2 — "One authoritative playback state" is stated as a goal, but the default failure mode violates it.**
The most common architectural failure in Media3 apps is building a parallel playback state machine in the app (a `PlayerState` in Room or a ViewModel that mirrors position/isPlaying/queue, then drifts out of sync with the player). The correct resolution is not more synchronization code — it's a rule: **Media3's `Player` is the only runtime playback state. Kaon observes it; Kaon never mirrors it.** This must be an explicit architectural principle from day one, because every natural instinct (ViewModel state, Compose state, persistence) pushes toward duplication.

**T3 — "The queue is a system-level concern" vs. Media3 already owning a queue.**
Media3's playlist API *is* a queue. If Kaon also keeps a queue table in Room and pushes it into Media3, there are two truths and they will fight. The workable model is: Media3 playlist is the runtime truth; Kaon adds a **persistence snapshot** (for process death) and optionally queue *sources* (e.g., "queue came from album X") — but not a competing runtime model. The details deserve their own decision round, but the ownership direction should be settled now because it shapes the service and the UI.

**T4 — "Changes should be detected and synchronized reliably" overpromises what Android delivers.**
MediaStore change notifications are inconsistent across OEMs, sometimes batched, sometimes missing, and genre data in MediaStore is historically unreliable. The honest guarantee Kaon can make is: *eventually consistent within a bounded time* (observer-triggered incremental sync + periodic reconciliation + re-sync on app foreground/user request). Instant, perfectly reliable detection is not achievable on Android, and designing as if it were will produce fragile code.

**T5 — Personalization has one genuine day-one implication.**
Recommendations, ML, similarity — all deferred. But **listening history cannot be retroactively recreated.** If Kaon ships v1 without recording play events, early users' data is simply gone. Recording minimal play events (timestamp, track, played/skipped/completed) is cheap and is the one "future feature" with a present-day cost if skipped. This is worth deciding now.

**T6 — Android Auto is cheap to permit, expensive to build.**
A correctly structured `MediaSessionService` makes Auto *possible* later without redesign. Building the Auto browse tree, testing, and compliance is its own project. Keep it out of v1 scope; just don't structure the session in a way that blocks it.

---

## 2. Separating Requirements, Assumptions, and Long-Term Ideas

**Actual v1 requirements (my reading — needs your confirmation):**
- Discover local audio via MediaStore; normalize into a browsable library (tracks/albums/artists)
- Playback: background, media session, notification, audio focus, becoming-noisy, headset/Bluetooth controls
- Queue: add/play-next/remove/reorder/shuffle/repeat, survives process death
- Artwork: embedded artwork displayed with sane caching
- Basic search across tracks/albums/artists
- Light/dark theme on a token system
- Responsive UI during scanning

**Assumptions that need validation (currently unquantified):**
- "Large libraries" — what target? 5,000 tracks? 20,000? This number drives scan batching, list virtualization strategy, and how paranoid we must be. I suggest designing for ~10k tracks as the comfort target and treating 50k+ as "must not break."
- MediaStore-first discovery is acceptable vs. needing SAF/folder access (affects identity, scanning, everything). I recommend MediaStore-first as a v1 stance.
- One Room database and one DataStore are sufficient. (Almost certainly yes at this scale.)

**Long-term ideas with no v1 architectural influence** (see section 8).

---

## 3. Ranked Architectural Risks

1. **Two playback state machines** (T2) — kills reliability, the core promise.
2. **Two queue truths** (T3) — causes "queue operations unexpectedly restart playback," explicitly forbidden by your requirements.
3. **MediaStore fragmentation + scan cost** — per-file metadata extraction is IO-heavy; naive scanning of 10k files will jank or drain battery. The scan pipeline must be incremental, resumable, and batched from the start.
4. **Metadata normalization scope creep** — "intelligent metadata" can absorb unlimited effort. v1 needs deterministic rules (trim/casefold/group-by-name), raw tags preserved, and nothing more.
5. **Compose performance on large lists + artwork** — unstable models, missing keys, and artwork-driven recomposition are the usual culprits. Address with discipline + measurement, not pre-optimization.
6. **Customization platform creep** (T1).
7. **Navigation 3 immaturity** — manageable, but a conscious choice (below).
8. **Room migration discipline** — a library database with user data (playlists, favorites, history) means migrations become sacred once real users exist. Cheap insurance: export schemas, test migrations, from the first release.

---

## 4. Decisions: Now vs. Open

**Decide before writing code:**

| # | Decision | Why now |
|---|----------|---------|
| D1 | Playback state authority principle | Shapes service, UI, everything |
| D2 | Queue ownership direction (preliminary) | Shapes service + queue UI |
| D3 | Single Gradle module vs. multi-module | Project skeleton |
| D4 | Package structure | Discipline boundary in a single module |
| D5 | Derived vs. user-owned data split in schema | Nearly impossible to retrofit |
| D6 | Track identity key approach | Schema depends on it |
| D7 | Navigation: Nav3 vs. Nav2 | Moderately annoying to change later |
| D8 | v1 scope cut (MVP) | Prevents everything else from bloating |
| D9 | minSdk | Small, but needed |
| D10 | Record play events from v1? | Data cannot be recreated (T5) |

**Explicitly open (do not decide yet):**
Sync pipeline details · artwork data model (entity vs. per-track blob) · search depth (plain queries now; FTS later if justified) · recommendation v1 strategy · error taxonomy · logging/observability/crash reporting (privacy stance needed) · testing depth per layer · performance targets · release/distribution/CI details · Android Auto · online services · streaming · any plugin or dynamic-theming mechanism.

---

## 5. Proposed Minimal Architectural Foundation

### 5.1 Sources of truth (the core map)

| Concern | Source of truth | Notes |
|---|---|---|
| What music exists | MediaStore (external world) | Never modified by Kaon |
| Normalized library model | Room (derived tables) | Rebuildable from MediaStore at any time |
| Playlists, favorites, history | Room (user-owned tables) | Kaon's irreplaceable data — migrations are sacred |
| Settings, theme, last queue snapshot | DataStore | Small, preferences-shaped |
| Now-playing, position, play/pause, live queue | Media3 `Player` inside the service | Kaon only observes |
| Artwork pixels | Coil cache | Never a source of truth, only a rendering concern |

The **derived vs. user-owned table split (D5)** is the most important schema decision: derived tables can always be dropped and rescanned (which makes sync-strategy evolution safe); user-owned tables must be migrated with care forever.

### 5.2 Runtime shape

Exactly two Android entry points plus one optional worker:

- `MainActivity` — the entire UI, single activity
- `PlaybackService` (`MediaSessionService`) — owns ExoPlayer + MediaSession; independent of UI lifecycle by construction
- `LibraryScanWorker` (WorkManager) — only if scanning needs to outlive the UI; a coroutine in app scope may suffice initially — open

The UI talks to playback exclusively through a thin connection layer over `MediaController`. The UI never imports Media3 types, never touches Room directly (ViewModels → repositories), and the scanner never touches playback.

### 5.3 Dependency direction

```
ui  ──►  ViewModels  ──►  repositories (data)  ──►  Room / DataStore
 │
 └──►  playback-connection (MediaController)  ──►  PlaybackService ──►  Media3
                                                          │
scanner ──► MediaStore + metadata extraction ──► Room     └── queue snapshot ──► DataStore
```

Rules:
- Dependencies point downward/inward only. Nothing in data or playback knows the UI exists.
- No event bus. State flows upward as `StateFlow`; commands go down as plain function calls. If we ever feel we need an event bus, that's a design smell to investigate, not a mechanism to add.
- Playback failure must never corrupt library state and vice versa; they share only the identity of tracks.

### 5.4 Module and package structure

**Decision:** Start with **one Gradle module** (`:app`), strict package boundaries.
**Reasoning:** One developer; fastest iteration; simplest Hilt/KSP/CI setup; no cross-module version friction. Gradle modules earn their cost (build time, enforced boundaries, reuse) only when the codebase is substantially larger.
**Alternatives:** 3–5 modules (`:app`, `:core:library`, `:core:playback`, …) — better enforcement, but real overhead now and speculative splits tend to be wrong splits.
**Trade-offs:** Package boundaries are discipline-only. Mitigated by mapping packages 1:1 to plausible future modules, so extraction later is mechanical.
**Recommendation:** Single module; revisit when build times hurt or boundaries erode.

```
com.kaon.music
├── app/          Application, Hilt wiring, MainActivity, navigation shell
├── playback/     PlaybackService, player setup, queue snapshot persistence,
│                 MediaController connection layer (the ONLY Media3 imports outside this package)
├── library/      Scanner, sync logic, metadata extraction & normalization (pure, testable)
├── data/         Room (entities/DAOs/db), DataStore, repositories
├── ui/           theme tokens, shared components, feature screens
└── (no "core"/"common" package until something concretely needs it)
```

Note: `library` contains the *pipeline* (MediaStore → extract → normalize → write Room) while `data` owns *storage*. This keeps the scan logic pure-Kotlin and testable without Android instrumentation where possible.

### 5.5 Playback authority (D1) — proposed principle

**Decision:** Media3 `Player` in the service is the sole runtime authority for playback state and the live queue. Kaon observes it (via `MediaController` + `Player.Listener`) and issues commands; it never maintains a parallel state model.
**Reasoning:** Directly satisfies "one authoritative source"; eliminates the drift class of bugs; less code.
**Alternatives:** App-owned state machine synced to Media3 — rejected: doubles state, guarantees eventual desync.
**Trade-offs:** Position/isPlaying must be observed at UI-appropriate cadence rather than stored — a solved problem with `Player.Listener` + polling for position.
**Recommendation:** Adopt as an immutable architectural rule; revisit only if a requirement genuinely cannot be expressed through the Player API.

### 5.6 Queue (D2) — preliminary direction

**Decision (preliminary):** The Media3 playlist is the runtime queue. Kaon persists a queue *snapshot* (ordered track identities + current index + position) on meaningful mutations and periodically; restores it on cold service start; resolves conflicts as "last snapshot wins, skip missing items gracefully."
**Reasoning:** One runtime truth (satisfies T3); persistence is a side artifact, not a competing model.
**Alternatives:** Room table as queue master pushed into Media3 — rejected for v1 (sync complexity with no benefit).
**Trade-offs:** Snapshot cadence and restore fidelity (e.g., position accuracy to the second) need tuning.
**Recommendation:** Adopt direction now; do the detailed design in a dedicated queue round before implementing queue UI.

### 5.7 Track identity (D6) — proposed v1 approach

**Decision:** v1 identity = MediaStore document/URI identity, validated by cheap attributes (size + duration), with raw tags stored separately. Reserve a nullable `fingerprint` column but do not compute fingerprints in v1.
**Reasoning:** Cheap, sufficient for add/remove/move-within-MediaStore correctness. Hashing/fingerprinting every file at scan time costs real IO on large libraries and solves problems (renames outside MediaStore, duplicates) that v1 doesn't handle anyway.
**Alternatives:** Content hash per file — deferred; audio fingerprinting — explicitly out of scope for now.
**Trade-offs:** Tracks moved in ways that change MediaStore identity will lose history/favorite linkage until fingerprints exist. Acceptable for v1, and the reserved column keeps the upgrade path open.
**Recommendation:** Adopt; revisit when duplicate handling or move-resilience becomes a real requirement.

### 5.8 Domain/use-case layer — temporary decision

No dedicated domain layer. ViewModels orchestrate directly against repositories and the playback connection. Extract a use-case class only when logic is genuinely shared between screens or complex enough to isolate (scan orchestration and "play album with shuffle semantics" are the two likely first candidates). Revisit at first sign of ViewModel bloat. This is a **temporary decision**, not a permanent stance.

### 5.9 First milestone: validate the spine, not features

A vertical slice: app shell with theme tokens → scan MediaStore into minimal Room tables (tracks/albums/artists, raw + normalized) → flat track list → tap plays via `PlaybackService` with media notification and surviving backgrounding → queue snapshot survives process death. This exercises every boundary in section 5.1–5.3 with almost no feature code. If this slice feels awkward, the architecture is wrong *before* features are built on it.

---

## 6. Navigation (D7) — decision needed, my read

Navigation 3 is young; its small, state-based model fits Kaon well (few screens, mini-player as persistent scaffold rather than destination, full player likely an overlay/sheet rather than a route). Navigation Compose (Nav2) is battle-tested but its route/argument model is heavier for this shape. Since Kaon starts fresh and ships months from now, **Nav3 is acceptable** — but this is a risk-appetite call only you can make, and the mini-player/full-player/adaptive-layout interaction deserves its own short design discussion before committing. Cheapest fallback if Nav3 churn bites: with ~5 screens, plain state-based navigation in the shell is genuinely fine — but per your rules, we adopt a platform solution first and only do that if it proves insufficient.

---

## 7. Overlooked Areas (identified, not decided)

These need decisions before or shortly after v1 — flagging without inventing requirements:

- **minSdk (D9)** — affects compat code; Media3/Compose run on old APIs, so this is mostly a testing-surface decision. I'd suggest minSdk 26 as a comfortable default for a solo project; your call.
- **Privacy stance concretized** — local-first implies *no analytics/crash reporting by default*. Silent telemetry would contradict the vision; opt-in reporting is a future decision. Decide the stance explicitly so it's not added casually later.
- **Backup rules** — which data should survive device migration (playlists/favorites/history: yes; derived library: doesn't matter). One manifest decision, easy to miss.
- **Observability** — what logging exists in debug vs release; no framework decision needed yet.
- **Testing strategy** — my preliminary priority ranking: scanner/normalization pure logic > queue snapshot restore > ViewModel state > UI smoke. Full strategy is its own discussion.
- **Performance targets** — "fast on modest hardware" needs one concrete reference device + a few measurable criteria (cold start, scroll with artwork, scan throughput). Macrobenchmark is in your stack; define when we first have something to measure.
- **Release scope** — Play Store vs. sideload/F-Droid affects signing, CI, and update assumptions.

---

## 8. Explicit List: What Must NOT Influence the Initial Architecture

- Plugin systems of any kind (runtime themes, UI modules, provider SPIs)
- UI customization beyond theme tokens (rearrangeable layouts, alternative players)
- Recommendation engine, ML, similarity, embeddings — *except* the minimal play-event capture (D10), which is data insurance, not a feature
- Online services: metadata enrichment, lyrics, external artwork, discovery, accounts — Ktor stays out of the dependency set until one of these becomes real
- Streaming (no shared "media source abstraction" — the seam where it would attach is the track-identity model, which we're keeping honest anyway)
- Audio fingerprinting / hashing infrastructure
- Android Auto implementation (structure stays compatible; nothing built)
- Multi-window/tablet-specific architecture beyond normal adaptive-layout practice
- Any event bus, message broker, or reactive infrastructure beyond Flow
- Additional Gradle modules

---

## 9. Proposed Order for the First Decision Rounds

1. **v1 scope cut (D8)** — confirm/adjust the requirement list from section 2. Everything downstream depends on this.
2. **Playback & queue design** (D1/D2 details) — service structure, connection layer, snapshot format and restore rules.
3. **Library schema v1** (D5/D6 applied) — entities, derived/user split, raw-vs-normalized metadata columns, play events (D10).
4. **Sync strategy outline** — initial scan, incremental sync, reconciliation; enough to size the scan pipeline, not a full spec.
5. **Project skeleton** (D3/D4) + minSdk (D9) + navigation commitment (D7) — then the vertical slice.

**Questions for you before round 1:**

1. Does the v1 requirement list in section 2 match your intent? Anything you'd cut or add?
2. Target library size: is "comfortable at 10k tracks, must not break at 50k" the right bar?
3. Record minimal play events from v1 — yes/no? (I recommend yes.)
4. Navigation 3 risk appetite: comfortable, or prefer Nav2 for stability?
5. minSdk: is 26 acceptable, or do you need lower?
6. Privacy stance: confirm "no analytics or crash reporting by default"?

No code until these are settled.
