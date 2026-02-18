# KidsWatch v4 — Remote Relay Spec

**Date:** 2026-02-17
**Status:** Draft (from council discussion in napkin-v4-council.md)
**Scope:** Remote access via application-aware relay. Parents manage and monitor the TV from anywhere.
**Goal:** One URL, one bookmark — works from home, work, anywhere.

---

## Why This Release Exists

KidsWatch v3 requires the parent's phone and the TV to be on the same WiFi network. The parent dashboard is served from the TV's embedded Ktor server at `http://<tv-ip>:8080`.

This means:
- A parent at work can't add a playlist for tonight
- A parent at work can't check what the kids are watching
- A parent at work can't say "that's enough" and stop playback
- Future features (daily time limits, usage controls) are local-only

v4 makes the TV remotely accessible through a lightweight relay, without changing the TV's local-first architecture.

---

## Architecture

```
Phone (anywhere)          Cloudflare Edge            TV (home WiFi)
     |                         |                          |
     |  GET /tv/{tvId}/  ───►  |                          |
     |  ◄── dashboard HTML     |  (static, edge-cached)   |
     |                         |                          |
     |  GET /tv/{tvId}/api/ ─► |                          |
     |                         |  [path allowlist ✓]      |
     |                         |  [rate limit ✓]          |
     |                         |  [payload size ✓]        |
     |                         |                          |
     |                         |  WS: {id,method,path} ─► |
     |                         |                          | [Ktor processes]
     |                         |  ◄─ WS: {id,status,body} |
     |                         |                          |
     |                         |  [response type ✓]       |
     |  ◄── JSON response      |                          |
```

- **TV** opens an outbound WebSocket to the relay on startup
- **Relay** (Cloudflare Worker + Durable Object) holds the WebSocket
- **Phone** sends HTTPS requests to the relay
- **Relay** validates the request (path, method, size), forwards through WebSocket to TV
- **TV's Ktor routes** process the request identically to local — they don't know about the relay
- **Relay** validates the response (type, size) and returns it to the phone
- **Dashboard** (HTML/JS/CSS) served from Cloudflare edge, not from the TV

If the relay is unreachable, the TV works exactly as v3 — local Ktor server still runs.

---

## Platform Decision

**Cloudflare Workers + Durable Objects**

Reasons:
1. Durable Objects are purpose-built for holding WebSocket connections with in-memory state
2. Edge-distributed (300+ PoPs) — low latency globally, no region selection
3. Mature MCP server (`workers-mcp`, `mcp-server-cloudflare`) — Claude Code can deploy, view logs, manage KV directly in the TDD loop
4. Workers Sites for edge-cached static dashboard assets
5. $5/month paid plan (required for Durable Objects). Compute costs negligible at family scale.
6. Well-documented, widely used for WebSocket relay patterns

**MCP Integration:**
- `mcp-server-cloudflare` added to Claude Code MCP config
- Claude Code can: deploy Workers, read logs, manage KV, interact with Durable Objects
- Enables tight TDD cycle: write test → implement → deploy to staging → verify → deploy to production

---

## Components to Build

### 1. Relay Worker (`relay/`)

**~200 lines of TypeScript.** A Cloudflare Worker + Durable Object that:

- Accepts WebSocket connections from TVs (authenticated with tv-secret)
- Accepts HTTPS requests from phones (forwarded to TV via WebSocket)
- Serves dashboard static assets (HTML/JS/CSS) from Workers Sites
- Enforces security controls (path allowlist, rate limits, payload caps, response type validation)

**Files:**
```
relay/
├── src/
│   ├── index.ts          # Worker entry point, routing
│   ├── relay.ts          # Durable Object: WebSocket + request bridging
│   ├── allowlist.ts      # Path/method/content-type rules
│   ├── ratelimit.ts      # Token bucket rate limiter
│   └── protocol.ts       # WebSocket message format types
├── test/
│   ├── allowlist.test.ts
│   ├── ratelimit.test.ts
│   ├── relay.test.ts
│   ├── security.test.ts
│   ├── dashboard.test.ts
│   └── protocol.test.ts
├── assets/               # Dashboard HTML/JS/CSS (served via Workers Sites)
│   ├── index.html
│   ├── app.js
│   └── style.css
├── wrangler.toml
├── package.json
├── tsconfig.json
└── vitest.config.ts
```

