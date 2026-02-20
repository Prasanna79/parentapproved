# ParentApproved.tv Architecture Review

**Date:** 2026-02-20
**Reviewer:** Senior Software Architect (Claude Opus 4.6)
**Version reviewed:** v0.7.1 (commit 547db0a)
**Scope:** Full codebase review for public release readiness

---

## Executive Summary

ParentApproved.tv is a well-architected, local-first Android TV application with a thoughtfully designed relay system for remote access. The codebase demonstrates disciplined engineering: clear component boundaries, comprehensive test coverage (371 tests across 5 suites), and deliberate security controls. The ServiceLocator-based DI is lightweight and appropriate for the project's scale.

The architecture's greatest strength is its simplicity: an embedded Ktor server bridges the TV and phone, WebSocket relay adds remote access, and the dashboard is plain HTML/JS served from assets. There are no unnecessary abstractions.

The most significant findings are:
- **CRITICAL**: Double session creation on PIN auth (tokens leaked)
- **HIGH**: `runBlocking` on UI thread path for time limit checks
- **HIGH**: Relay allowlist missing time-limit routes (remote parents cannot manage time controls)
- **HIGH**: Thread safety gaps in shared mutable state (`SessionManager`, `PlayEventRecorder`)

Overall assessment: **Ready for limited release with targeted fixes.** The critical/high items should be addressed before wider distribution.

---

## Table of Contents

