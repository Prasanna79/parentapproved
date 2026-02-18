# KidsWatch v0.2.2 — Moldable Development Adoption

**Date:** February 17, 2026
**Scope:** Architectural refactor only. No new features. All existing tests must continue to pass.
**Goal:** Restructure the codebase so the system explains itself through domain objects, not framework abstractions.

---

## Why This Release Exists

KidsWatch is built by Claude Code with Prasanna as PM. The current development loop is:

1. Prasanna writes a spec
2. Claude Code reads source files, reconstructs understanding, implements
3. Deploy to device, test
4. When something breaks: Prasanna describes the symptom in words → Claude Code hypothesizes by grepping through source → fix attempt → repeat

Two bottlenecks dominate:

**Comprehension cost.** Every task requires Claude Code to reconstruct domain understanding from Android framework code. The code is organized around Room DAOs, Ktor route handlers, ExoPlayer callbacks, and Compose ViewModels. To answer "why did this playlist fail to load?", Claude Code must trace through `PlaylistRepository`, `NewPipeExtractor` wrapper, `CachedVideoDao`, error handling spread across multiple files, and ViewModel state. The domain is invisible — buried inside framework plumbing.

**Feedback cost.** Test failures say *what* broke but not *why*. Bug reports are verbal descriptions of symptoms. Debug intents return raw state dumps. When the system misbehaves, nobody — not Prasanna, not Claude Code — can quickly see what actually happened inside the domain.

This release restructures the code so that **the domain is the primary structure** and **the system can explain what happened and why**.

---

## What Is Moldable Development (For Claude Code)

Moldable development is an approach where every domain concept in the system is represented by a rich object that:

1. **Encapsulates its own logic** — not scattered across framework layers
2. **Records what happened to it** — not just current state, but history and transitions
3. **Can explain itself** — has methods/properties that answer domain questions directly
4. **Is inspectable** — can be serialized, queried, and displayed without special tooling

This is NOT about building debug UIs or dashboards. It is about how the code is structured. The inspectability is a consequence of the structure, not the goal.

### The Practical Difference

**Before (framework-organized):**
```
PlaylistRepository.kt      — calls NewPipe, writes to Room
CachedVideoDao.kt          — raw SQL queries
PlaylistViewModel.kt       — UI state, loading flags
KtorRoutes.kt              — HTTP handlers that query DAO directly
NewPipeWrapper.kt           — extraction calls, error handling
```

To understand "what happened when playlist X was resolved," Claude Code must read all five files and mentally reconstruct the flow.

**After (domain-organized):**
```
ResolutionAttempt.kt        — one object that IS the resolution: input, steps, timing, outcome, errors
PlaybackSession.kt          — one object that IS a playback: video, stream selection, player states, how it ended
ParentAction.kt             — one object that IS a parent operation: what, when, result
WatchableContent.kt         — one object that answers: what can the kid watch right now?
```

To understand "what happened when playlist X was resolved," Claude Code reads `ResolutionAttempt` — it contains everything.

---

## Domain Objects to Introduce

### 1. ResolutionAttempt

**What it represents:** A single attempt to resolve a playlist's videos via NewPipe.

**What it captures:**
```kotlin
data class ResolutionAttempt(
    val playlistId: String,
    val startedAt: Instant,
    val completedAt: Instant?,
    val pagesRequested: Int,
    val pageResults: List<PageResult>,  // per-page: count, duration, errors
    val videosResolved: Int,
    val videosCached: Int,
    val outcome: ResolutionOutcome,     // SUCCESS, PARTIAL, NETWORK_ERROR, PARSE_ERROR, PLAYLIST_GONE
    val error: String?,                 // if failed, human-readable why
    val cacheFallback: Boolean,         // did we fall back to stale cache?
    val durationMs: Long
)

data class PageResult(
    val pageNumber: Int,
    val videosFound: Int,
    val durationMs: Long,
    val error: String?                  // null = success
)

enum class ResolutionOutcome {
    SUCCESS,          // all pages resolved, cache updated
    PARTIAL,          // some pages failed, partial cache update
    NETWORK_ERROR,    // couldn't reach YouTube at all
    PARSE_ERROR,      // NewPipe couldn't parse the response
    PLAYLIST_GONE,    // playlist deleted or made private
    CACHE_FALLBACK    // failed entirely, serving stale cache
}
```

