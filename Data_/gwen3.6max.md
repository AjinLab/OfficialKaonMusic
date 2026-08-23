# Kaon Music — Initial Architectural Analysis

Ephoreee, this is a strong vision, but it also contains several areas where the architecture can become overbuilt very quickly if we treat long-term ideas as first-day requirements.

My initial position is:

> Kaon should begin as a carefully bounded, local-first Android music player with a small number of strong internal boundaries: playback, library synchronization, normalized library storage, artwork loading, and UI composition.  
> It should not begin as a plugin platform, recommendation engine, streaming-ready media kernel, or dynamically customizable shell.

The first architecture should be intentionally boring. The interesting parts of Kaon — metadata intelligence, personalization, customization, and future services — will only succeed if the boring foundation is stable.

---

## 1. Initial Assessment of the Vision

The vision is coherent, but it has different levels of maturity:

### Solid, immediate product concerns

These are real and should shape the first architecture:

* Local music discovery and library storage
* Reliable Media3/ExoPlayer playback
* Background playback and MediaSession integration
* Queue management and queue persistence
* Artwork loading with caching and resizing
* Basic browsing and search
* Responsive Compose UI
* Resource-aware behavior
* Graceful degradation

These are the parts that should influence the initial architecture.

### Important but secondary concerns

These matter, but should not drive the first structure:

* Metadata enrichment
* User corrections
* Advanced recommendation signals
* Personalized discovery
* Genre relationships
* Advanced playlist semantics
* Android Auto browsing
* External metadata/artwork providers

These should be allowed to emerge once the core is stable.

### High-risk architectural ideas

These are dangerous if introduced too early:

* Runtime plugins
* Dynamic UI modules
* A “kernel”
* A generalized extension system
* A recommendation engine abstraction
* Streaming-compatible media model
* Online service abstraction layers
* Heavy modularization before boundaries are proven

They may become useful later, but they should not influence the initial system.

---

## 2. Contradictions, Ambiguities, and Architectural Risks

### 2.1 “Modular” versus “lightweight for one developer”

The project emphasizes modularity, but also lightweight development and limited time.

Risk:

* Creating many Gradle modules too early
* Adding dependency boundaries before responsibilities are understood
* Paying build/configuration cost for separation that has not proven useful

My view:

> Kaon should start as a modular monolith: one Gradle module with strong package boundaries.  
> Physical Gradle modularization should come later, only when it solves a concrete problem.

---

### 2.2 “One authoritative playback state” versus “queue persistence”

This is one of the most important risks.

If Kaon maintains its own live queue state and Media3 maintains its own live queue state, you will end up with two competing state machines.

The correct split is likely:

* Media3 owns the active playback queue and playback state while playback is alive.
* Kaon owns persistence and restoration of the queue.
* Kaon does not treat the persisted queue as live truth while playback is active.

If we get this wrong, queue behavior will become fragile very quickly.

---

### 2.3 “Metadata should be normalized” versus “files move, rename, and change”

This is harder than it looks.

Raw tags are unreliable:

* Missing artist
* Misspelled album names
* Duplicate albums
* Inconsistent album artists
* File renames
* File moves
* MediaStore ID changes
* Duplicate files
* Corrupt tags

If Kaon identifies music only by file path or MediaStore ID, references will break.

If Kaon identifies music only by metadata, false matches may occur.

The architecture needs a conservative identity model:

* Internal Kaon track ID
* External MediaStore reference
* Normalized metadata keys
* Optional content fingerprint for future reconciliation

This should be designed carefully, but not over-engineered.

---

### 2.4 “Local-first” versus “future online enrichment”

The vision includes metadata enrichment, artwork, lyrics, discovery, and possibly recommendations.

Risk:

* Introducing a generic “online service” abstraction before knowing what any service actually needs
* Making local behavior depend on network availability
* Creating a provider architecture before there is one real provider

Recommendation:

> Keep the core fully local. When the first online capability appears, introduce the smallest boundary that serves that capability.

Do not build a universal external-service framework now.

---

### 2.5 “Deep customization” versus “maintainable UI”

Customization is a real product idea, but it can easily become architecturally destructive.

Risk:

* Runtime themes
* Swappable player layouts
* Pluggable UI modules
* User-defined screens
* Dynamic component systems

