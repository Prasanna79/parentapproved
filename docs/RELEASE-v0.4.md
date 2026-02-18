# KidsWatch v0.4 "The Relay" — Release Notes

**Date:** February 18, 2026
**Milestone:** Parents can manage the TV from anywhere, not just the living room WiFi.

---

## The dashboard left the building

v0.3 gave parents a dashboard — but only on the same WiFi. v0.4 opens a door to the internet. A Cloudflare relay sits between phone and TV: the TV reaches out, the phone reaches in, and the relay holds the connection. Grandma in another city can add a playlist. Dad at work can check what the kids are watching. The TV doesn't know the difference.

```
Signal relayed—
across the miles, a parent
still shapes what they see.
```

---

## What's New

### Remote Access via Cloudflare Relay
- TV opens an outbound WebSocket to `relay.parentapproved.tv` on startup
- Phone sends HTTPS to the relay, relay forwards to TV via WebSocket, TV responds
- Dashboard served from Cloudflare edge — fast load worldwide
- TV's local Ktor routes unchanged — relay is transparent to the server
- **Opt-in:** disabled by default, toggle in Settings > Enable Remote

### ConnectScreen Redesign
- Relay QR code shown as primary when Remote Access is enabled
- QR encodes dashboard URL with auto-fill PIN — scan and you're in
- Local IP shown as secondary for same-WiFi use
- Settings gear (bottom-right) for quick access

### Relay Security (12 controls)
- Path allowlist + method allowlist (GET/POST/DELETE only)
- 10KB request cap, 100KB response cap
- 1 WebSocket per tvId, 5 tvIds per IP
- Phone rate limit: 60 req/min per TV
- Token refresh rate limit: 5/hour per token
- Timing-safe secret comparison
- 90-second heartbeat timeout with automatic disconnect
- Malformed WebSocket frame rejection

### Auth Improvements
- Session TTL extended to 90 days (was 30)
- `POST /api/auth/refresh` — dashboard refreshes token on every page load
- Session persistence across TV app restarts (SharedPreferences)
- **Session limit bumped to 20** (was 5) — room for phones, laptops, grandparents
- PIN reset rotates tv-secret — invalidates all remote access

### Relay Status in Settings
- Live "Relay: Connected / Connecting / Disconnected" with color coding
- Polls every 2 seconds — updates within moments of state change

### Developer Experience
- `kw.sh` — ADB debug helper with 8 subcommands (`pin`, `relay`, `status`, `state`, etc.)
- `relay/scripts/tail.sh` — live-tail relay logs via `wrangler tail`
- 6 structured log points in relay DO (connect, secret, bridge, response, heartbeat, disconnect)
- `shouldAcceptSecret()` extracted as pure testable function
- Bridge integration test — real Ktor server + FakeWebSocket, catches path mismatches

---

## Bug Fixes

- **QR code 404** — QR pointed to `/tv/{tvId}/connect` which didn't exist on relay. Fixed to point to dashboard root `/tv/{tvId}/?pin=...`
- **"Relay: Connecting..." stuck** — Settings screen read relay state once via `remember {}` and never recomposed. Now polls every 2s.
- **Session limit exhausted during testing** — 5 sessions too few for multi-device families. Bumped to 20.

---

## Test Coverage

| Suite | Count | Runner |
|-------|-------|--------|
| TV unit tests | 187 | `./gradlew testDebugUnitTest` |
| Relay tests | 139 | `cd relay && npx vitest run` |
| TV instrumented | 19 | `./gradlew connectedDebugAndroidTest` |
| Intent + HTTP | 15 | `scripts/test-suite.sh` |
| UI tests | 34 | `scripts/ui-test.sh` |
| **Total** | **394** | |

New test files:
- `RelayConnectorTest.kt` (18 tests — connect, backoff, bridge, path mapping)
- `RelayBridgeIntegrationTest.kt` (6 tests — real Ktor server end-to-end)
- `relay/test/security.test.ts` (+5 secret rotation tests)
- `relay/test/integration.test.ts` (28 tests — rate limits, allowlist, bridge)
- `relay/test/protocol.test.ts` (12 tests)
- `relay/test/protocol-contract.test.ts` (19 tests — cross-language JSON fixtures)
- `relay/test/dashboard.test.ts` (20 tests)
- `relay/test/allowlist.test.ts` (24 tests)

---

## Known Issues

- **Now Playing shows video ID, not title** — dashboard Recent Activity and Now Playing show raw video codes instead of human-readable titles
- **Next button appears disabled** in dashboard when single-video playlist is playing (correct behavior, but confusing)
- **D-pad Down from "Refresh" skips to 3rd video** — focus order issue on HomeScreen
- **Overscan on some TVs** — Panasonic shows slight corner clipping in fullscreen playback
- **Dashboard fields not cleared on offline** — stale playlist data visible behind the offline banner
- **Reset PIN doesn't auto-kick dashboard sessions** — sessions stay valid until next API call or page refresh

---

## Verified Hardware
- **Emulator:** TV_API34 (Android 14, arm64)
- **Real device:** Xiaomi Mi Box 4 (MIBOX4, Android 9, API 28) on Panasonic TV
- **Remote dashboard:** Chrome on Android phone (mobile data), Chrome on laptop (same WiFi + remote)