**Where the logic lives:** The resolution logic (currently in `PlaylistRepository` or similar) moves INTO this object or into a builder/factory that produces it. The function that resolves a playlist returns a `ResolutionAttempt`, not just a list of videos.

**What it replaces:** Scattered try/catch blocks, log statements, and boolean flags across repository and DAO layers.

**Persistence:** Store as a Room entity (`resolution_attempts` table). Keep last 3 attempts per playlist. Queryable from Ktor debug endpoints and from tests.

### 2. PlaybackSession

**What it represents:** Everything that happens from when a kid selects a video to when playback ends.

**What it captures:**
```kotlin
data class PlaybackSession(
    val sessionId: String,              // UUID
    val videoId: String,
    val videoTitle: String,
    val playlistId: String,
    val playlistPosition: Int,
    val startedAt: Instant,
    val endedAt: Instant?,
    val streamSelection: StreamSelection,
    val playerStates: List<PlayerStateChange>,
    val keyEvents: List<KeyEventRecord>,
    val endReason: PlaybackEndReason,
    val durationWatchedMs: Long,
    val completedPercent: Float
)

data class StreamSelection(
    val availableStreams: List<StreamOption>,  // what NewPipe offered
    val selectedStream: StreamOption,          // what we picked and why
    val selectionReason: String,               // "1080p progressive preferred"
    val extractionDurationMs: Long
)

data class StreamOption(
    val resolution: String,       // "720p", "1080p"
    val format: String,           // "progressive", "adaptive"
    val codec: String,
    val bitrateKbps: Int?
)

data class PlayerStateChange(
    val fromState: String,        // ExoPlayer state name
    val toState: String,
    val at: Instant,
    val metadata: String?         // e.g., "buffer empty", "seek to 45s"
)

data class KeyEventRecord(
    val keyCode: Int,
    val keyName: String,          // "DPAD_CENTER", "DPAD_RIGHT", "BACK"
    val action: String,           // what the app did: "toggle_pause", "next_video", "navigate_home"
    val at: Instant
)

enum class PlaybackEndReason {
    COMPLETED,          // video finished naturally
    USER_NEXT,          // kid pressed next
    USER_BACK,          // kid pressed back
    PARENT_STOP,        // parent pressed stop on dashboard
    PARENT_SKIP,        // parent pressed next on dashboard
    EXTRACTION_FAILED,  // couldn't get stream URL
    PLAYER_ERROR,       // ExoPlayer error
    APP_BACKGROUNDED    // TV went to sleep or app lost focus
}
```

**Where the logic lives:** The PlaybackSession is created when a video is selected and finalized when playback ends. ExoPlayer callbacks, key event handlers, and the Ktor playback control endpoints all write to the *same session object* instead of scattered state.

**Concurrency:** ExoPlayer callbacks, key event handlers, and Ktor control endpoints may fire from different threads/coroutines. The active session MUST be written through a serializing mechanism — either a `Mutex`-guarded builder or a `Channel` that funnels all writes through a single coroutine. Do NOT use a bare mutable object accessed from multiple threads. Recommended approach: a `PlaybackSessionBuilder` that accepts events through a `Channel<SessionEvent>` and a single coroutine that drains the channel and builds the session. On playback end, close the channel, finalize the builder, and persist the immutable `PlaybackSession`.

**What it replaces:** The current `play_events` table (which only stores videoId, duration, completedPercent) and whatever ad-hoc ExoPlayer state tracking exists.

**Persistence:** Store as Room entities. The `play_events` table is dropped and replaced by `playback_sessions` (plus related tables/columns per serialization strategy below). There are no existing users, so no data migration is needed — destructive migration is fine. Stats endpoints query only `playback_sessions`. Keep all sessions (retention policy below).

