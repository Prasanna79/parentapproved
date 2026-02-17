# KidsWatch V0.3 — Product Spec (Remote Relay)

*Add-on to V0.2.1. Enables remote access to the TV's local server via a lightweight relay.*

## What It Is

V0.2.1 works on the home WiFi. V0.3 adds an optional relay server so the parent can manage playlists and view stats from anywhere — work, commute, grandma's house. The TV is still the backend. The relay is just a pipe.

This is charityware. No gates, no codes, no accounts. The app includes a "If you find this useful, please donate to [charity]" message. Honor system.

---

## Architecture Principle

**The relay is a dumb pipe — almost.** It forwards HTTP requests from the parent to the TV over a WebSocket tunnel, and forwards responses back. It doesn't store playlists, videos, PINs, or user data. Its only intelligence: it peeks at HTTP response status codes to rate-limit failed PIN attempts.

**The TV is still the backend.** The same Ktor server handles the same requests whether they arrive from local WiFi or through the relay. The parent dashboard is identical. Room DB is still the source of truth.

---

## The Parent Journey (Remote)

1. Parent has already set up V0.2.1 (TV + local access works)
2. On TV Settings: taps "Enable Remote Access"
3. TV connects outbound to the relay via WebSocket
4. TV displays a new QR code with the relay URL: `https://relay.kidswatch.app/tv/f7k2m9p4x3n8...`
5. Parent scans QR code (or bookmarks the URL)
6. Browser opens, parent enters PIN (same PIN as local)
7. Same dashboard — add playlists, view stats, manage
8. Works from anywhere with internet

---

## What Changes from V0.2.1

### TV App Changes

| Component | Change |
|-----------|--------|
| Settings screen | New "Enable Remote Access" toggle + relay status indicator |
| Connect screen | When relay enabled: shows both local QR code and remote QR code |
| WebSocket client | New: ~100 lines. Connects to relay, receives HTTP requests, forwards to localhost Ktor, returns responses. |
| Foreground service | New: keeps WebSocket alive when app is backgrounded. Persistent notification: "KidsWatch remote access active." |
| PIN display | Now also shown with relay URL context: "Use this PIN for both local and remote access" |

### Parent Dashboard Changes

None. The same HTML/JS works whether the parent is on local WiFi or coming through the relay. The dashboard doesn't know or care about the transport.

### New Component: Relay Server

A standalone server you host. ~200 lines of Node.js or Go.

---

## Relay Server

### What It Does

1. Accepts WebSocket connections from TVs
2. Assigns each TV a random 24-character slug
3. Accepts HTTPS requests from parents at `/tv/{slug}/*`
4. Forwards requests to the TV over the WebSocket
5. Forwards the TV's response back to the parent
6. Rate-limits failed PIN attempts per slug

### What It Doesn't Do

- Store playlists, videos, or user data
- Know what a PIN is (never parses request bodies)
- Authenticate parents (the TV does that)
- Persist anything to disk (all state is in-memory)
- Require accounts, tokens, or registration

### API Routes

```
WSS /ws/connect                   ← TV connects here
                                    TV sends: initial handshake (no auth needed)
                                    Relay assigns slug, sends it back
                                    Then: relay sends HTTP requests, TV sends responses

GET  /tv/{slug}/*                 → forward to TV via WebSocket → return response
POST /tv/{slug}/*                 → forward to TV via WebSocket → return response
DELETE /tv/{slug}/*               → forward to TV via WebSocket → return response
```

### Rate Limiting (the "almost dumb" part)

The relay inspects **only the HTTP response status code** from the TV:

```
Parent → POST /tv/{slug}/auth     → relay forwards to TV
TV responds 200 (correct PIN)     → relay forwards back, does NOT count
TV responds 401 (wrong PIN)       → relay forwards back, COUNTS as failure

5 failures in 15 minutes per slug → relay returns 429 directly (never reaches TV)
Counter resets after 15 min of no failures
```

**Why the relay rate-limits instead of just the TV:**
- The TV is an Android device — it shouldn't absorb brute-force floods
- The relay protects the TV from request volume
- The TV also rate-limits locally (defense in depth)

### In-Memory State