### 2. TV RelayConnector (`tv-app/`)

**New class: `RelayConnector.kt`** — manages the outbound WebSocket to the relay.

- Connects to relay on app startup with tv-id + tv-secret
- Sends heartbeat every 30s
- Receives forwarded HTTP requests via WebSocket
- Bridges them to the local Ktor server
- Sends responses back through WebSocket
- Reconnects on disconnect (exponential backoff: 1s → 2s → 4s → 8s → cap 60s)
- Re-authenticates on every reconnect
- Listens for WiFi state changes → immediate reconnect
- Runs within the existing Ktor foreground service (survives Android Doze)

**Config (SharedPreferences):**
- `tv_id` — UUID, generated on first launch, permanent (survives until app data cleared)
- `tv_secret` — 256-bit random hex, generated on first launch, rotates on PIN reset
- `relay_url` — configurable, defaults to production relay URL

**Session token persistence (change to SessionManager):**
- Session tokens must be persisted in SharedPreferences, not just in-memory
- Tokens survive app restart, app update (sideload APK), and device reboot
- On startup, SessionManager loads persisted tokens and resumes validation
- This is a change from v3, where SessionManager held tokens only in memory

### 3. ConnectScreen Update

- QR code encodes: `https://{relay-domain}/tv/{tv-id}/connect?secret={tv-secret}&pin={pin}`
- Dashboard auto-submits PIN from query param — no manual entry needed
- QR refreshes when PIN changes. PIN is single-use.
- Parent scans → opens relay URL → auto-authenticated → bookmarks URL → done

**ConnectScreen UI layout:**
- **Prominent:** Remote QR code (relay URL + secret + PIN) — main focus of the screen
- **Small:** TV IP address text (for local/debug reference)
- **Small:** Settings gear icon (bottom-right corner)

**Settings panel (opens from gear icon):**
- TV info (tv-id, app version, uptime)
- Debug QR code (encodes `http://<tv-ip>:8080/debug/` — local network only)
- Debug controls (existing debug intents)
- Relay config (relay URL, connection status)
- Current PIN text (for manual entry if QR scan fails)

### 4. Dashboard Changes

- Dashboard assets move from `tv-app/app/src/main/assets/` to `relay/assets/`
- Base URL changes from `http://<tv-ip>:8080` to `https://{relay-domain}/tv/{tvId}/api`
- TV-id extracted from URL path: `const tvId = window.location.pathname.split('/')[2]`
- API calls are relative to the relay URL (same origin)
- Offline state: if API returns 503 (TV not connected), show "TV is offline"
- Version check: on load, call `GET /api/status`, check API version. Mismatch → "TV app needs updating"

### 5. Token Refresh Endpoint

- New endpoint: `POST /api/auth/refresh`
- If current bearer token is valid, return a new token with fresh 90-day TTL
- Dashboard calls this on each page load (keeps session alive)
- Parent only needs PIN for initial pairing (physical presence)
- Rate-limited: 5/hour per token

---

## Security Model

The relay is an **application-aware gateway**, not a generic tunnel. It cannot be abused as a VPN/proxy.

### Abuse Prevention Controls

| # | Control | Implementation | Why |
|---|---------|---------------|-----|
| 1 | Path allowlist | Only forward `/api/auth`, `/api/playlists`, `/api/playback/*`, `/api/stats/*`, `/api/status` | Prevents generic proxy abuse |
| 2 | Method allowlist | GET, POST, DELETE only | Blocks CONNECT/tunnel methods |
| 3 | Response type check | Only `application/json` and `text/html` forwarded | Prevents binary data tunneling |
| 4 | Request payload cap | 10KB max body | Prevents file transfer abuse |
| 5 | Response payload cap | 100KB max body | Prevents large data exfiltration |
| 6 | 1 WebSocket per tv-id | New connection replaces old | Prevents connection multiplication |
| 7 | 5 tv-ids per source IP | Reject excess registrations | Prevents mass fake TV registration |
| 8 | Phone rate limit | 60 req/min per tv-id | Prevents flooding |
| 9 | TV rate limit | 10 req/min per tv-id (TV-initiated) | Prevents compromised TV flooding |
| 10 | WebSocket auth | tv-secret required, timing-safe comparison | No anonymous TV connections |
| 11 | Heartbeat timeout | 90s without heartbeat → disconnect | Prevents connection hoarding |
| 12 | Malformed frame rejection | Invalid JSON or oversized WS frames → drop | Prevents protocol abuse |