### 3. ParentAction

**What it represents:** Any action a parent takes through the phone dashboard.

**What it captures:**
```kotlin
data class ParentAction(
    val actionId: String,
    val type: ParentActionType,
    val at: Instant,
    val sessionToken: String?,          // which parent session (for multi-device debugging)
    val input: String?,                 // e.g., the playlist URL they submitted
    val outcome: ActionOutcome,
    val detail: String?,                // human-readable result or error
    val durationMs: Long                // how long the action took
)

enum class ParentActionType {
    AUTH_ATTEMPT,           // tried to enter PIN
    AUTH_SUCCESS,           // correct PIN
    PLAYLIST_ADD,           // submitted a playlist URL
    PLAYLIST_REMOVE,        // deleted a playlist
    PLAYBACK_STOP,          // stopped playback from phone
    PLAYBACK_SKIP,          // skipped video from phone
    REFRESH_TRIGGERED,      // asked for playlist refresh
    STATS_VIEWED            // opened stats page
}

enum class ActionOutcome {
    SUCCESS,
    FAILED,
    RATE_LIMITED
}
```

**Where the logic lives:** Each Ktor route handler creates a `ParentAction` as its first line, populates outcome as the action proceeds, and persists it at the end.

**What it replaces:** Ktor request logging (if any). Currently parent actions are fire-and-forget with no history.

**Persistence:** Room entity (`parent_actions` table). Keep last 100.

### 4. WatchableContent

**What it represents:** The current state of everything the kid can watch. This is a *computed view*, not a stored entity.

**What it provides:**
```kotlin
class WatchableContent(
    private val playlistDao: PlaylistDao,
    private val resolutionAttemptDao: ResolutionAttemptDao
) {
    fun summary(): WatchableSummary    // total playlists, total videos, stale playlists, failed playlists

    fun playlists(): List<PlaylistState>  // per-playlist: name, video count, freshness, last resolution outcome

    fun stalePlayists(): List<PlaylistState>  // playlists not resolved in >24h

    fun failedPlaylists(): List<PlaylistState>  // playlists whose last resolution failed

    fun videoCount(): Int

    fun healthCheck(): ContentHealth    // GOOD / STALE / DEGRADED / EMPTY — with reason
}

data class WatchableSummary(
    val playlistCount: Int,
    val videoCount: Int,
    val stalePlaylists: Int,            // last resolution >24h ago
    val failedPlaylists: Int,           // last resolution outcome != SUCCESS
    val oldestResolution: Instant?,
    val health: ContentHealth
)

enum class ContentHealth {
    GOOD,           // all playlists resolved recently with no errors
    STALE,          // some playlists haven't been refreshed in >24h
    DEGRADED,       // some playlists failed to resolve, serving cache
    EMPTY           // no playlists or no cached videos at all
}
```

**Where the logic lives:** This is a query object that composes data from playlists + resolution attempts. It does NOT duplicate data. It answers questions.

**What it replaces:** The current pattern of ViewModels querying DAOs directly and computing state inline.

---

## Implementation Decisions

### Serialization Strategy

Nested objects within Room entities follow these rules:

- **JSON columns** for lists that are recorded but never individually queried: `PlayerStateChange` lists, `KeyEventRecord` lists, `StreamSelection` (all within `PlaybackSession`). Use Kotlin serialization to a JSON `TEXT` column.
- **Separate table** for `PageResult` within `ResolutionAttempt` — individual page failures may need to be queried (e.g., "which pages failed across all recent attempts?"). Foreign key to `resolution_attempts`.
- **Inline columns** for small value objects like `ActionOutcome` and `ResolutionOutcome` — store as enum name strings, not ordinals (ordinals break when enums are reordered).

This must be applied consistently across all phases. Do not mix strategies within the same domain object.

### Retention Policy

The Mi Box has limited storage. Domain objects accumulate over time and must be bounded:

