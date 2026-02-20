# ParentApproved.tv Test Coverage Gap Analysis

**Reviewer:** Senior Test Manager (Claude Opus 4.6)
**Date:** 2026-02-20
**Version:** v0.7.1
**Total Tests:** 371 (194 TV unit + 19 instrumented + 139 relay + 10 landing page + 9 digest worker)

---

## Executive Summary

ParentApproved has strong test coverage for its **core business logic** (auth, time limits, relay protocol, rate limiting) and **Ktor route handlers**. The codebase follows TDD practices with injectable clocks, fake DAOs, and a clean ServiceLocator pattern that enables testing without Android.

However, there are significant gaps that pose release risk:

| Area | Health | Risk Level |
|------|--------|------------|
| Auth (PIN, sessions, middleware) | Excellent | Low |
| Time limits (manager, routes, DAO) | Excellent | Low |
| Relay protocol & contract | Excellent | Low |
| Ktor routes (playlist, playback, stats, status) | Good | Low |
| Relay security (allowlist, rate limiting) | Good | Low-Medium |
| Relay bridge integration | Good | Medium |
| Dashboard JavaScript (`app.js`) | **Poor** | **High** |
| Relay Durable Object (`relay.ts`) | **Poor** | **High** |
| Relay Worker entry point (`index.ts`) | **Not tested** | **High** |
| UI Compose screens | **Not tested** | Medium |
| ContentSourceRepository (video extraction) | **Not tested** | Medium |
| ServiceLocator lifecycle | **Minimal** | Medium |
| CI/CD automation | **None** | Medium |

**Overall assessment:** The TV app's server-side logic is well-tested. The two highest-risk gaps are (1) the dashboard JavaScript has zero automated DOM/integration tests and (2) the relay Durable Object has no tests exercising its actual WebSocket lifecycle, alarm handling, or request bridging. These are the primary phone-to-TV communication paths and need coverage before public release.

---

## 1. Existing Test Inventory

### 1.1 TV App Unit Tests (28 test files, 194 tests)

| File | Tests | Type | What It Tests |
|------|-------|------|---------------|
| `PinManagerTest.kt` | 13 | Unit | PIN generation, validation, rate limiting, exponential backoff, lockout persistence |
| `SessionManagerTest.kt` | 19 | Unit | Token creation, validation, expiry (90-day), refresh, persistence, max sessions |
| `AuthRoutesTest.kt` | 5 | Integration | POST /auth with correct/wrong/missing PIN, rate limiting, lockout expiry |
| `AuthIntegrationTest.kt` | 8 | Integration | Full auth flow: PIN -> token -> refresh -> use, multi-session, expiry |
| `AuthMiddlewareTest.kt` | 5 | Integration | Protected/public routes, expired/invalid/missing tokens |
| `AuthRefreshRoutesTest.kt` | 6 | Integration | POST /auth/refresh: valid/invalid/missing/expired token, preserves other sessions |
| `PlaylistRoutesTest.kt` | 13 | Integration | GET/POST/DELETE /playlists: CRUD, URL validation, duplicates, max limit, bare IDs |
| `PlaybackRoutesTest.kt` | 7 | Integration | POST /playback/stop,skip,pause: auth, command emission |
| `StatsRoutesTest.kt` | 3 | Integration | GET /stats, /stats/recent: empty, with events, pagination |
| `StatusRoutesTest.kt` | 7 | Integration | GET /status: shape, now-playing, title update race, elapsed/duration, playing state |
| `TimeLimitRoutesTest.kt` | 21 | Integration | GET/PUT /time-limits, POST lock/bonus/request: all CRUD, auth, validation, edge cases |
| `PlaybackCommandBusTest.kt` | 5 | Unit | SharedFlow: emit/collect Stop/Skip/Pause, no-collector fire-and-forget, ordering |
| `StreamSelectorTest.kt` | 7 | Unit | Stream selection: prefer 1080p/720p progressive, adaptive merge, cap at 1080p, audio bitrate |
| `DpadKeyHandlerTest.kt` | 7 | Unit | D-pad key mapping: center=pause, back=stop, left/right=null, media keys |
| `RelayConnectorTest.kt` | 18 | Unit | Connect/disconnect state, WS URL, backoff doubling/cap/reset, message parsing, path mapping |
| `RelayProtocolTest.kt` | 9 | Unit | Serialize/parse: ConnectMessage, HeartbeatMessage, RelayResponse, RelayRequest |
| `ProtocolContractTest.kt` | 6 | Contract | Shared JSON fixtures match Kotlin serialization (cross-language contract) |
| `RelayConfigTest.kt` | 8 | Unit | TV ID/secret generation, persistence, rotation, relay URL default/override |
| `RelayToggleTest.kt` | 9 | Unit | Enable/disable toggle, connect/disconnect states, PIN reset rotates secret |
| `RelayBridgeIntegrationTest.kt` | 5 | Integration | Full bridge: FakeWebSocket -> RelayConnector -> real Ktor server (status, auth, playlists, playback, refresh) |
| `PlayEventRecorderTest.kt` | 11 | Unit | Start/pause/resume/end events, title update, elapsed time calculation |
| `PlayEventDaoExtendedTest.kt` | 3 | Unit | Fake DAO: recent events, today filter, watch time sum |
| `ChannelDaoTest.kt` | 10 | Unit | Fake DAO: CRUD, count, duplicates, display name/video count/meta updates |
| `ContentSourceParserTest.kt` | 8 | Unit | 73 URL fixtures: playlists, videos, channels, Vimeo rejection, private IPs, edge cases |
| `QrCodeGeneratorTest.kt` | 3 | Unit | URL builder: format, port, default port |
| `TimeLimitManagerTest.kt` | 33 | Unit | Daily limits, bedtime (midnight span), manual lock, bonus time, priority, midnight rollover |
| `TimeLimitDaoTest.kt` | 7 | Unit | Fake DAO: config CRUD, manual lock, bonus accumulation |
| `UrlFixtures.kt` | - | Fixtures | 73 URL test cases shared by ContentSourceParserTest |