```javascript
// This is ALL the relay stores. In memory. Not persisted.
connections = {
  "f7k2m9p4x3n8q5w2j8r6t4v1": {
    ws: <WebSocket>,              // connection to TV
    failCount: 2,                 // auth failures in current window
    windowStart: 1708185600000,   // when the current 15-min window started
    lastActivity: 1708185650000   // for idle timeout
  }
}
```

When the TV disconnects, its entry is deleted. Zero persistent state.

### Connection Management

- TV connects → relay assigns 24-char random slug → TV displays it
- TV must send heartbeat every 30s or relay drops the connection
- Idle TVs (no parent requests for 24h) get a "still there?" ping
- If TV disconnects, parent gets "TV is offline. Try again when KidsWatch is running."
- TV reconnects → gets a **new slug** (old URL stops working, TV shows new QR code)

### Abuse Protection

No registration means any TV can connect. Mitigations:

| Concern | Mitigation |
|---------|------------|
| Flood of fake TVs | Max 3 WebSocket connections per IP per hour |
| Resource exhaustion | Max 500 total concurrent connections (way beyond charityware needs) |
| Slug enumeration | 24-char base62 = 10^43 possibilities. Unscannable. |
| PIN brute-force | 5 attempts / 15 min per slug. TV hard-locks after 10 total. |
| Bandwidth abuse | Max 1MB per request/response. Dashboard pages are tiny. |

---

## Security Model

### The Layers

```
Layer 1: Slug (finding the TV)
  24-char random base62 = ~143 bits of entropy
  Delivered via QR code on the physical TV screen
  Parent scans it — never types it
  Attacker can't find any TV to attack without the slug

Layer 2: Relay rate limit (protecting the TV)
  Counts 401 responses per slug
  5 fails / 15 min → blocks auth requests for that slug
  Doesn't parse request bodies — just reads response codes

Layer 3: PIN (authenticating the parent)
  6-digit, displayed on TV screen
  Same PIN for local and remote access
  Rotates every 24 hours
  At 5 attempts / 15 min = ~1,042 days to brute-force on average
  Daily rotation resets any progress

Layer 4: TV hard lockout
  10 total remote auth failures → TV disables remote access
  Parent must re-enable on the physical TV
  Prevents slow-burn brute-force over days

Layer 5: HTTPS
  Let's Encrypt cert on relay domain
  WSS (encrypted WebSocket) between TV and relay
  Only unencrypted hop: localhost on the TV (Ktor)
```

### PIN Exposure

| Path | PIN encrypted? | Who could see it? |
|------|---------------|-------------------|
| Local (WiFi) | No (HTTP) | Someone sniffing your home WiFi |
| Remote (relay) | Yes (HTTPS + WSS) | The relay operator (you) — in memory during forwarding |

### Slug Lifecycle

- New slug assigned every time TV connects to relay
- TV disconnects (backgrounded, rebooted, WiFi drops) → old slug dies
- TV reconnects → new slug, new QR code on TV screen
- Parent must re-scan QR code (or bookmark the new URL from the TV screen)
- **This is a feature:** if a slug leaks, it dies automatically the next time the TV reconnects

---

## Data Flow

```
REMOTE SETUP:
Parent's phone            Relay (your VPS)              Android TV
     |                         |                            |
     |                         |<-- WSS connect ------------|
     |                         |-- assign slug: f7k2... --->|
     |                         |                            |-- display QR code
     |                         |                            |   with relay URL
     |  (scans QR code)        |                            |
     |                         |                            |
     |-- HTTPS POST /auth ---->|                            |
     |                         |-- forward over WS -------->|
     |                         |                    check PIN|
     |                         |<-- 200 + cookie -----------|
     |<-- 200 + cookie --------|                            |

REMOTE USE:
Parent's phone            Relay                         Android TV
     |                         |                            |
     |-- HTTPS GET /playlists ->|                            |
     |                         |-- forward over WS -------->|
     |                         |<-- JSON response ----------|
     |<-- JSON response -------|                            |
     |                         |                            |
     |-- HTTPS POST /playlists->|                            |
     |                         |-- forward over WS -------->|
     |                         |         save to Room, oEmbed|
     |                         |<-- 201 -------------------|
     |<-- 201 ----------------|                            |

RATE LIMIT:
Attacker                  Relay                         Android TV
     |                         |                            |
     |-- POST /auth (wrong) -->|                            |
     |                         |-- forward ----------------> |
     |                         |<-- 401 -------------------|
     |<-- 401 (fail count: 1) -|                            |
     |  ... 4 more wrong ...   |                            |
     |-- POST /auth (wrong) -->|                            |
     |<-- 429 (blocked) -------|  (never forwarded to TV)   |
```