### Auth Flow

1. **Initial pairing (one-time, requires physical presence):**
   - TV shows QR code with relay URL + tv-secret
   - Parent scans, enters PIN (displayed on TV screen)
   - Relay forwards PIN to TV → TV validates → returns bearer token
   - Dashboard stores token in localStorage. Parent bookmarks URL.

2. **Subsequent use (from anywhere):**
   - Dashboard sends bearer token in Authorization header
   - Relay forwards header to TV → TV validates → returns response
   - Dashboard refreshes token on each page load (`POST /api/auth/refresh`)

3. **Token expiry:**
   - 90-day TTL. Refresh extends it indefinitely.
   - If token expires (parent inactive >90 days), dashboard shows "Session expired. Scan QR code at TV to re-pair."
   - Re-pairing requires physical presence (see PIN on TV, scan QR)

### Threat Model

| Threat | Risk | Mitigation |
|--------|------|-----------|
| Relay used as VPN/proxy | High impact | Path allowlist + response type check (kills generic proxy use) |
| Stolen session token | Medium | Token only grants access to playlist/playback data (low sensitivity). Rotate on PIN reset. |
| Stolen tv-secret | Medium | Attacker could connect a fake TV. Rotate on PIN reset. tv-id + tv-secret needed together. |
| Relay compromise (MitM) | Low | Relay can see tokens and data in transit. Acceptable for v4 (playlist URLs, watch stats). E2E encryption deferred to v5+. |
| DDoS on relay | Low | Cloudflare provides DDoS protection. Rate limits per tv-id. |
| Resource exhaustion | Low | Connection limits + rate limits + payload caps |

### PIN Reset vs Clear Sessions

Two distinct security operations:

**PIN Reset (nuclear):**
- Generates new PIN
- Generates new tv-secret (256-bit random)
- Clears all session tokens
- All parents must re-pair (scan new QR at TV)
- Use when: TV compromised, tv-secret leaked, or full security reset needed

**Clear Sessions (targeted):**
- Invalidates all session tokens
- tv-secret unchanged — QR code stays the same
- Parents must re-enter PIN (physical presence) but don't need to re-scan QR
- Use when: revoking a specific parent's access or routine session cleanup

### Not in v4 (security roadmap)