| Entity | Retention |
|--------|-----------|
| `resolution_attempts` | Last 3 per playlist (as specified) |
| `playback_sessions` | Last 200 sessions OR last 30 days, whichever is smaller |
| `parent_actions` | Last 100 (as specified) |

Cleanup runs on app startup and after each new session/attempt/action is persisted. Implement as a simple `DELETE WHERE` in the DAO, not a background worker.

### Error Handling for Domain Object Persistence

Domain object persistence failures MUST NOT block the primary operation they are recording. If persisting a `ResolutionAttempt` to Room fails, the resolution result (cached videos) is still returned normally. If creating or finalizing a `PlaybackSession` throws, playback continues.

Concretely: wrap all domain object persistence in a try/catch that logs the failure (Android `Log.w`) and continues. The domain objects exist to observe the system — they must never break it. This applies to all five phases.

---

## Refactoring Plan

### Phase 1: Introduce ResolutionAttempt (Highest Impact)

This is where Claude Code spends the most debugging time — playlist resolution failures.

**Steps:**
1. Create `ResolutionAttempt`, `PageResult`, `ResolutionOutcome` data classes
2. Create `ResolutionAttemptEntity` Room entity and DAO
3. Refactor playlist resolution logic: the function that calls NewPipe now builds and returns a `ResolutionAttempt`. Every branch (success, partial, error, fallback) populates the attempt.
4. Store completed attempts in Room
5. Add Ktor endpoint: `GET /debug/resolutions` — returns last 3 attempts per playlist
6. Add Ktor endpoint: `GET /debug/resolutions/:playlistId` — returns attempts for specific playlist
7. Update existing tests: resolution tests now assert on `ResolutionAttempt` fields, not just "did videos appear in cache"
8. Add new tests: verify attempt is recorded for success, network failure, parse error, partial failure, and cache fallback scenarios

**Migration:** The existing `cached_videos` table and resolution logic don't go away. `ResolutionAttempt` wraps and records the resolution process. Cached videos are still the outcome — the attempt is the history of how we got there.

**Test expectations:** All existing playlist resolution tests pass. New tests verify:
- Successful resolution produces attempt with `outcome = SUCCESS` and correct page counts
- Network failure produces attempt with `outcome = NETWORK_ERROR` and `cacheFallback = true/false`
- Each `PageResult` has accurate timing
- Attempts are persisted and queryable
- Only last 3 attempts per playlist are retained

### Phase 2: Introduce PlaybackSession

**Steps:**
1. Create `PlaybackSession`, `StreamSelection`, `StreamOption`, `PlayerStateChange`, `KeyEventRecord`, `PlaybackEndReason` data classes
2. Create Room entities (session + related records)
3. Refactor playback flow:
   - On video selection: create `PlaybackSession`, populate stream selection during NewPipe extraction
   - On ExoPlayer state change: append to session's `playerStates`
   - On key event: append to session's `keyEvents`
   - On playback end (any reason): finalize session, compute duration and completion, persist
4. Drop old `play_events` table, create `playback_sessions` (destructive migration — no existing users)
5. Update `GET /stats` and `GET /stats/recent` to query from `playback_sessions`
6. Add Ktor endpoint: `GET /debug/sessions` — returns last 10 sessions with full detail
7. Add Ktor endpoint: `GET /debug/sessions/:sessionId` — returns single session with all state changes and key events
8. Update existing tests
9. Add new tests for each `PlaybackEndReason`, stream selection logic, and state transition recording

**Test expectations:** All existing playback and stats tests pass (against new schema). New tests verify:
- Session records correct `endReason` for each scenario (completed, back, parent stop, error)
- Stream selection records all available streams and selection reason
- Player state changes are recorded in order with correct timestamps
- Key events are recorded with the action taken
- Stats endpoints return correct data from new schema

### Phase 3: Introduce ParentAction

**Steps:**
1. Create `ParentAction`, `ParentActionType`, `ActionOutcome` data classes
2. Create Room entity and DAO
3. Wrap each Ktor route handler: create `ParentAction` at entry, populate outcome, persist on exit
4. Add Ktor endpoint: `GET /debug/actions` — returns last 50 parent actions
5. Add tests: verify actions are recorded for auth (success + failure + rate limit), playlist add/remove, playback control, stats view