---

## Hosting

| Option | Cost | Capacity |
|--------|------|----------|
| fly.io free tier | $0/mo | 3 shared VMs, 160GB outbound. Dozens of families. |
| Oracle Cloud free tier | $0/mo | ARM VM, 24GB RAM. Hundreds of families. |
| Hetzner VPS (CX22) | ~$4/mo | 2 vCPU, 4GB RAM, 20TB traffic. Overkill. |

For charityware usage levels, free tiers last years.

---

## Charityware Model

```
TV Settings → "Enable Remote Access"
  ↓
Toggle ON → TV connects to relay → QR code appears
  ↓
Below the toggle:
  "KidsWatch is free. If you find it useful, please consider
   donating to [Charity Name] at [charity-url]."
  ↓
That's it. No verification. No codes. Honor system.
```

---

## What's NOT in V0.3

- End-to-end encryption (relay sees decrypted traffic in memory — you run it)
- Multiple TVs in one dashboard (each TV is its own relay connection)
- Time limits or scheduling (could be V0.4 — relay enables push-style control)
- Parent mobile app (still browser-only)
- Relay authentication / registration (open access by design)
- Analytics aggregation across days/weeks (TV has raw events, dashboard shows today/recent)
- Persistent relay state (everything in-memory, nothing survives relay restart)

---

## Key Technical Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Relay framework | Node.js (ws + express) or Go (gorilla/websocket + net/http) | Tiny. ~200 lines. Either works. |
| TV tunnel client | OkHttp WebSocket | Already in the dependency tree. Reliable. |
| Slug entropy | 24-char base62 (~143 bits) | Unguessable. QR-code delivered. Never typed. |
| Rate limiting | Response-code inspection (401 counting) | Relay stays nearly dumb. Doesn't parse bodies. |
| Slug lifecycle | New slug on every reconnect | Leaked slugs die automatically. |
| Charityware gate | None (honor system) | No codes, no accounts, no friction. |
| HTTPS | Let's Encrypt on relay | Free, automated, real cert. |
| Foreground service | Required for V0.3 | WebSocket must survive backgrounding. |
| Persistent notification | "KidsWatch remote access active" | Android requires it for foreground services. |

---

## Prerequisites (V0.3-specific)

### Test 9: WebSocket Tunnel
Validate OkHttp WebSocket from Android TV emulator to a local test relay. Forward an HTTP request, get response. Test reconnection after WiFi toggle.

### Test 10: Relay Rate Limiting
Validate relay correctly counts 401s and blocks after threshold. Verify 200s don't count. Verify counter resets.

### Test 11: Foreground Service on Android TV
Validate foreground service keeps WebSocket alive when app is backgrounded. Test Doze mode. Test TV sleep/wake. Verify persistent notification appearance.

### Test 12: Slug + QR Code Rotation
Validate TV displays new QR code with new relay URL after reconnection. Old slug stops working.

**Tests 9-12 are go/no-go for V0.3. V0.2.1 must be working first.**

---

## Open Questions

1. **Relay restart behavior** — All connections drop. TVs reconnect automatically (new slugs). Parents see "TV offline" briefly. Acceptable for charityware?
2. **Multiple relay instances** — If usage grows beyond one server, sticky sessions by slug? Or just don't worry about it until it's a real problem?
3. **Monitoring** — Minimal: connection count, request rate, error rate. Log to stdout, view via hosting platform's built-in logs.
4. **TV notification text** — "KidsWatch remote access active" is clear but boring. Does it need to say more?
5. **Charity selection** — Which charity? One fixed charity, or let the parent choose? Simpler: one charity, stated in the app.