- End-to-end encryption (phone ↔ TV, relay can't read payloads)
- TV identity verification beyond shared secret
- Payload content inspection
- Abuse reporting/alerting

---

## What Works Remotely

| Feature | Local (same WiFi) | Remote (via relay) |
|---------|-------------------|-------------------|
| Add/remove playlist | Yes | Yes |
| View playlists | Yes | Yes |
| Now Playing status | Yes | Yes |
| Playback control (pause/skip/stop) | Yes | Yes |
| Watch stats/history | Yes | Yes |
| PIN auth (initial pairing) | Yes | No (requires physical presence) |
| Token refresh | Yes | Yes |
| Debug endpoints (`/debug/*`) | Yes (local only) | No (not in allowlist) |
| Future: daily time limits | Yes | Yes (same API pattern) |

---

## WebSocket Protocol

### Message Format

**Request (Relay → TV):**
```json
{
  "id": "req-abc123",
  "method": "GET",
  "path": "/api/playlists",
  "headers": {
    "Authorization": "Bearer xyz789",
    "Content-Type": "application/json"
  },
  "body": null
}
```

**Response (TV → Relay):**
```json
{
  "id": "req-abc123",
  "status": 200,
  "headers": {
    "Content-Type": "application/json"
  },
  "body": "{\"playlists\": [...]}"
}
```

**Heartbeat (TV → Relay, every 30s):**
```json
{
  "type": "heartbeat",
  "tvId": "uuid-here",
  "uptime": 3600
}
```

**Connection (TV → Relay, on WebSocket open):**
```json
{
  "type": "connect",
  "tvId": "uuid-here",
  "tvSecret": "hex-256bit",
  "protocolVersion": 1,
  "appVersion": "4.0.0"
}
```

---

## Local Fallback

**No automatic local fallback in the dashboard.**

- The relay URL is the only parent-facing entry point
- One code path = one security model = easier to test and audit
- Relay latency (~50-100ms round trip) is imperceptible for dashboard actions
- Local Ktor server stays running for:
  - Processing requests forwarded from relay via WebSocket
  - Developer debug access (`http://<tv-ip>:8080/debug/*`)
  - Emergency fallback if relay service is discontinued

---

## Dashboard Serving

**Relay serves dashboard static assets. TV only handles API calls.**

- Dashboard HTML/JS/CSS deployed with the relay Worker via Workers Sites
- Phone loads dashboard from Cloudflare edge (fast, cached globally)
- Dashboard JS makes API calls to same origin → Worker forwards to TV via WebSocket
- TV offline → page loads instantly, shows "TV is offline"
- Dashboard updates without TV APK update (deploy Worker only)

**Route split at the relay:**
- `GET /tv/{tvId}/` → serve `index.html` (static, from Workers Sites)
- `GET /tv/{tvId}/app.js` → serve `app.js` (static)
- `GET /tv/{tvId}/style.css` → serve `style.css` (static)
- `GET /tv/{tvId}/api/*` → forward to TV via WebSocket
- `GET /tv/{tvId}/ws` → WebSocket endpoint for TV connections

---

## TV Offline Handling

### v4 (stateless relay)
- TV disconnects → Durable Object marks tv-id as offline
- Phone requests → relay returns 503 with `{"error": "TV is offline"}`
- Dashboard shows: "TV is offline. Check that your TV is powered on and connected to WiFi."

### v4.1 (deferred — cached state)
- Relay caches recent GET responses while TV is connected
- TV disconnects → relay serves cached data with `"stale": true, "lastSeen": "2026-02-17T15:30:00Z"`
- Dashboard shows cached playlists/stats with "Last updated 2 hours ago"
- v4 relay design does NOT preclude this (Durable Object storage available)

---

## Debug Routes

**Local-only in v4.** `/debug/*` paths are NOT in the relay allowlist.

**Fast-follow (if friction log shows need):**
- TV sends `debug_enabled: true` in WebSocket heartbeat
- Relay checks this flag before forwarding `/debug/*` requests
- Enabled via debug intent (`DEBUG_ENABLE_REMOTE_DEBUG`), resets on app restart
- Opt-in, per-TV, temporary — not a permanent attack surface

---

## Testing Strategy — 100% TDD

### Test Pyramid

```
          /  1 E2E smoke test (staging Worker, real HTTP)      \
         /   ~10 protocol contract tests (both sides)           \
        /    ~15-20 dashboard JS tests (Vitest + jsdom)           \
       /     ~25-30 TV RelayConnector unit tests (JUnit)            \
      /      ~60 relay unit + security tests (Vitest)                 \
     /       ~105+ existing TV unit tests (unchanged)                   \
    /____________________________________________________________\
```

### Test Runtime

Relay tests use **Vitest + `@cloudflare/vitest-pool-workers`** with Miniflare. Durable Objects and WebSocket run locally in Miniflare — no mocking needed. Tests exercise real Worker bindings, real Durable Object storage, and real WebSocket connections against the local Miniflare runtime.

### Layer 1: Relay Unit Tests (Vitest, ~60 tests)

**Path allowlist:**
- Forwards known API paths (GET/POST/DELETE for each endpoint)
- Rejects unknown paths → 404
- Rejects disallowed methods (CONNECT, PUT) → 405

**Rate limiting:**
- 60 req/min allowed, 61st → 429
- Resets after window
- Isolated per tv-id

**Payload limits:**
- Accepts under-limit, rejects over-limit (413 request, 502 response)

**Response type validation:**
- Forwards application/json and text/html
- Rejects application/octet-stream → 502

**WebSocket management:**
- Valid tv-secret → accept. Invalid → reject
- Reconnect replaces old connection
- 503 when TV not connected
- 90s heartbeat timeout → disconnect

**Connection limits:**
- 1 WebSocket per tv-id
- 5 tv-ids per source IP

**Auth forwarding:**
- Forwards Authorization header, forwards 401 response
- Does not cache/log tokens

**Static asset serving:**
- Correct Content-Type for HTML/JS/CSS
- Static assets don't require auth

**Security (Raj's tests):**
- Cannot proxy to external URLs
- Rejects invalid WebSocket frames
- Timing-safe tv-secret comparison
- Oversized WS frames rejected
- Malformed JSON handled gracefully

### Layer 2: TV RelayConnector Unit Tests (JUnit, ~25-30 tests)

**Connection lifecycle:**
- Connects with tv-id + tv-secret
- Heartbeat every 30s
- Exponential backoff reconnection (1s → 2s → 4s → 8s → cap 60s)
- Re-authenticates on reconnect
- Stops on explicit disconnect
- Resets backoff after success
- WiFi state change triggers reconnect

**Request bridging:**
- Converts WS message → local Ktor HTTP request
- Sends Ktor response → WS message
- Handles concurrent requests
- 10s timeout for local Ktor
- Error response on Ktor exception

**Config:**
- Generates tv-id (UUID) on first launch
- Generates tv-secret (256-bit random) on first launch
- Persists in SharedPreferences
- Rotates tv-secret on PIN reset
- Reads relay URL from config

### Layer 3: Protocol Contract Tests (~10 tests)

Shared message format verified on BOTH sides:
- Relay: "given this HTTP request, I send this WS frame" / "given this WS frame, I return this HTTP response"
- TV: "given this WS frame, I make this local HTTP request" / "given this HTTP response, I send this WS frame"
- Both sides test against identical message format. Drift → test failure.

### Layer 4: Dashboard JS Tests (Vitest + jsdom, ~15-20 tests)

- Shows "TV offline" when API returns 503
- Shows "Now Playing: X" when API returns playing status
- Renders playlists correctly
- Handles auth token storage/retrieval
- Extracts tvId from URL path
- Version check on load
- Refresh token on load

### Layer 5: E2E Smoke Test (1 test script)

- Deploy relay to staging Worker
- Script sends real HTTPS requests
- Verifies: allowlisted path works, blocked path → 404, oversized payload → 413, no TV → 503

### Test Count

| Suite | Count | Runner |
|-------|-------|--------|
| Relay unit + security tests | ~60 | Vitest |
| Dashboard JS tests | ~15-20 | Vitest + jsdom |
| Protocol contract tests | ~10 | Vitest (relay) + JUnit (TV) |
| TV RelayConnector tests | ~25-30 | `./gradlew testDebugUnitTest` |
| Existing TV unit tests | 105+ | `./gradlew testDebugUnitTest` |
| E2E smoke test | 1 | curl/node script |
| **New v4 tests** | **~110-120** | |
| **Total all tests** | **~215-225** | |

---

## CI/CD Pipeline

### Monorepo Structure

```
KidsWatch/
├── tv-app/               # Android TV app (existing)
├── relay/                # Cloudflare Worker (new)
│   ├── src/
│   ├── test/
│   ├── assets/           # Dashboard HTML/JS/CSS
│   ├── wrangler.toml
│   ├── package.json
│   └── vitest.config.ts
├── docs/
└── CLAUDE.md
```

### Relay CI

```
on: push to relay/**

1. npm ci
2. eslint
3. tsc --noEmit
4. vitest run (all relay + dashboard + contract tests)
5. wrangler deploy --env staging
6. E2E smoke test against staging
7. wrangler deploy --env production (main branch only, all tests green)
```

### TV CI

```
on: push to tv-app/**

1. ./gradlew testDebugUnitTest (existing + RelayConnector + contract tests)
2. ./gradlew assembleDebug
3. Instrumented tests (if emulator available)
```

### Protocol Version Check

Both `relay/package.json` and `tv-app/build.gradle.kts` declare `PROTOCOL_VERSION`. CI fails if they diverge on PRs touching both directories.

### Environments

| Environment | Relay Worker | Dashboard | TV APK |
|-------------|-------------|-----------|--------|
| **Staging** | `kidswatch-relay-staging.workers.dev` | Staging assets | Debug builds |
| **Production** | `kidswatch-relay.workers.dev` (later: custom domain) | Production assets | Release builds |

Staging and production use separate Durable Object namespaces. Never mix.

### Deployment Order

1. Deploy relay first (backward compatible — supports current + previous protocol version)
2. Deploy TV APK second (sideload to Mi Box)
3. At family scale: "deploy relay, update APK within a day"

### MCP Integration

Add `mcp-server-cloudflare` to Claude Code config for the project. Enables:
- Deploy Workers from Claude Code (`wrangler deploy`)
- View Worker logs and analytics
- Manage KV storage
- Debug Durable Object state
- Tight TDD loop: write test → implement → deploy → verify

---

## Domain Strategy

| Phase | Domain | Cost |
|-------|--------|------|
| Development | `kidswatch-relay.workers.dev` | Free |
| Production | `kidswatch.dev` or `kidswatch.app` | ~$12-14/year |

Don't block v4 development on domain purchase. Use `workers.dev` subdomain for dev and staging. Buy domain when shipping to real users (Mi Box).

---

## Moldable Development Strategy

v4 introduces new infrastructure (relay) where we don't yet know where friction will occur. Follow the friction-log approach:

### Build Now (predictable design)
- Path allowlist, rate limiting, payload caps — standard patterns
- Dashboard serving from edge — standard SPA
- tv-id/tv-secret generation — straightforward
- Token refresh endpoint — cheap, clearly needed

### Friction-Log Candidates (build at 3 strikes)

| Candidate | Trigger | Domain Object |
|-----------|---------|---------------|
| WebSocket lifecycle | "Why is TV showing offline?" × 3 | `ConnectionLog` — connect/disconnect/reconnect with timestamps and reasons |
| Request forwarding failures | "Dashboard spun forever" × 3 | `RelayRequest` — full lifecycle: received, forwarded, responded, error states |
| Auth flow via relay | PIN entry broken through relay × 3 | `RemoteAuthAttempt` — relay forwarded? TV received? Token returned? |
| Dashboard version mismatch | Weird UI bugs from version skew × 3 | Version handshake log |
| Stale data confusion (v4.1) | "Why am I seeing old data?" × 3 | `CacheEntry` with staleness tracking |
| Abuse detection | Suspicious traffic patterns × 3 | `AbuseSignal` — source IP, rates, path distribution |
| Connection state UX | Users confused by states × 3 | Refined state machine with real lifecycle data |

---

## User Interaction Flows

### Flow 1 — First-time Setup (Pairing)
1. Parent installs APK on TV (sideload)
2. TV generates tv-id, tv-secret, PIN on first launch
3. TV shows ConnectScreen with QR code (relay URL + secret + PIN)
4. Parent scans QR on phone → browser opens relay URL → auto-authenticated
5. Parent bookmarks the URL → done. That bookmark is their permanent dashboard.

### Flow 2 — Daily Use (Remote)
1. Parent opens bookmarked relay URL from anywhere
2. Dashboard loads from Cloudflare edge (fast, cached)
3. Dashboard refreshes session token (`POST /api/auth/refresh`)
4. Parent views playlists, now-playing, stats → manages content
5. Dashboard polls for updates (now-playing status)

### Flow 3 — TV Offline
1. Parent opens bookmark → dashboard loads from edge (instant)
2. API call returns 503 → dashboard shows "TV is offline"
3. Dashboard retries every 30s
4. TV comes back online → next retry succeeds → dashboard auto-resumes

### Flow 4 — Session Expired (>90 days)
1. Parent opens bookmark after >90 days of inactivity
2. Token refresh returns 401
3. Dashboard shows "Session expired, scan QR at TV"
4. Parent goes to TV → scans QR → re-authenticated

### Flow 5 — Second Parent
1. Second parent scans QR code on TV
2. Gets their own session token (separate from first parent)
3. Both parents share the same tvId, each with independent sessions

### Flow 6 — App Update
1. Parent sideloads new APK to TV
2. SharedPreferences preserved (tv-id, tv-secret, session tokens)
3. RelayConnector reconnects to relay with existing credentials
4. No re-pairing needed

### Flow 7 — Developer Debug
1. On ConnectScreen, tap settings gear (bottom-right)
2. Settings panel shows debug QR code (local IP → `/debug/`)
3. Scan debug QR on phone (must be on same WiFi)
4. Opens local debug dashboard with full debug controls

### Flow 8a — PIN Reset (nuclear)
1. Trigger PIN reset (settings or debug intent)
2. New PIN generated + new tv-secret generated + all sessions cleared
3. All parents must re-pair: scan new QR at TV

### Flow 8b — Clear Sessions (targeted)
1. Trigger clear sessions (settings or debug intent)
2. All session tokens invalidated
3. tv-secret unchanged — QR code stays the same
4. Parents must re-enter PIN (physical presence) but don't re-scan QR

### Flow 9 — Relay Down
1. Relay returns HTTP error or is unreachable
2. TV is unaffected — local Ktor server still runs, kids keep watching
3. Parent on same WiFi can access `http://<tv-ip>:8080` as local fallback
4. Relay recovers → TV auto-reconnects (exponential backoff) → remote access resumes

---

## Implementation Phases

### Phase 1: Relay Foundation (TDD)
1. Set up `relay/` directory with Vitest, TypeScript, wrangler
2. TDD the path allowlist (tests first → implement)
3. TDD rate limiting
4. TDD payload size limits
5. TDD response type validation
6. TDD the Durable Object: WebSocket accept, heartbeat, timeout
7. TDD request bridging: HTTPS → WS → response → HTTPS
8. TDD auth forwarding
9. TDD connection limits (1 per tv-id, 5 per IP)
10. Deploy to staging, run E2E smoke test

### Phase 2: TV RelayConnector (TDD)
1. TDD `RelayConnector` class: connect, heartbeat, reconnect
2. TDD request bridging: WS message → local Ktor call → WS response
3. TDD config: tv-id/tv-secret generation and persistence
4. TDD tv-secret rotation on PIN reset
5. Wire into `KidsWatchApp.onCreate()` via ServiceLocator
6. Wire into existing Ktor foreground service lifecycle

### Phase 3: Dashboard Migration
1. Move dashboard assets from `tv-app/assets/` to `relay/assets/`
2. Update `app.js`: base URL from relay, tvId from URL path
3. TDD dashboard JS logic (Vitest + jsdom): offline state, auth, rendering
4. Add version check on load
5. Add token refresh on load
6. Deploy to staging, manual test on phone

### Phase 4: ConnectScreen + Integration
1. Update ConnectScreen QR code to encode relay URL
2. Add `POST /api/auth/refresh` endpoint to TV's Ktor routes (TDD)
3. End-to-end test: scan QR → enter PIN → use dashboard → verify relay forwarding
4. Test on Mi Box (real hardware — WebSocket reliability, WiFi drops, sleep/wake)

### Phase 5: Security Hardening
1. Raj's security test suite (Vitest)
2. Verify: non-allowlisted paths blocked, oversized payloads rejected, timing-safe comparisons
3. Penetration test: attempt to use relay as proxy (should fail)
4. Rate limit verification under load

---

## What Does NOT Change

- **TV app architecture.** Still Ktor + Room + ExoPlayer + NewPipe. No new Android dependencies except OkHttp WebSocket (already transitive via Ktor).
- **Ktor routes.** All existing routes work identically. They don't know about the relay.
- **Room database.** No schema changes.
- **Kid's experience.** The TV UI is completely unchanged.
- **Local Ktor server.** Still runs, still accessible on the LAN for debug.
- **Existing tests.** All 105+ TV unit tests pass without modification.

**Note:** SessionManager IS changing in v4 — session tokens move from in-memory to SharedPreferences persistence. See Component 2 (TV RelayConnector) for details.

---

## What's NOT in v4

- TV offline caching (v4.1 — relay caches last-known state)
- Daily time limits / usage controls (v4.2 — parent sets limit, TV enforces locally)
- Native phone app (not planned — browser dashboard is sufficient)
- End-to-end encryption (v5+ — if threat model changes)
- Multi-TV support (works technically — each TV gets its own tv-id — but no unified dashboard)
- Push notifications ("your kid has been watching for 2 hours")
- Remote debug routes (fast-follow if friction log proves need)

---

## Success Criteria

1. Parent at work opens bookmarked URL → sees dashboard → sees what kid is watching → adds a playlist → it appears on TV within seconds
2. All 215+ tests pass (existing + new)
3. Relay is deployed to Cloudflare Workers, accessible via HTTPS
4. TV connects to relay on startup, reconnects on WiFi drop
5. Security: non-allowlisted paths return 404, oversized payloads rejected, rate limits enforced
6. TV works normally if relay is unreachable (local-first preserved)

---

## Cost

| Item | Cost |
|------|------|
| Cloudflare Workers paid plan | $5/month |
| Domain (when ready) | $12-14/year |
| Compute/bandwidth | Negligible at family scale |
| **Total** | **~$6/month** |

---

## Haiku

*One link, near or far*
*the TV hears a parent —*
*playlist waits at home*