**Test expectations:** New tests verify:
- Every authenticated endpoint produces a `ParentAction`
- Failed auth produces action with `outcome = RATE_LIMITED` after threshold
- Action `durationMs` is populated
- Actions are queryable by type

### Phase 4: Introduce WatchableContent

**Steps:**
1. Create `WatchableContent` class and related data classes
2. Wire it to existing DAOs + `ResolutionAttemptDao`
3. Use `WatchableContent.healthCheck()` in the home screen ViewModel to determine what state to show (loading, empty, degraded, good)
4. Use `WatchableContent.summary()` in the `GET /status` endpoint to enrich the response
5. Add Ktor endpoint: `GET /debug/content` — returns full `WatchableSummary` plus per-playlist state
6. Add tests for health computation: GOOD when all resolved recently, STALE after 24h, DEGRADED when resolutions failed, EMPTY when no playlists

**Test expectations:** New tests verify:
- `healthCheck()` returns correct status for each scenario
- `stalePlayists()` correctly identifies playlists older than threshold
- `summary()` counts are accurate
- Home screen ViewModel uses `WatchableContent` instead of raw DAO queries

### Phase 5: Debug Endpoints as Inspection Surface

After phases 1-4, the following debug endpoints exist:

```
GET /debug/resolutions                — all recent resolution attempts
GET /debug/resolutions/:playlistId    — attempts for one playlist
GET /debug/sessions                   — recent playback sessions
GET /debug/sessions/:sessionId        — one session in full detail
GET /debug/actions                    — recent parent actions
GET /debug/content                    — watchable content health + per-playlist state
```

**Steps:**
1. All `/debug/*` routes require valid session (same auth as other routes)
2. Responses are JSON, human-readable (formatted timestamps, enum names not ordinals)
3. All list endpoints support optional `?limit=N` query parameter (default: 10 for sessions, 50 for actions, 3-per-playlist for resolutions). No cursor pagination needed at current scale.
4. Add a simple `/debug` index page (served HTML) that links to all debug endpoints with descriptions
5. Add corresponding ADB debug intents:
   - `DEBUG_DUMP_RESOLUTIONS` — logs last resolution attempt per playlist
   - `DEBUG_DUMP_SESSION` — logs current or most recent playback session
   - `DEBUG_DUMP_CONTENT_HEALTH` — logs WatchableContent summary
6. Update the existing `DEBUG_GET_STATE` intent (or equivalent) to include content health

**Test expectations:** All debug endpoints return valid JSON. ADB intents produce parseable output.

---

## What Changes for Tests

### Existing Tests

All 138 existing tests must pass after refactoring. The domain objects wrap existing behavior — they don't change it. If a test currently asserts "after adding a playlist and refreshing, the home screen shows 12 videos," that still works. The difference is that NOW the test can also assert "and the ResolutionAttempt shows outcome=SUCCESS with 1 page and 12 videos resolved in <10s."

### New Test Philosophy

Tests shift from asserting on *side effects* to asserting on *domain objects*:

**Before:**
```kotlin
// Test: playlist resolution handles network error
// Setup: simulate offline
// Assert: cached videos still present in Room DB
// Assert: UI shows "Offline" badge
```

**After:**
```kotlin
// Test: playlist resolution handles network error
// Setup: simulate offline, resolve playlist
// Assert: ResolutionAttempt.outcome == NETWORK_ERROR
// Assert: ResolutionAttempt.cacheFallback == true
// Assert: ResolutionAttempt.error contains "network" or "unreachable"
// Assert: ResolutionAttempt.durationMs > 0
// Assert: WatchableContent.healthCheck() == DEGRADED
// Assert: cached videos still present (existing assertion, still valid)
// Assert: UI shows "Offline" badge (existing assertion, still valid)
```