These can turn the UI layer into a platform instead of a product.

Initial recommendation:

* Use Material 3
* Use a small design-token layer
* Support light/dark theme
* Support basic user preferences
* Defer runtime UI plugin systems entirely

---

### 2.6 “Recommendation system” versus “no speculative abstraction”

The recommendation vision is sensible, but it is not an initial architectural requirement.

Risk:

* Creating a `RecommendationEngine` interface before we know what a useful recommendation is
* Designing for ML before simple heuristics are validated
* Storing excessive listening data without a retention model

Initial recommendation:

* Store enough listening history to support “recently played” and “most played”
* Do not build recommendation infrastructure yet
* Let the first recommendation feature be a simple query or heuristic when needed

---

### 2.7 Navigation 3 versus project stability

Navigation 3 is in your preferred stack, but this needs validation.

Risk:

* Using a navigation library whose maturity, documentation, or behavior is not yet proven for Kaon’s player-shell requirements
* Creating custom navigation workarounds if Nav3 does not handle persistent mini-player/full-player behavior well

My current position:

> Use official navigation, but verify Navigation 3 before committing.  
> If Navigation 3 is stable and fits the player-shell model, use it.  
> If it creates friction around back stack, sheets, adaptive layouts, or state restoration, use Navigation Compose instead.

This is a decision to settle early.

---

## 3. Actual Requirements Versus Long-Term Ideas

### 3.1 Actual requirements for the first real version

These should shape the initial architecture:

* Local audio discovery through MediaStore
* Initial and incremental library synchronization
* Room-backed library storage
* Track, album, artist browsing
* Basic search
* Media3-based playback
* Background playback
* MediaSession/notification/lock-screen controls
* Audio focus and noisy handling
* Queue display and basic queue mutation
* Queue persistence and restoration
* Artwork loading with caching
* Theme support
* Responsive Compose UI
* Permission handling
* Graceful error handling

### 3.2 Necessary soon, but not necessarily on day one

These should be considered, but not fully designed yet:

* Playlists
* Favorites
* History
* Recently played
* Most played
* Recently added
* Genre browsing
* Folder browsing
* User metadata corrections
* Advanced duplicate handling
* Artwork fallback strategies
* Advanced search ranking

### 3.3 Long-term ideas that should not drive the first architecture

These should not influence the initial architecture yet:

* Streaming
* Plugin architecture
* Runtime themes beyond simple tokens
* Dynamic UI modules
* Recommendation engine abstraction
* Machine learning
* Online metadata enrichment
* Online artwork lookup
* Lyrics
* Cloud sync
* Multi-account systems
* Android Auto browsing
* Generic external-service abstraction
* User-defined layouts
* Advanced personalization models

These may become real later. For now, they are possibilities, not constraints.

---

## 4. What Must Be Decided Now Versus What Can Remain Open

### Decisions that matter now

These should be settled before implementation:

1. Minimum supported Android version
2. Initial feature scope
3. Single Gradle module versus early multi-module
4. Playback state authority
5. Queue persistence and restore model
6. Track identity model
7. Library synchronization strategy
8. Initial database schema direction
9. Navigation approach and player-shell structure
10. Error-handling and logging baseline
11. Testing boundaries
12. Whether Navigation 3 is acceptable for production

### Decisions that can remain open

These do not need final answers yet:

1. Recommendation algorithm
2. Online metadata providers
3. Online artwork providers
4. Streaming model
5. Plugin architecture
6. Dynamic UI customization
7. Full-text search engine
8. History retention policy
9. Advanced genre model
10. Android Auto support
11. Lyrics
12. Backup and restore details
13. Analytics or crash-reporting policy
14. Advanced performance targets

---

## 5. Smallest Sensible Architectural Foundation

The smallest sensible foundation is not a large layered clean-architecture system. It is a small set of meaningful boundaries.

### Core boundaries

Kaon should initially have these boundaries:

1. **Playback boundary**  
   Owns Media3, MediaSession, active queue, playback state, audio focus, and playback commands.

2. **Library boundary**  
   Owns MediaStore observation, synchronization, normalization, and library querying.

3. **Persistence boundary**  
   Owns Room and DataStore access.