1. [Component Boundaries & Coupling](#1-component-boundaries--coupling)
2. [Dependency Graph](#2-dependency-graph)
3. [DI Pattern (ServiceLocator)](#3-di-pattern-servicelocator)
4. [Data Flow](#4-data-flow)
5. [State Management](#5-state-management)
6. [API Design](#6-api-design)
7. [Error Handling](#7-error-handling)
8. [Code Organization](#8-code-organization)
9. [Scalability Concerns](#9-scalability-concerns)
10. [Tech Debt](#10-tech-debt)
11. [Dashboard Architecture](#11-dashboard-architecture)
12. [Relay Architecture](#12-relay-architecture)
13. [Build System](#13-build-system)

---

## 1. Component Boundaries & Coupling

### Strengths

- **Clean layer separation**: Server routes (`server/`), auth (`auth/`), data (`data/`), relay (`relay/`), playback (`playback/`), and UI (`ui/`) are well-separated packages with minimal cross-cutting.
- **Interface-driven abstractions**: `SessionPersistence`, `PinLockoutPersistence`, `TimeLimitStore`, `WatchTimeProvider`, and `WebSocketFactory` all enable clean testing without mocking frameworks.
- **Relay is transparent**: The TV's Ktor routes have zero knowledge of the relay. The `RelayConnector` bridges requests to `localhost:8080`, maintaining a clean boundary.

### Findings

**[MEDIUM] StatusRoutes directly accesses ServiceLocator and PlayEventRecorder singletons**
File: `tv-app/app/src/main/java/tv/parentapproved/app/server/StatusRoutes.kt`, lines 34-37, 39-48

Unlike other route files that receive dependencies as parameters, `statusRoutes()` takes no parameters and reaches directly into `ServiceLocator.database`, `ServiceLocator.sessionManager`, and `PlayEventRecorder`. This breaks the dependency injection pattern used by every other route file.

```kotlin
fun Route.statusRoutes() {
    get("/status") {
        val playlistCount = try {
            ServiceLocator.database.channelDao().count()  // Direct singleton access
        } catch (e: Exception) { 0 }
        val activeSessions = ServiceLocator.sessionManager.getActiveSessionCount()
        // ... PlayEventRecorder direct access ...
    }
}
```

Recommendation: Pass `sessionManager`, `database`, and a `NowPlayingProvider` interface as parameters, matching the pattern in `playlistRoutes()`, `authRoutes()`, etc.

**[LOW] ContentSourceRepository is an `object` (singleton) with hardcoded `Dispatchers.IO`**
File: `tv-app/app/src/main/java/tv/parentapproved/app/data/ContentSourceRepository.kt`, lines 30, 35

The repository uses `withContext(Dispatchers.IO)` directly rather than accepting a dispatcher, making it harder to test coroutine behavior. However, since the repository's tests focus on data transformation rather than concurrency, this is a minor concern.

**[NOTE] PlaybackCommandBus coupling is appropriate**
The `PlaybackCommandBus` singleton (`object`) is used as a cross-component event bus between HTTP routes, D-pad handler, and ExoPlayer. For a single-activity app with one playback surface, a `SharedFlow`-based bus is the right level of abstraction.

---

## 2. Dependency Graph

### Overview

```
ParentApprovedApp (Application)
  -> ServiceLocator.init()
    -> CacheDatabase (Room)
    -> SessionManager -> SharedPrefsSessionPersistence
    -> RelayConfig -> SharedPreferences
    -> RelayConnector -> RelayConfig, OkHttpClient
    -> PinManager -> SessionManager (via callback)
    -> TimeLimitManager -> RoomTimeLimitStore, RoomWatchTimeProvider
    -> PlayEventRecorder.init(db)

MainActivity
  -> ParentApprovedServer -> ServiceLocator (routes)
  -> AppNavigation -> Screens -> ServiceLocator, PlaybackCommandBus

Relay (Cloudflare Workers)
  -> Worker entry (index.ts) -> static assets, rate limiter, allowlist
  -> RelayDurableObject -> protocol, WebSocket lifecycle
```

### Findings

**[NOTE] No circular dependencies detected**
The dependency graph is a clean DAG. `ServiceLocator` is the single root, and downstream components do not reference back to it (except `StatusRoutes`, noted above, and `DebugReceiver` which is appropriately a debugging tool).

**[MEDIUM] PinManager has an implicit coupling to SessionManager via callback**
File: `tv-app/app/src/main/java/tv/parentapproved/app/ServiceLocator.kt`, line 52

```kotlin
pinManager = PinManager(
    onPinValidated = { sessionManager.createSession() ?: "" },
    ...
)
```

This creates a session inside `PinManager.validate()`. But `AuthRoutes` (line 57-58) creates **another** session:

```kotlin
is PinResult.Success -> {
    val token = sessionManager.createSession()  // Second session!
    call.respond(HttpStatusCode.OK, AuthResponse(success = true, token = token))
}
```

This means every successful PIN auth creates **two sessions**: one via the callback (returned inside `PinResult.Success.token`) and one explicitly in the route handler. The callback-created session is never used or returned to the client -- it's leaked.

**See Critical finding in Section 6 for full analysis.**

**[LOW] NewPipe dependency is a risk**
The app depends on `com.github.teamnewpipe:NewPipeExtractor:v0.25.2` for all YouTube extraction. This is an unofficial library that can break when YouTube changes its frontend. This is a known, accepted trade-off (no API key, no sign-in), but it means the app can break without any code changes. Consider documenting a contingency plan.

---

## 3. DI Pattern (ServiceLocator)

### Assessment: Appropriate for the project's scale

File: `tv-app/app/src/main/java/tv/parentapproved/app/ServiceLocator.kt`

The `ServiceLocator` object pattern is a deliberate choice (documented in CLAUDE.md). For a single-module, single-activity Android TV app with ~55 source files, this is the right call.

### Strengths

- `initForTest()` method allows test-time replacement of all major dependencies
- `lateinit var` properties fail fast if accessed before initialization
- `isInitialized()` guard prevents double-init
- Relay enable/disable logic is cleanly centralized

### Findings

**[MEDIUM] No thread safety on initialization**
File: `tv-app/app/src/main/java/tv/parentapproved/app/ServiceLocator.kt`, lines 25-68

The `initialized` flag is not `@Volatile` and the `init()` method is not synchronized. While `Application.onCreate()` runs on the main thread and double-init is unlikely, the `initForTest()` path in tests could race.

```kotlin
private var initialized = false  // Not @Volatile

fun init(context: Context) {
    if (initialized) return  // Not thread-safe check
    // ... initialization ...
    initialized = true
}
```

**[LOW] `initForTest()` does not initialize `relayConfig` or `relayConnector`**
File: `tv-app/app/src/main/java/tv/parentapproved/app/ServiceLocator.kt`, lines 85-111

Test code that accesses `ServiceLocator.relayConfig` or `ServiceLocator.relayConnector` after `initForTest()` will get `UninitializedPropertyAccessException`.

---

## 4. Data Flow

### TV -> Phone (Local Network)

```
Phone browser -> HTTP GET/POST -> Ktor server (port 8080) -> Route handler
  -> Room DB / PlaybackCommandBus / PlayEventRecorder
  -> JSON response -> Phone browser
```

### TV -> Relay -> Phone (Remote)

```
TV: RelayConnector -> WebSocket -> Cloudflare DO (RelayDurableObject)
Phone: HTTPS -> Worker -> allowlist check -> rate limit -> DO.fetch()
DO: serialize RelayRequest -> WebSocket -> TV
TV: RelayConnector.bridgeToLocal() -> HTTP to localhost:8080 -> Ktor route
TV: RelayResponse -> WebSocket -> DO -> HTTP response -> Phone
```

### Findings

**[HIGH] Relay allowlist is missing time-limit routes**
File: `relay/src/allowlist.ts`, lines 11-21

The allowlist does not include any `/api/time-limits` paths:

```typescript
const ALLOWED_ROUTES: AllowlistEntry[] = [
  { pattern: /^\/api\/auth$/, methods: ["POST"] },
  { pattern: /^\/api\/auth\/refresh$/, methods: ["POST"] },
  { pattern: /^\/api\/playlists$/, methods: ["GET", "POST"] },
  { pattern: /^\/api\/playlists\/[^\/]+$/, methods: ["GET", "DELETE"] },
  { pattern: /^\/api\/playback\/[^\/]+$/, methods: ["POST"] },
  { pattern: /^\/api\/playback$/, methods: ["GET"] },
  { pattern: /^\/api\/stats$/, methods: ["GET"] },
  { pattern: /^\/api\/stats\/recent$/, methods: ["GET"] },
  { pattern: /^\/api\/status$/, methods: ["GET"] },
];
```

Missing routes:
- `GET /api/time-limits`
- `PUT /api/time-limits`
- `POST /api/time-limits/lock`
- `POST /api/time-limits/bonus`
- `POST /api/time-limits/request`

This means **parents cannot manage time limits when using the relay (remote access)**. This is a significant feature gap since the relay's whole purpose is remote access.

**[MEDIUM] Dashboard `app.js` does not know if it's running via relay or local**
File: `tv-app/app/src/main/assets/app.js`, line 5

```javascript
const API_BASE = '';
```

The dashboard uses relative URLs, which works for local access. But the relay copy of the dashboard must handle the `/tv/{tvId}` prefix. Verify that the relay-served dashboard's API calls include the correct `/tv/{tvId}/api/` prefix.

**[LOW] Bridge request is synchronous blocking on the Ktor thread**
File: `tv-app/app/src/main/java/tv/parentapproved/app/relay/RelayConnector.kt`, line 168

The `bridgeToLocal()` method uses OkHttp's synchronous `.execute()` within a coroutine. While wrapped in `withContext(dispatcher)`, the `localHttpClient` has a 10-second timeout. For a single-TV scenario, this is acceptable.

---

## 5. State Management

### Compose State

The Compose state management follows standard patterns:
- `HomeViewModel` uses `StateFlow<HomeUiState>` (correct for state holders)
- `PlaybackScreen` uses local `remember` state (correct for ephemeral screen state)
- `AppLogger.lines` uses `mutableStateListOf` for reactive log display

### Findings

**[HIGH] SessionManager is not thread-safe**
File: `tv-app/app/src/main/java/tv/parentapproved/app/auth/SessionManager.kt`

`SessionManager` uses a plain `HashMap<String, Long>` accessed from:
1. Ktor route handlers (Netty thread pool)
2. `validateSession()` called on every authenticated request
3. `refreshSession()` which removes and adds entries

```kotlin
private val sessions = HashMap<String, Long>() // Not thread-safe!
```

Concurrent requests could cause `ConcurrentModificationException` or corrupted state.

Recommendation: Replace with `ConcurrentHashMap` or add `@Synchronized` to all methods.

**[HIGH] PlayEventRecorder is a singleton with mutable state accessed from multiple threads**
File: `tv-app/app/src/main/java/tv/parentapproved/app/data/events/PlayEventRecorder.kt`

`PlayEventRecorder` is an `object` with mutable properties (`currentVideoId`, `isPlaying`, `pausedElapsedMs`, etc.) read from Ktor server thread, UI thread, and IO coroutine scope. None of these properties are `@Volatile` or synchronized.

**[HIGH] `runBlocking` in RoomTimeLimitStore called from UI path**
File: `tv-app/app/src/main/java/tv/parentapproved/app/timelimits/RoomTimeLimitStore.kt`, lines 17-21

```kotlin
override fun getConfig(): TimeLimitConfig? = runBlocking {
    dao.getConfig()?.toTimeLimitConfig()
}
```

Called from HomeScreen, PlaybackScreen, and LockScreen LaunchedEffects. `runBlocking` inside a coroutine blocks the coroutine's thread. Can cause ANR if Room query hits disk I/O.

Recommendation: Make `TimeLimitStore` a suspend interface, or use `withContext(Dispatchers.IO)` at the call sites.

**[MEDIUM] TimeLimitRoutes uses closure-captured mutable state for `hasTimeRequest`**
File: `tv-app/app/src/main/java/tv/parentapproved/app/server/TimeLimitRoutes.kt`, lines 54-55

```kotlin
fun Route.timeLimitRoutes(...) {
    var hasTimeRequest = false
    var lastRequestTime = 0L
```

These `var` captures are shared across all Ktor request-handling threads, not volatile or synchronized.

---

## 6. API Design

### REST API Surface

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| POST | /auth | No | PIN authentication |
| POST | /auth/refresh | Token | Refresh session token |
| GET | /playlists | Token | List content sources |
| POST | /playlists | Token | Add content source |
| DELETE | /playlists/{id} | Token | Remove content source |
| POST | /playback/stop | Token | Stop playback |
| POST | /playback/skip | Token | Skip to next |
| POST | /playback/pause | Token | Toggle pause |
| GET | /stats | Token | Today's watch stats |
| GET | /stats/recent | Token | Recent play events |
| GET | /status | No* | Server status + now playing |
| GET | /time-limits | Token | Time limit config + status |
| PUT | /time-limits | Token | Update time limits |
| POST | /time-limits/lock | Token | Lock/unlock TV |
| POST | /time-limits/bonus | Token | Grant bonus time |
| POST | /time-limits/request | No | Kid requests more time |

### Findings

**[CRITICAL] Double session creation on PIN authentication**
Files:
- `tv-app/app/src/main/java/tv/parentapproved/app/ServiceLocator.kt`, line 52
- `tv-app/app/src/main/java/tv/parentapproved/app/server/AuthRoutes.kt`, lines 57-58
- `tv-app/app/src/main/java/tv/parentapproved/app/auth/PinManager.kt`, line 57

When a PIN is validated:
1. `PinManager.validate()` calls `onPinValidated(pin)` (line 57)
2. `onPinValidated` is wired to `{ sessionManager.createSession() ?: "" }` (ServiceLocator line 52)
3. This creates **Session A** and returns it inside `PinResult.Success.token`
4. Back in `AuthRoutes`, the route handler calls `sessionManager.createSession()` **again** (line 58)
5. This creates **Session B** and returns it to the client

Session A is created but **never returned to anyone** -- it's a leaked session that counts against the 20-session limit and persists for 90 days in `SharedPrefsSessionPersistence`.

Impact:
- Every PIN auth leaks one session slot (max 20 sessions, so after 20 logins, half the slots are wasted)
- The leaked tokens are saved to SharedPreferences, consuming storage
- Not a security vulnerability (leaked tokens are never exposed), but a resource leak

Fix: Either remove the `onPinValidated` callback and let `AuthRoutes` handle session creation, or use the token from `PinResult.Success.token` in `AuthRoutes` instead of creating a new one.

**[MEDIUM] No API versioning**
The API has no version prefix (`/v1/auth`). For a local-first app where the dashboard is always served from the same APK, this is less of a concern. But the relay serves its own copy of the dashboard, so a relay update with a dashboard change could break if the TV hasn't been updated yet.

**[MEDIUM] Inconsistent response formats**
- Success responses vary: `{"success": true}` (string "true" in some places), `{"status": "ok"}`, `{"success": true}` (boolean)
- Error responses vary: `{"error": "message"}`, `AuthResponse` with `success: false`

File: `tv-app/app/src/main/java/tv/parentapproved/app/server/TimeLimitRoutes.kt`, line 149:
```kotlin
call.respond(HttpStatusCode.OK, mapOf("success" to "true"))  // String "true", not boolean
```

**[LOW] DELETE /playlists/{id} loads all playlists to find one**
File: `tv-app/app/src/main/java/tv/parentapproved/app/server/PlaylistRoutes.kt`, line 100. Should use a `getById(id)` DAO method.

**[LOW] `/status` endpoint does not require authentication**
Exposes app version, playlist count, active session count, and currently playing video to anyone on the local network.

---

## 7. Error Handling

### Strengths

- Ktor `StatusPages` plugin catches unhandled exceptions and returns 500
- Content resolution has graceful fallback to cached data (`SourceResult.CachedFallback`)
- Relay has clean timeout handling (10s bridge timeout, 90s heartbeat timeout)
- PlaybackScreen handles extraction failures with auto-skip after 3 seconds

### Findings

**[MEDIUM] Swallowed exceptions in multiple locations**

1. `RelayProtocol.kt`, line 48-52: `parseRequest()` returns `null` on any exception
2. `ContentSourceRepository.kt`: Multiple `catch (_: Exception)` blocks
3. `LockScreen.kt`: HTTP request response code read and discarded
4. `StatusRoutes.kt`, line 35: `catch (e: Exception) { 0 }` silently returns 0

**[MEDIUM] DebugReceiver JSON output uses string interpolation**
JSON is constructed via string interpolation rather than a JSON builder in some handlers. If `e.message` contains `"`, JSON breaks. Some handlers correctly use `buildJsonObject`/`buildJsonArray` while others use string interpolation.

**[LOW] No crash reporting or analytics**
Consistent with the "no cloud dependencies" philosophy, but crashes (especially from NewPipe breakage) will go unnoticed. Consider writing crash logs to local storage.

---

## 8. Code Organization

### Package Structure Assessment: Well-organized

```
tv.parentapproved.app/
  auth/        - PinManager, SessionManager, persistence interfaces (5 files)
  data/        - ContentSourceRepository, Room entities, DAOs (9 files)
  debug/       - DebugReceiver (1 file)
  playback/    - StreamSelector, PlaybackCommandBus, DpadKeyHandler (3 files)
  relay/       - RelayConnector, RelayConfig, RelayProtocol (3 files)
  server/      - Ktor routes (7 files)
  timelimits/  - TimeLimitManager, config, stores (5 files)
  ui/          - screens, components, theme, navigation (11 files)
  util/        - Parsers, loggers, network utils (5 files)
```

~55 Kotlin source files total. Each package has a clear responsibility. No file exceeds 440 lines. Most files are under 200 lines.

### Findings

**[LOW] Naming inconsistency: "Playlist" vs "Channel" vs "Source"**
REST API uses `/playlists`, database entity is `ChannelEntity`, internal model uses `ChannelMeta`/`ContentSource`. Reflects the v0.5 evolution from playlist-only to multi-source support.

**[NOTE] No dead code detected**

**[NOTE] Dashboard sync requirement is documented**
Manual process that could be automated with a build step.

---

## 9. Scalability Concerns

### Context

ParentApproved is designed for a single household: one TV, one parent phone. The architecture makes this explicit.

### Findings

**[LOW] ContentSourceRepository resolves sources sequentially**
With a 20-source limit and each resolution taking 1-5 seconds, startup refresh could take 20-100 seconds. Intentional to avoid YouTube rate limiting.

**[LOW] In-memory session storage**
HashMap backed by SharedPreferences. With 20-session cap and 90-day TTL, adequate.

**[LOW] Relay rate limiter is per-isolate**
Acknowledged in code comments. Durable Object layer provides the real per-tvId security.

**[NOTE] 200-video-per-source cap is appropriate**

---

## 10. Tech Debt

### Findings

**[MEDIUM] `handleFullReset` in DebugReceiver has a bug**
File: `tv-app/app/src/main/java/tv/parentapproved/app/debug/DebugReceiver.kt`, line 388

```kotlin
ServiceLocator.database.videoDao().deleteByPlaylist("%") // won't match, need deleteAll
```

`deleteByPlaylist("%")` uses exact match, not LIKE. No videos are deleted during full reset.

**[MEDIUM] Version mismatch between build.gradle and release notes**
`build.gradle.kts` says `versionName = "0.7.0"` but latest commit says v0.7.1. App self-reports as v0.7.0.

**[LOW] `exportSchema = false` in CacheDatabase**
No JSON schema file to verify migrations against.

**[LOW] Ktor server lifecycle tied to Activity, not Application**
If Activity is destroyed and recreated, server stops and restarts. Consider moving to Application class.

**[LOW] `app.js` uses `localStorage` without a per-TV key**
Uses flat `kw_token` key instead of `kw_token_{tvId}`. Multiple TVs from same browser will collide.

---

## 11. Dashboard Architecture

### Overview

Single-page application: plain HTML, CSS, JavaScript (no framework, no build step). Lives in two places:
1. `tv-app/app/src/main/assets/` -- served by TV's Ktor server
2. `relay/assets/` -- served by Cloudflare Workers

### Strengths

- **Zero build step**: Refreshingly simple
- **Progressive enhancement**: No framework means no blank screen on JS error
- **Auto-PIN from QR code**: Reduces friction

### Findings

**[MEDIUM] No session token refresh in local dashboard**
Dashboard does not call `/auth/refresh`. CLAUDE.md says it does, but the actual code does not implement this.

**[MEDIUM] Dashboard does not handle relay path prefix**
`API_BASE = ''` — when served from relay, API calls need `/tv/{tvId}/api/` prefix.

**[LOW] `escapeHtml` uses DOM-based escaping** — correct and safe

**[LOW] Polling-based updates** — appropriate for local-first app

---

## 12. Relay Architecture

### Strengths

- **Application-aware gateway**: Path allowlist, method allowlist, payload limits, response validation
- **12 security controls** documented and implemented
- **Durable Object hibernation**: WebSockets survive hibernation, reducing costs
- **Secret rotation**: Clean handling of reconnection after rotation
- **Protocol contract tests**: Both TypeScript and Kotlin parse same JSON fixtures

### Findings

**[HIGH] Relay allowlist missing time-limit routes** (duplicate of Section 4)

**[MEDIUM] Durable Object reconnection on hibernation wake**
On wake, DO sets `authenticated = true` and restores WebSocket. If TV has disconnected, DO won't know until heartbeat alarm fires. Small window of stale state.

**[LOW] Relay response body is read twice** — buffered in memory, fine with 100KB cap

**[NOTE] Staging environment is well-configured**

---

## 13. Build System

### Findings

**[MEDIUM] Dependency versions are aging**

| Dependency | Current | Latest (approx.) |
|-----------|---------|-------------------|
| Ktor | 2.3.7 | 3.x |
| Room | 2.6.1 | 2.7.x |
| kotlinx-serialization-json | 1.6.2 | 1.7.x |
| OkHttp | 4.12.0 | 4.12.x (latest 4.x) |
| Coil | 2.7.0 | 3.x |
| coroutines-test | 1.7.3 | 1.9.x |

**[MEDIUM] Release build has `isMinifyEnabled = false`**
For a public release, R8/ProGuard should be enabled. NewPipeExtractor and Ktor will need ProGuard rules.

**[LOW] No version catalog (libs.versions.toml)** — low priority for single-module project

**[LOW] `testOptions.unitTests.isReturnDefaultValues = true`** — necessary for Log.d() in tests, `AppLogger` wrapper contains it

---

## Prioritized Recommendations

### Must Fix Before Public Release

1. **[CRITICAL] Fix double session creation in PIN auth** (Section 6)
   - Remove the `onPinValidated` callback or use its returned token in `AuthRoutes`
   - Estimated effort: 30 minutes

2. **[HIGH] Add time-limit routes to relay allowlist** (Section 4, 12)
   - Add 5 entries to `ALLOWED_ROUTES` in `relay/src/allowlist.ts`
   - Add `PUT` to valid methods
   - Estimated effort: 1 hour

3. **[HIGH] Fix `SessionManager` thread safety** (Section 5)
   - Replace `HashMap` with `ConcurrentHashMap`
   - Estimated effort: 15 minutes

### Should Fix Before Public Release

4. **[HIGH] Address `runBlocking` in RoomTimeLimitStore** (Section 5)
   - Make `TimeLimitStore` methods suspend, or wrap calls in `withContext(Dispatchers.IO)`
   - Estimated effort: 2 hours

5. **[HIGH] Make `PlayEventRecorder` thread-safe** (Section 5)
   - Add `@Volatile` to mutable properties, or synchronize access
   - Estimated effort: 1 hour

6. **[MEDIUM] Fix `handleFullReset` video deletion bug** (Section 10)
   - Add `deleteAll()` to `PlaylistCacheDao` and use it
   - Estimated effort: 15 minutes

7. **[MEDIUM] Fix version mismatch** (Section 10)
   - Bump `versionName` in `build.gradle.kts`
   - Estimated effort: 5 minutes

### Should Fix Before Wider Distribution

8. **[MEDIUM] Verify relay dashboard API calls work** (Section 11)
   - Estimated effort: 2 hours

9. **[MEDIUM] Implement session token refresh in dashboard** (Section 11)
   - Estimated effort: 1 hour

10. **[MEDIUM] Enable ProGuard for release builds** (Section 13)
    - Estimated effort: 4 hours

11. **[MEDIUM] Fix `StatusRoutes` to accept dependencies as parameters** (Section 1)
    - Estimated effort: 30 minutes

### Nice to Have

12. Make `TimeLimitRoutes` state thread-safe
13. Standardize API response formats
14. Add API versioning prefix
15. Enable Room schema export
16. Move Ktor server lifecycle to Application class
17. Implement per-TV localStorage keys in dashboard
18. Update aging dependencies

---

## Architecture Strengths (What to Preserve)

1. **Local-first design**: No cloud dependencies, no analytics, no tracking
2. **Interface-driven testing**: Every major component has an interface or injection point for testing. 371 tests across 5 suites.
3. **Security-conscious relay**: 12 documented security controls
4. **Simple dashboard**: Plain HTML/CSS/JS with no build step
5. **Moldable development**: Friction log and debug intents show disciplined observability
6. **Graceful degradation**: Cached video fallback, auto-skip on errors, offline simulation

---

*Review completed 2026-02-20. 55 Kotlin source files, 5 TypeScript source files, 3 JavaScript/HTML assets, and build configuration reviewed.*