**Assessment:** Strong assertion quality. Tests verify specific status codes, JSON response shapes, state transitions, and edge cases. Injectable clocks and fake DAOs are used consistently.

### 1.2 TV App Instrumented Tests (3 test files, 19 tests)

| File | Tests | Type | What It Tests |
|------|-------|------|---------------|
| `DashboardAssetTest.kt` | 3 | Asset verification | index.html exists, contains playlist form; app.js contains /auth |
| `DebugReceiverIntentTest.kt` | 12 | Integration | 12 debug intents: PIN, auth, playlists, playback, offline, server status, play events |
| `FullFlowTest.kt` | 4 | E2E | Auth -> add source -> list -> delete; multi-source; full reset; offline toggle |

**Assessment:** Good coverage of debug intents and basic E2E flows. Missing: real Ktor server E2E (HTTP requests), actual video playback, time limits through intents.

### 1.3 Relay Tests (7 test files, 139 tests)

| File | Tests | Type | What It Tests |
|------|-------|------|---------------|
| `allowlist.test.ts` | 18 | Unit | Path/method allowlist: allowed paths, blocked paths, blocked methods, case insensitivity |
| `ratelimit.test.ts` | 8 | Unit | Token bucket: within/over limit, refill, isolation, TV/phone/refresh limits, cap |
| `protocol.test.ts` | 14 | Unit | Parse/serialize: ConnectMessage, HeartbeatMessage, RelayResponse, RelayRequest |
| `protocol-contract.test.ts` | 11 | Contract | Shared JSON fixtures match TypeScript parsing; path convention (relay /api/* -> TV bare paths) |
| `dashboard.test.ts` | 19 | Unit | URL parsing (tvId, apiBase, pin extraction), localStorage keys, offline/refresh logic, version check |
| `integration.test.ts` | 21 | Integration | Auth/playlist/status/playback flows through allowlist + rate limiter; blocked paths |
| `security.test.ts` | 19 | Unit | Path traversal, proxy blocking, CONNECT blocking, debug routes, method restrictions, payload sizes, secret rotation |

**Assessment:** Excellent coverage of allowlist, rate limiting, and protocol. However, `dashboard.test.ts` tests are **logic-only** -- they re-implement parsing logic inline rather than importing from `app.js`. The relay Durable Object (`relay.ts`) has **zero tests**.

### 1.4 Marketing Tests (2 test files, 19 tests)

| File | Tests | Type | What It Tests |
|------|-------|------|---------------|
| `landing-page/tests/notify.test.js` | 10 | Unit | Email signup: validation, normalization, duplicates, daily index, CORS |
| `notify-digest/tests/digest.test.js` | 9 | Unit | Digest: no signups, send email, skip digested, multiple signups, send failure, HTTP trigger |

---

## 2. TV App Unit Test Gaps

### 2.1 Classes with No Tests

| Class | Location | Priority | Effort | Description |
|-------|----------|----------|--------|-------------|
| `ContentSourceRepository` | `data/ContentSourceRepository.kt` | P1 | L | Video extraction. `buildCanonicalUrl()` and `extractVideoId()` are pure functions that could easily have tests. |
| `HomeViewModel` | `ui/screens/HomeViewModel.kt` | P2 | M | ViewModel with state flow. Depends on ServiceLocator. |
| `ParentApprovedServer` | `server/ParentApprovedServer.kt` | P2 | S | Server lifecycle wrapper. |
| `DashboardRoutes` | `server/DashboardRoutes.kt` | P2 | S | Static asset serving. |
| `RoomTimeLimitStore` | `timelimits/RoomTimeLimitStore.kt` | P2 | S | Entity-to-config mapping (14 fields) could drift. |
| `RoomWatchTimeProvider` | `timelimits/RoomWatchTimeProvider.kt` | P2 | S | Simple delegation. |
| All UI Screens | `ui/screens/*.kt` | P2 | L | Would need Compose test harness. |
| `AppNavigation` | `ui/navigation/AppNavigation.kt` | P2 | M | Navigation graph. |

### 2.2 Specific Missing Tests for Existing Tested Classes

**P0 - Must have for release:**

1. **`ContentSourceRepository.buildCanonicalUrl()` -- add 5-6 unit tests**
   - `buildCanonicalUrl("yt_channel", "UCxxx")` -> `https://www.youtube.com/channel/UCxxx`
   - `buildCanonicalUrl("yt_channel", "@handle")` -> `https://www.youtube.com/@handle`
   - `buildCanonicalUrl("yt_playlist", "PLtest")` -> full URL
   - `buildCanonicalUrl("yt_video", "dQw4w9WgXcQ")` -> full URL
   - `buildCanonicalUrl("unknown", "id")` -> returns id unchanged
   - Effort: S

2. **`ContentSourceRepository.extractVideoId()` -- add 4 unit tests**
   - URL with `?v=xxx` param
   - URL with `&v=xxx` param
   - youtu.be short URL (path extraction)
   - URL with no video ID
   - Effort: S

**P1 - Should have:**

3. **`RoomTimeLimitStore` entity mapping roundtrip test** -- S
4. **`PlaybackRoutesTest` -- missing `/playback/pause` command emission test** -- S
5. **`StatsRoutesTest` -- missing unauthenticated access test** -- S

### 2.3 Error Path Gaps (P1)

- `PinManager.validate()` with non-6-digit strings (empty, 5 digits, special chars)
- `SessionManager.createSession()` at exactly 20 sessions
- `PlaylistRoutes` POST with malformed JSON body
- `TimeLimitRoutes` PUT with missing `dailyLimits` key

---

## 3. TV App Integration Test Gaps

### 3.1 Ktor Route Coverage Matrix

| Route | GET | POST | PUT | DELETE | Auth | Unauth |
|-------|-----|------|-----|--------|------|--------|
| `/auth` | - | Yes | - | - | Yes | Yes |
| `/auth/refresh` | - | Yes | - | - | Yes | Yes |
| `/playlists` | Yes | Yes | - | - | Yes | **No** |
| `/playlists/:id` | **No** | - | - | Yes | Yes | **No** |
| `/playback/stop` | - | Yes | - | - | Yes | Yes |
| `/playback/skip` | - | Yes | - | - | Yes | Yes |
| `/playback/pause` | - | Yes | - | - | Yes | **No** |
| `/stats` | Yes | - | - | - | Yes | **No** |
| `/stats/recent` | Yes | - | - | - | Yes | **No** |
| `/status` | Yes | - | - | - | N/A | - |
| `/time-limits` | Yes | - | Yes | - | Yes | Yes |
| `/time-limits/lock` | - | Yes | - | - | Yes | Yes |
| `/time-limits/bonus` | - | Yes | - | - | Yes | Yes |
| `/time-limits/request` | - | Yes | - | - | N/A | - |
| `/` (dashboard) | **No** | - | - | - | N/A | - |

**P1 - Missing route tests (add ~8-10 tests to existing files):**

1. GET /playlists unauthenticated -> 401
2. GET /playlists/:id -> 200 with source details
3. POST /playback/pause unauthenticated -> 401
4. GET /stats unauthenticated -> 401
5. GET /stats/recent unauthenticated -> 401
6. Dashboard routes (GET /, GET /assets/app.js)

### 3.2 Missing Integration Scenarios

**P0:**
- Auth flow through real Ktor server (not just `testApplication`)

**P1:**
- Time limit enforcement during playback (canPlay -> Stop command)
- Session persistence across ServiceLocator re-initialization

---

## 4. Relay Test Gaps

### 4.1 Durable Object (`relay.ts`) -- ZERO direct tests [P0]

This is the **highest-priority gap**. The `RelayDurableObject` handles:
- WebSocket upgrade and connection lifecycle
- ConnectMessage authentication with timing-safe secret comparison
- Heartbeat monitoring and alarm-based timeout
- Request bridging (HTTP -> WebSocket -> HTTP response with correlation IDs)
- Connection replacement (1 WS per tvId)
- Protocol version validation
- Frame size enforcement
- Pending request timeout (10s)

**P0 tests needed:**

| Test | Description | Effort |
|------|-------------|--------|
| handleConnect accepts valid secret | Verify ConnectMessage accepted | M |
| handleConnect rejects invalid secret | Wrong secret -> close 4001 | M |
| handleConnect accepts new secret after disconnect | Regression test | M |
| handleConnect replaces existing connection | Second connect closes first with 4002 | M |
| handleConnect rejects wrong protocol version | Close with 4004 | M |
| bridgeRequest returns 503 when TV offline | No connected TV -> 503 | S |
| bridgeRequest times out after 10s | TV doesn't respond -> 504 | M |
| bridgeRequest resolves on RelayResponse | Full cycle with correlation ID | M |
| heartbeat timeout disconnects TV | No heartbeat for 90s -> close 4003 | M |
| frame size enforcement | >100KB -> close 4005 | S |
| invalid message format | Unparseable -> close 4004 | S |
| cleanupConnection rejects pending requests | TV disconnects -> all rejected | M |

**Total effort: L** (requires Miniflare DO test setup)

### 4.2 Worker Entry Point (`index.ts`) -- ZERO direct tests [P0]

| Test | Description | Effort |
|------|-------------|--------|
| parsePath extracts tvId and rest | URL parsing | S |
| unknown path returns 404 | | S |
| static asset serving | index.html, app.js, etc. | M |
| WebSocket upgrade forwarded to DO | | M |
| IP connection limit (6th TV -> 429) | | S |
| API allowlist enforcement at Worker level | | S |
| phone rate limiting (61st -> 429) | | S |
| refresh rate limiting (6th -> 429) | | S |
| request body size (>10KB -> 413) | | S |
| response body size (>100KB -> 502) | | M |
| response content type validation | | S |

**Total effort: L**

### 4.3 Relay Allowlist Gap: Time Limit Routes Missing [P0 - CRITICAL]

The relay `allowlist.ts` does **not include time-limit routes**:
- `/api/time-limits` (GET, PUT) -- **NOT IN ALLOWLIST**
- `/api/time-limits/lock` (POST) -- **NOT IN ALLOWLIST**
- `/api/time-limits/bonus` (POST) -- **NOT IN ALLOWLIST**
- `/api/time-limits/request` (POST) -- **NOT IN ALLOWLIST**

**Time controls do not work via remote access.** This is a production defect, not just a test gap.

### 4.4 Other Relay Gaps (P1)

- Concurrent bridge requests (correlation ID isolation)
- WebSocket reconnection after alarm-based disconnect
- Binary WebSocket messages
- `shouldAcceptSecret` with empty string

---

## 5. Dashboard Test Gaps -- MAJOR GAP

### 5.1 Current State

The dashboard is a **480-line JavaScript file** (`app.js`) with zero DOM tests. The relay `dashboard.test.ts` only tests pure logic by **re-implementing it inline**.

### 5.2 Missing Tests

**P0 - Must have for release:**

| Test Suite | Tests Needed | Effort |
|------------|-------------|--------|
| Auth flow | PIN submission, token storage, auto-PIN from query param, logout on 401 | M |
| API error handling | Network failure shows error, 401 triggers logout, 429 handling | M |
| XSS prevention | `escapeHtml()` with `<script>`, `"`, `&` characters | S |
| Token refresh | Auto-refresh on page load, token replacement in localStorage | S |

**P1 - Should have:**

| Test Suite | Tests Needed | Effort |
|------------|-------------|--------|
| Playlist management | Add/delete with API mocking, error display, duplicate handling | M |
| Now-playing UI state | Playing state starts fast polling, idle state slows polling | M |
| Time limits UI | Status badge rendering, lock/unlock toggle, bonus time granting | M |
| Edit limits modal | Open/save/close, daily limit parsing, bedtime parsing | M |

**Recommended approach:** Use `vitest` with `jsdom` environment. Mock `fetch()` globally. Import `app.js` in a JSDOM context with minimal HTML fixture matching `index.html`'s DOM IDs.

**Total effort: L** (requires JSDOM test infrastructure setup + ~25-30 tests)

---

## 6. E2E Test Gaps

### 6.1 Missing Full System Flows

**P0:**

| Flow | Description | Current Coverage | Effort |
|------|-------------|-----------------|--------|
| Phone -> Relay -> TV -> Response | Full remote access cycle | TV-side only; relay DO untested | L |
| PIN setup -> QR scan -> Dashboard auth | First-time onboarding | Not tested | M |

**P1:**

| Flow | Description | Effort |
|------|-------------|--------|
| Content source add -> Video extraction -> Playback | L |
| Time limit enforcement -> Lock screen | M |
| Relay reconnection after disconnect | M |
| Session expiry and refresh | S |
| Multiple phones controlling same TV | M |

---

## 7. Security Test Gaps

**P0:**

| Test | Description | Effort |
|------|-------------|--------|
| XSS in playlist display name | `<script>alert(1)</script>` in name, verify escaping | S |
| XSS in video title via now-playing | HTML/JS in title, verify sanitization | S |
| Auth bypass via relay | Forged path bypasses allowlist | S |
| Session token not in URL | Verify tokens only in headers | S |

**P1:**

| Test | Description | Effort |
|------|-------------|--------|
| PIN brute force through relay | Rate limiting via relay path | M |
| Response header security | X-Frame-Options, CSP in dashboard responses | S |
| Debug intents in release builds | `IS_DEBUG` guard blocks all intents | S |
| Input validation fuzzing | Long URLs (>2000 chars), null bytes, unicode | M |

---

## 8. Edge Case & Error Scenario Gaps

### Network Failures (P1)
- TV loses WiFi during playback
- Relay WebSocket drops mid-request
- Ktor server crashes and restarts
- DNS resolution failure for relay URL

### Concurrent Requests (P1)
- Two phones send conflicting playback commands
- Phone adds playlist while TV is resolving
- Two phones delete the same playlist

### Invalid/Malformed Data (P1)
- Video with 0-second duration
- Playlist with deleted/private videos
- Very long playlist title (>500 chars)
- Non-UTF8 characters in video titles
- Empty response body from TV

### Device-Specific (P2)
- API 28 (Mi Box) vs API 34 (emulator)
- Low memory conditions
- TV goes to sleep/screensaver

---

## 9. Missing Test Infrastructure

### 9.1 Dashboard Test Infrastructure [P0]

**Needed:**
- `vitest` config with `jsdom` environment
- HTML fixture matching `index.html` DOM structure
- Global `fetch` mock
- `localStorage` mock
- Setup effort: S-M

### 9.2 Relay Durable Object Test Infrastructure [P0]

**Needed:**
- Miniflare environment for DO instantiation
- WebSocket pair creation in tests
- Alarm scheduling mocks
- `DurableObjectState` mock
- Setup effort: M

### 9.3 CI/CD Test Automation [P1]

**Needed:**
- GitHub Actions workflow for all test suites
- Pre-commit hook running relevant tests
- Test count tracking
- Setup effort: M

### 9.4 Missing Test Utilities [P2]

- **FakePlaylistCacheDao** -- should be reusable like FakeChannelDao
- **FakeTimeLimitDao** -- private scope, should be extracted
- **FakeRelayConnector** -- no fake exists
- **TestServer helper** -- extract from RelayBridgeIntegrationTest

---

## 10. Recommended Test Implementation Plan

### Phase 1: Release Blockers (P0) -- Estimated 3-5 days

1. **Relay allowlist: add time-limit routes** (or document as intentional) -- S
2. **ContentSourceRepository pure function tests** (`buildCanonicalUrl`, `extractVideoId`) -- S
3. **Dashboard XSS prevention tests** (escapeHtml unit test at minimum) -- S
4. **Relay Durable Object core tests** (connect, reject, bridge, timeout) -- L
5. **Relay Worker entry point tests** (path parsing, static assets, rate limiting) -- L

### Phase 2: Should Have (P1) -- Estimated 3-5 days

6. **Missing Ktor route auth tests** (unauthenticated access to protected routes) -- M
7. **Dashboard auth flow tests** with JSDOM + fetch mock -- M
8. **Relay concurrent request isolation test** -- S
9. **RoomTimeLimitStore roundtrip test** -- S
10. **CI/CD pipeline setup** -- M
11. **Security test suite** (XSS, auth bypass, debug intent guard) -- M

### Phase 3: Nice to Have (P2) -- Ongoing

12. **Dashboard full test suite** (playlist management, time limits UI, polling) -- L
13. **HomeViewModel tests** -- M
14. **Compose UI tests** (basic screen rendering) -- L
15. **Test utility extraction** (FakeDAOs, TestServer) -- S

---

## Appendix A: Test Count by Module

| Module | Production Files | Test Files | Tests | Coverage Estimate |
|--------|-----------------|------------|-------|-------------------|
| auth/ | 4 | 2 | 32 | 90% |
| server/ | 7 | 7 | 75 | 80% |
| playback/ | 3 | 3 | 19 | 85% |
| relay/ | 3 | 6 | 55 | 75% (connector) / 0% (DO) |
| data/ | 7 | 3 | 24 | 60% |
| timelimits/ | 5 | 3 | 61 | 90% |
| util/ | 5 | 3 | 14 | 50% |
| ui/ | 8 | 0 | 0 | 0% |
| debug/ | 1 | 1 (instrumented) | 12 | 40% |
| relay (TS) | 4 | 7 | 139 | 70% (allowlist excellent, DO/Worker 0%) |
| dashboard (JS) | 1 (app.js) | 1 (logic only) | 19 | 10% |
| marketing | 2 | 2 | 19 | 80% |

## Appendix B: Test Pattern Quality Assessment

**Strengths:**
- Injectable clocks everywhere
- Fake DAOs follow DAO interface exactly, catching API drift
- Protocol contract tests ensure Kotlin/TypeScript serialization stays in sync
- `testApplication` pattern for Ktor routes is clean and consistent
- FakeWebSocketFactory enables relay connector testing without real network

**Weaknesses:**
- Dashboard tests re-implement logic rather than testing actual `app.js`
- No property-based testing or fuzzing
- No performance/load tests
- Several test files use `mockk(relaxed = true)` which can hide unexpected calls
- `RelayBridgeIntegrationTest` uses `advanceTimeBy(5000)` which is fragile

---

*Review completed 2026-02-20. 28 TV unit test files, 3 instrumented test files, 7 relay test files, 2 marketing test files analyzed.*