4. **Artwork boundary**  
   Owns artwork loading, caching, sizing, and failure behavior.

5. **UI boundary**  
   Owns Compose screens, design-system components, navigation shell, and view models.

That is enough for the first stage.

We do not need a separate “domain kernel”, event bus, plugin registry, or generic service layer yet.

---

## 6. Major Sources of Truth

This is critical.

### Active playback

**Source of truth:** Media3 Player inside `MediaSessionService`.

Owns:

* Current media item
* Play/pause state
* Buffering state
* Position during active playback
* Repeat mode
* Shuffle mode
* Active timeline/queue while playback is alive

Kaon should derive UI state from this, not duplicate it.

---

### Persisted queue

**Source of truth:** Room queue snapshot, but only for restoration.

Owns:

* Last known queue items
* Last known index
* Last known position
* Repeat/shuffle preference
* Restoration metadata

It should not compete with Media3 while playback is active.

---

### Library

**Source of truth:** Room database for Kaon’s normalized library view.

MediaStore is the external source of file facts, but Kaon should not make the UI query MediaStore repeatedly.

Room owns:

* Normalized track rows
* Library availability
* Basic grouping keys
* Playlist entries later
* History and favorites later

---

### User preferences

**Source of truth:** DataStore.

Owns:

* Theme preference
* Playback preferences
* Library display preferences
* Permission/onboarding state if useful

---

### Listening facts

**Source of truth:** Room history/favorites tables.

Owns:

* Play history
* Favorite state
* Later: skips, completion, aggregation

This should be minimal initially.

---

## 7. Dependency Direction

The dependency direction should remain explicit and mostly one-directional.

```text
UI Features
   ↓
ViewModels
   ↓
Core Controllers / Repositories
   ↓
Data Sources
   ↓
Media3 / Room / MediaStore / DataStore / Coil
```

More specifically:

```text
feature/library      → core/library
feature/player       → core/playback
feature/search       → core/library
feature/settings     → core/datastore

core/library         → core/database, core/mediastore, core/model
core/playback        → core/model, Media3, queue persistence contract
core/artwork         → Coil, core/model
core/database        → Room, core/model
core/datastore       → DataStore, core/model
```

Rules:

* Core packages should not depend on feature packages.
* Playback should not depend on library internals.
* Library should not depend on playback state.
* UI should not talk directly to MediaStore or Room except through core components.
* Design-system components should not contain product logic.

This gives us many of the benefits of modularity without immediate Gradle module overhead.

---

## 8. Proposed Initial Repository / Module Structure

I recommend starting with **one Gradle module**.

The project can still be modular internally through package boundaries.

### Repository layout