The domain object assertions are MORE informative when they fail. Instead of "expected cached videos to be present but they weren't," a failure says "expected ResolutionAttempt.outcome to be NETWORK_ERROR but was PARSE_ERROR" — which tells Claude Code exactly where the bug is.

### Test Count Expectation

Expect roughly 60-80 new tests across the 5 phases, in addition to the existing 138. Phase 2 (PlaybackSession) accounts for the bulk — 8 end reasons × stream selection scenarios × state transition sequences. The new tests cover:
- Domain object construction for each scenario
- Persistence and retrieval of domain objects
- Debug endpoint responses
- Health computation logic
- Migration from old schema

---

## What Does NOT Change

- **No new user-facing features.** The kid sees the same UI. The parent sees the same dashboard (with slightly richer `/status` data).
- **No new dependencies.** All domain objects are plain Kotlin data classes + Room entities.
- **No architecture change.** Still Ktor + Room + ExoPlayer + NewPipe. The server, database, and player are the same. The *organization of logic around them* changes.
- **No API contract changes.** Existing endpoints (`/playlists`, `/stats`, `/status`, `/auth`) keep their current response shapes. `/status` gets additional fields (additive, not breaking). New `/debug/*` endpoints are added.
- **No migration risk.** Room schema migration adds new tables and drops `play_events` (no existing users, so destructive migration is safe). Old tables that are still needed (playlists, cached_videos) are untouched.
- **Domain objects are designed for evolution.** When v0.3 needs to add fields (e.g., subtitle track in PlaybackSession), add them as nullable columns with Room migration. Never remove or rename existing columns — only add. This keeps forward migrations trivial.

---

## How to Verify This Release

### For Claude Code (automated)

1. All 138 existing tests pass
2. All new domain object tests pass
3. All new debug endpoint tests pass
4. CI pipeline (`ci-run.sh`) passes end-to-end

### For Prasanna (manual, on device)

1. Install on Mi Box. App works exactly as before — playlists load, videos play, dashboard works.
2. Open `/debug` in phone browser. See index page with links to all debug endpoints.
3. Play a video, then check `/debug/sessions` — see a complete PlaybackSession with stream selection, player states, and end reason.
4. Add a playlist, then check `/debug/actions` — see the ParentAction with type, outcome, and timing.
5. Check `/debug/content` — see WatchableContent health summary.
6. Simulate offline (via debug intent), try to refresh playlists, then check `/debug/resolutions` — see ResolutionAttempt with NETWORK_ERROR outcome and cache fallback flag.

### The Real Test

Next time something breaks during v0.3 development, check: can Prasanna paste a `/debug/*` JSON response into the Claude Code prompt and get a targeted fix — instead of describing symptoms in words? If yes, this release succeeded.

---

## Execution Order for Claude Code

```
Phase 1: ResolutionAttempt           — highest debugging value, most scattered logic today
Phase 2: PlaybackSession             — second highest, enables v0.3 playback work
Phase 3: ParentAction                — straightforward, wraps Ktor handlers
Phase 4: WatchableContent            — computed view, depends on Phase 1
Phase 5: Debug endpoints + intents   — inspection surface, depends on Phases 1-4
```

Each phase is independently deployable and testable. If any phase is blocked, the others can proceed (except Phase 4 depends on Phase 1, and Phase 5 depends on all).

---

## Reference: Moldable Development Principles Applied

| Principle | How It Appears in This Release |
|-----------|-------------------------------|
| The system is the source of understanding | Domain objects capture what happened, not just what exists now |
| Every object can explain itself | ResolutionAttempt tells you why resolution failed; PlaybackSession tells you why playback ended |
| Custom views for custom problems | `/debug/resolutions/:playlistId` is a view shaped exactly like "why didn't this playlist load?" |
| No framework-shaped reasoning | Claude Code reads `ResolutionAttempt.kt` to understand resolution, not 5 scattered files |
| Tests assert on domain truth | "outcome == NETWORK_ERROR" not "cache row count > 0" |
| Inspection is a consequence of structure | Debug endpoints serialize domain objects that already exist — no special inspection code |