```text
kaon-music/
├── .github/
│   └── workflows/
│       └── ci.yml
├── docs/
│   └── architecture/
│       └── decisions/
├── gradle/
│   └── libs.versions.toml
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/dev/kaon/music/
│   │   │   │   ├── app/
│   │   │   │   ├── core/
│   │   │   │   │   ├── common/
│   │   │   │   │   ├── model/
│   │   │   │   │   ├── database/
│   │   │   │   │   ├── datastore/
│   │   │   │   │   ├── mediastore/
│   │   │   │   │   ├── library/
│   │   │   │   │   ├── playback/
│   │   │   │   │   └── artwork/
│   │   │   │   ├── designsystem/
│   │   │   │   └── feature/
│   │   │   │       ├── library/
│   │   │   │       ├── player/
│   │   │   │       ├── search/
│   │   │   │       ├── playlists/
│   │   │   │       └── settings/
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   └── test/
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

### Package responsibilities

#### `app`

Application-level wiring:

* Hilt application
* Main activity
* Navigation host
* App-level scaffold
* Player shell composition
* Permission flow coordination

#### `core/common`

Small shared utilities:

* Dispatchers
* Result/error helpers if needed
* Logging helpers
* Time/format utilities

Avoid turning this into a junk drawer.

#### `core/model`

Plain models used across boundaries:

* Track
* Album summary
* Artist summary
* Queue snapshot
* Playback state snapshot
* Library sync status

These should be mostly stable and Android-light.

#### `core/database`

Room-specific implementation:

* Entities
* DAOs
* Database
* Migrations
* Type converters

#### `core/datastore`

Preference storage:

* Theme
* Playback settings
* Library display settings

#### `core/mediastore`

MediaStore access:

* Query logic
* Change observation
* MediaStore row mapping
* Permission-aware access

This should not contain normalization policy beyond basic mapping.

#### `core/library`

Library domain logic:

* Sync orchestration
* Normalization
* Reconciliation
* Library repository
* Search queries
* Browse queries

This is one of the most important boundaries.

#### `core/playback`

Playback coordination:

* MediaSessionService
* ExoPlayer setup
* MediaController connection
* Playback state observation
* Queue command handling
* Queue restoration coordination

This is the other critical boundary.

#### `core/artwork`

Artwork loading:

* Coil setup
* Artwork keys
* Embedded artwork extraction strategy later
* Error/fallback behavior

#### `designsystem`

Reusable UI primitives:

* Typography
* Spacing
* Color/theme tokens
* Common components
* Empty/error/loading states

No feature logic.

#### `feature/*`

Screen-level features:

* Library browsing
* Player UI
* Search
* Playlists later
* Settings

Features depend on core APIs, not on each other unless clearly justified.

---

## 9. When Should Kaon Split Into Real Gradle Modules?

Not yet.

A Gradle module should be introduced when one or more of these becomes true:

1. A boundary needs compile-time enforcement.
2. A component becomes reusable across multiple applications or build variants.
3. Build time would meaningfully improve.
4. A team or test boundary needs stronger isolation.
5. A module has stable public APIs and independent ownership.
6. A feature needs different dependency sets that are currently polluting the app module.

Possible future modules:

```text
:core:model
:core:database
:core:playback
:core:library
:core:artwork
:designsystem
:feature:library
:feature:player
:feature:search
:app
```

But these should be extracted later, not created speculatively.

---

# 10. Key Architectural Decisions

## Decision 1: Initial Module Structure

**Decision:**  
Start with one Gradle module and strict package boundaries.

**Reasoning:**  
Kaon is being developed primarily by one developer. The first version needs fast iteration, low configuration overhead, and room for boundaries to stabilize. Multi-module builds add friction before the actual boundaries are proven.

**Alternatives:**

1. Single module with package boundaries
2. Small number of core modules
3. Large feature-based modular structure

**Trade-offs:**

* Single module gives weaker compile-time boundary enforcement.
* Multi-module gives stronger boundaries but increases build and DI complexity.
* Large modularization is premature for one developer and an evolving product.

**Recommendation:**  
Use a modular monolith. Revisit modularization after the playback, library, and player-shell boundaries have stabilized.

---

## Decision 2: Playback State Authority

**Decision:**  
Media3 Player inside `MediaSessionService` is the authoritative source of active playback state.

**Reasoning:**  
Media3 already handles many concerns Kaon must not reimplement:

* MediaSession
* Notification
* External controls
* Audio focus
* Queue timeline
* Position state
* Playback lifecycle

If Kaon builds its own parallel playback state machine, synchronization bugs will follow.

**Alternatives:**

1. Media3 is authoritative.
2. Kaon domain model is authoritative and commands Media3.
3. Both maintain state and synchronize.

**Trade-offs:**

* Letting Media3 be authoritative means Kaon must adapt to Media3’s event model.
* Kaon-owned state gives more apparent control but creates duplicate truth.
* Dual-state synchronization is the most fragile option.

**Recommendation:**  
Use Media3 as live playback authority. Expose a Kaon-facing `PlaybackController` that observes Media3 and provides clean state flows to the UI.

---

## Decision 3: Queue Persistence

**Decision:**  
Persist the queue in Room as a restoration snapshot, but do not treat it as the active queue while playback is alive.

**Reasoning:**  
Users will expect the queue to survive process death, service restart, and app relaunch. However, active queue mutations should go through Media3 to avoid state divergence.

**Alternatives:**

1. No persistence
2. Room snapshot for restoration only
3. Live Room-backed queue synchronized with Media3

**Trade-offs:**

* No persistence is simple but poor UX.
* Room snapshot adds persistence complexity but keeps live state clean.
* Live Room-backed queue risks two competing queue truths.

**Recommendation:**

* Queue mutations go through the playback controller.
* Playback controller mutates Media3.
* A persistence component observes meaningful changes and writes a queue snapshot.
* On cold restore, Room snapshot is loaded into Media3.
* If items are missing, skip or remove them gracefully.

Persist at least:

* Queue item order
* Current index
* Playback position
* Repeat mode
* Shuffle state
* Updated timestamp

Exact shuffle-order restoration can be refined later.

---

## Decision 4: Metadata Identity

**Decision:**  
Use a layered identity model:

1. Internal Kaon track ID as the stable app-level reference.
2. MediaStore ID/URI as the current external source reference.
3. Normalized metadata keys for grouping and search.
4. Optional content fingerprint for future reconciliation.

**Reasoning:**  
No single identifier is sufficient:

* File path breaks when files move.
* MediaStore ID can change.
* Metadata alone can collide.
* Hashing file contents can be expensive and unstable for edited files.

**Alternatives:**

1. Use file path only.
2. Use MediaStore ID only.
3. Use metadata fingerprint only.
4. Use hash-based identity.
5. Use layered identity.

**Trade-offs:**

* Layered identity is more complex than a single ID.
* Simpler identifiers will break under real-world file changes.
* Fingerprint reconciliation must be conservative to avoid false matches.

**Recommendation:**  
For the initial architecture:

* Every track row has an internal primary key.
* Store MediaStore ID and URI.
* Store raw metadata fields.
* Store normalized fields: title, artist key, album key, album artist key.
* Store a conservative content fingerprint based on normalized metadata and duration.
* Prefer marking missing tracks instead of hard-deleting them immediately.

This gives room for better reconciliation later without requiring a full identity engine now.

---

## Decision 5: Library Synchronization

**Decision:**  
Use MediaStore observation plus WorkManager-backed synchronization, with Room as the durable library store.

**Reasoning:**  
The UI must not repeatedly scan MediaStore. Library synchronization must survive process death and should not block the main thread.

**Alternatives:**

1. Full scan every time
2. Observer-triggered coroutine only
3. Observer + WorkManager sync
4. Fully custom sync engine

**Trade-offs:**

* Full scan is simple but slow for large libraries.
* Coroutine-only sync may not survive process death or retries well.
* WorkManager adds some complexity but gives durable background execution.
* Custom sync engine is unnecessary.

**Recommendation:**

* Use ContentObserver to detect MediaStore changes.
* Enqueue unique sync work with WorkManager.
* First launch performs a full initial scan.
* Later syncs should be incremental where possible.
* Use MediaStore change mechanisms where available, with fallback reconciliation.
* Store sync state/token in Room or DataStore.
* Update Room transactionally.
* UI observes Room flows, not scan progress directly.

This keeps the UI responsive and avoids repeated discovery work.

---

## Decision 6: Initial Database Direction

**Decision:**  
Start with a minimal normalized library schema, but avoid over-normalizing everything immediately.

**Reasoning:**  
Kaon needs reliable querying, grouping, and future metadata enrichment, but it does not need a fully normalized music brain on day one.

**Initial tables likely needed:**

### `track`

Core library entity.

Important fields:

* Internal ID
* MediaStore ID
* URI
* Relative path or folder reference
* File name
* Duration
* Date added
* Date modified
* Size
* MIME type
* Raw title
* Raw artist
* Raw album
* Raw album artist
* Raw genre, if available
* Track number
* Disc number
* Normalized title
* Artist key
* Album key
* Album artist key
* Content fingerprint
* Artwork key
* Missing/unavailable flag
* Last seen timestamp

### `queue_state`

Singleton-like row for queue restoration.

Fields:

* Repeat mode
* Shuffle enabled
* Current index
* Position
* Updated timestamp

### `queue_item`

Persisted queue items.

Fields:

* Queue position
* Track reference
* Fallback URI if useful
* Added timestamp

### `playback_history`

Minimal listening facts.

Fields:

* Track reference
* Played timestamp
* Optional event type later
* Optional completed flag later

Retention policy can remain open.

### `favorite_track`

Simple favorite state.

Fields:

* Track reference
* Created timestamp

### `playlist` and `playlist_entry`

These can be introduced when playlists become an actual feature. They do not need to block the first architecture.

### Albums and artists

For the first version, album and artist browsing can be derived from normalized track keys.

This avoids premature album/artist table complexity.

Add dedicated `album` and `artist` tables when Kaon needs:

* Album enrichment
* Artist images
* User corrections
* External IDs
* Relationship data
* Better duplicate grouping

**Trade-offs:**

* Derived album/artist screens are simpler but less future-proof.
* Dedicated album/artist tables provide stronger structure but add sync and migration complexity.

**Recommendation:**  
Start with track-centric normalization. Add album/artist entities when enrichment or user corrections become real requirements.

---

## Decision 7: Navigation and Player Shell

**Decision:**  
Use official navigation, but validate Navigation 3 before committing. Keep the mini-player outside the normal screen back stack.

**Reasoning:**  
The player shell is one of Kaon’s most important UI structures.

The mini-player should persist across navigation destinations. The full player should usually behave like a modal or expandable surface, not necessarily a navigation destination.

**Alternatives:**

1. Navigation 3
2. Navigation Compose
3. Custom back-stack handling

**Trade-offs:**

* Navigation 3 may offer a newer model, but maturity must be validated.
* Navigation Compose is known and stable.
* Custom navigation is unnecessary and risky.

**Recommendation:**

* If Navigation 3 is stable and handles the player shell cleanly, use it.
* If not, use Navigation Compose.
* Keep mini-player and full-player state in a player-shell controller or UI-state holder.
* Avoid treating the full player as a normal destination unless deep-linking or back-stack behavior clearly benefits.

This area should be prototyped before building many screens.

---

## Decision 8: Domain / Use-Case Layer

**Decision:**  
Do not create a blanket use-case layer.

**Reasoning:**  
Many operations in Kaon are simple repository or controller calls. Turning every operation into a use-case class adds noise and increases maintenance cost.

**Alternatives:**

1. No use-case layer
2. Use cases everywhere
3. Selective use cases for complex orchestration

**Trade-offs:**

* No use-case layer can let logic accumulate in repositories.
* Use cases everywhere creates class explosion.
* Selective use cases require judgment.

**Recommendation:**

Use repositories and controllers first.

Introduce a use-case only when:

* It coordinates multiple repositories meaningfully
* It contains non-trivial business rules
* It is reused across multiple features
* It improves testability in a concrete way

Examples where use cases may later make sense:

* Complex library reconciliation
* Queue restoration with missing-item policy
* Recommendation generation
* Metadata enrichment pipeline

Examples where they probably do not make sense initially:

* Get albums
* Get songs
* Toggle favorite
* Add to queue
* Open settings

---

## Decision 9: Plugin Architecture

**Decision:**  
Do not introduce a runtime plugin architecture now.

**Reasoning:**  
There is no concrete requirement that justifies runtime plugins. The ideas in the vision — themes, metadata providers, recommendation engines, external services — do not need one shared plugin mechanism.

**Alternatives:**

1. Runtime plugin system
2. Dynamic feature modules
3. Ordinary interfaces and DI
4. Normal Gradle modules later

**Trade-offs:**

* Plugin systems increase complexity, testing burden, and failure modes.
* Interfaces and modules are simpler and easier to maintain.
* Future extensibility may require refactoring, but that is acceptable.

**Recommendation:**

Use ordinary Kotlin interfaces only where a real boundary exists:

* Playback controller abstraction for UI/testability
* Recommendation strategy later
* Metadata enrichment source later
* Artwork source later if needed

Do not build a plugin registry, extension manager, or dynamic UI module system.

---

## Decision 10: Search

**Decision:**  
Start with ordinary Room queries against normalized fields.

**Reasoning:**  
Search does not initially need a dedicated engine. Room queries can cover tracks, albums, artists, playlists, and simple relevance ordering.

**Alternatives:**

1. Room `LIKE` queries
2. Room FTS5
3. SQLite custom tokenizer
4. External search engine

**Trade-offs:**

* Room queries are simple but limited.
* FTS5 improves search quality but adds schema and maintenance complexity.
* External engines are unnecessary.

**Recommendation:**

Start with:

* Normalized title/artist/album fields
* Indexed columns
* Debounced queries
* Limited result counts
* Separate result groups

Introduce FTS5 only when real search quality requirements justify it.

---

## Decision 11: Background Work Policy

**Decision:**  
Match the mechanism to the work’s durability requirement.

**Reasoning:**  
Not all background work should use WorkManager. Not all work should be tied to UI lifecycle.

**Recommendation:**

### Immediate coroutine work

Use for:

* Short-lived repository operations
* Search queries
* UI-initiated mutations
* Preference changes

### Lifecycle-aware collection

Use for:

* Observing Room flows
* Observing playback state
* Observing sync status

### WorkManager

Use for:

* Library synchronization
* Batch metadata processing later
* Artwork cache cleanup if needed
* Other durable, retryable work

### MediaSessionService

Use for:

* Playback
* Media controls
* Notification
* External control handling

Avoid creating background work simply because WorkManager exists.

---

# 12. Recommended Initial Architectural Shape

Based on the above, the first architecture should look like this:

```text
┌────────────────────────────────────────────┐
│                  UI Layer                  │
│                                            │
│ Compose screens, mini-player, full player  │
│ ViewModels, navigation shell               │
└──────────────────────┬─────────────────────┘
                       │
┌──────────────────────▼─────────────────────┐
│             Core Controllers               │
│                                            │
│ PlaybackController                         │
│ LibraryRepository                          │
│ SearchRepository                           │
│ SettingsRepository                         │
│ ArtworkLoader                              │
└──────────────────────┬─────────────────────┘
                       │
┌──────────────────────▼─────────────────────┐
│              Data Sources                  │
│                                            │
│ Room                                       │
│ MediaStore                                 │
│ DataStore                                  │
│ Media3 / ExoPlayer                         │
│ Coil                                       │
└────────────────────────────────────────────┘
```

This is deliberately not a heavy layered architecture. It is a small set of explicit responsibilities.

---

# 13. Important Temporary Decisions

These are not permanent architectural commitments.

### Temporary Decision 1: Single Gradle module

We should stay single-module until boundaries stabilize.

### Temporary Decision 2: Derived album/artist browsing

Albums and artists can initially be derived from track keys instead of full entities.

### Temporary Decision 3: Room-based search

No FTS5 until needed.

### Temporary Decision 4: Minimal history

Store basic play events. Do not design a full personalization model yet.

### Temporary Decision 5: No online services

Ktor and serialization remain in the catalog but are not architecturally active yet.

### Temporary Decision 6: No recommendation abstraction

No recommendation interface until the first concrete recommendation feature is defined.

### Temporary Decision 7: No runtime theming engine

Only Material 3 token-based theming initially.

---

# 14. Parts of the Long-Term Vision That Should NOT Influence Initial Architecture Yet

These should be explicitly postponed:

1. Runtime plugin system
2. Dynamic UI modules
3. User-customizable layouts
4. Streaming architecture
5. Recommendation engine abstraction
6. Machine-learning personalization
7. Online metadata enrichment
8. Online artwork lookup
9. Lyrics
10. Android Auto browsing tree
11. Cloud synchronization
12. Multi-account or multi-library support
13. Generic external-service platform
14. Advanced folder power-user features
15. User tag-editing engine

This does not reject the vision. It prevents the vision from becoming accidental complexity.

---

# 15. Open Areas You Asked to Identify

These are not requirements yet, but they should not be ignored.

## 15.1 Minimum SDK

This is a missing decision.

It affects:

* MediaStore APIs
* Permissions
* Notification behavior
* Scoped storage
* Media3 behavior
* Compose capabilities
* Background work constraints

We need to choose a minimum SDK before implementation.

Assumption for now: probably API 26 or higher, but this should be decided explicitly.

---

## 15.2 MVP Scope

We need to define the first deliverable.

Possible MVP:

* Permission flow
* Initial library scan
* Songs/albums/artists browsing
* Basic search
* Playback
* Mini-player
* Full player
* Queue screen
* Notification controls
* Theme support
* Basic settings

Playlists, favorites, history, and recently played may belong in MVP or second iteration. This needs a product decision.

---

## 15.3 Observability

We need a simple policy for:

* Logging
* Error reporting
* Debug screens
* Sync diagnostics
* Playback diagnostics

We do not need analytics. In fact, for a private local-first app, avoiding analytics may be a feature.

---

## 15.4 Accessibility

This should be a design-system requirement, not an afterthought.

Important areas:

* Touch targets
* Content descriptions
* TalkBack behavior
* Dynamic text
* Contrast
* Keyboard/switch access where relevant
* Player controls accessibility

---

## 15.5 Device Compatibility

We need to consider:

* Low-memory devices
* Large libraries
* Bluetooth quirks
* Headset controls
* Background restrictions
* Battery saver modes
* Notification permission behavior

This does not require special architecture yet, but it should influence testing.

---

## 15.6 Performance Targets

We should eventually define practical targets, for example:

* Cold start time
* Time to browsable library
* Scroll performance
* Memory usage with large artwork lists
* Sync time for 10,000 tracks
* Playback restore time

We should not invent exact numbers yet, but we should decide that measurement matters.

---

## 15.7 Testing Strategy

Testing should be boundary-based.

Important test areas:

* Metadata normalization
* Sync reconciliation
* Queue persistence/restoration
* Playback controller state mapping
* Room DAOs
* Search queries
* Critical Compose UI flows

We should avoid trying to UI-test everything. Focus on fragile behavior.

---

## 15.8 Privacy

Kaon is local-first. This should be explicit.

Questions:

* Is listening history private?
* Is history backed up?
* Is any telemetry allowed?
* Are network features opt-in?
* How are online requests identified?

No answers are needed immediately, but the default should be private/local.

---

## 15.9 Backup and Restore

Open questions:

* Should preferences be backed up?
* Should history be backed up?
* Should playlists be exportable?
* Should queue state be backed up?
* Should the database participate in Android backup?

This can be postponed briefly but should not be forgotten.

---

# 16. First Architectural Decisions We Should Settle Before Implementation

I recommend we settle these first, in this order:

## 1. Minimum SDK

This affects too many things to leave undecided.

## 2. Initial product scope

We need to know whether the first vertical slice includes:

* Playlists
* Favorites
* History
* Search
* Genre browsing
* Folder browsing

## 3. Single-module structure

I recommend yes, but this should be confirmed.

## 4. Playback and queue authority model

This is the most technically important decision.

## 5. Metadata identity and missing-file policy

This affects database schema and future reconciliation.

## 6. Library sync mechanism

This affects Room schema, WorkManager usage, and UI state.

## 7. Navigation approach

Especially:

* Navigation 3 viability
* Mini-player persistence
* Full-player presentation

## 8. Initial database schema direction

Especially whether album/artist tables exist immediately or are derived.

---

# 17. My Current Recommendation

If I had to summarize the recommended first architecture in one paragraph:

> Kaon should begin as a single-module Android application with strict internal package boundaries. Media3 inside a MediaSessionService should be the authoritative playback engine. Room should be the normalized local library source of truth, synchronized from MediaStore through WorkManager-backed sync work. The queue should be persisted as a restoration snapshot, not treated as a second live playback state. Artwork should be handled through Coil with stable keys and graceful failure. The UI should use Compose with a persistent player shell. No plugin system, recommendation engine, streaming abstraction, or online-service framework should be introduced yet.

That gives Kaon the strongest chance of becoming the richer system in your vision without collapsing under premature complexity.

---

# 18. Where I Would Push Back Most Strongly

The areas where I would challenge the current vision most directly are:

1. **Do not build a plugin architecture early.**  
   It is likely to become infrastructure without a product.

2. **Do not design for streaming before local playback is excellent.**  
   Streaming changes media source models, authentication, caching, and queue semantics. It should not shape the local-first core.

3. **Do not create a recommendation abstraction before the first recommendation exists.**  
   The first version should probably be a simple query or heuristic.

4. **Do not build dynamic UI customization before the design system is stable.**  
   Customization amplifies UI complexity.

5. **Do not over-normalize metadata before understanding real-world library messiness.**  
   Start conservative and evolve.

---

# 19. Suggested Next Step

Before any substantial code is generated, we should decide:

1. Minimum SDK
2. MVP feature scope
3. Whether Navigation 3 is acceptable after a quick spike
4. Playback/queue authority model
5. Initial track identity and database direction

If you want, the next response can focus only on one of these. My recommended first discussion is:

> **Playback, queue persistence, and Media3 authority.**

That is the highest-risk technical area and the one most likely to cause rework if handled poorly.
