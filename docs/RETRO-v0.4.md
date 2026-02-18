# KidsWatch v0.4 Retrospective

**Date:** February 18, 2026
**Scope:** Remote relay, Cloudflare Workers + Durable Objects, hardware testing round 2

---

## What Went Well

### The Relay Architecture Held Up
Application-aware relay (not a generic tunnel) was the right call. The TV's Ktor routes don't know about the relay — same `/auth`, `/playlists`, `/playback/*` paths, just proxied through WebSocket. This meant zero changes to existing server code. The relay just maps paths, forwards headers, and bridges responses.

### Protocol Contract Tests Caught Real Mismatches
Shared JSON fixtures parsed by both TypeScript (Vitest) and Kotlin (JUnit) caught serialization mismatches before they became runtime bugs. 19 contract tests ensured both sides agree on message shapes.

### Security Review Before Code
Raj's 12-control security spec (path allowlist, rate limits, payload caps, timing-safe comparison) was designed before the first line of relay code. Every control has a corresponding test. This front-loaded approach avoided the typical "ship then patch" security cycle.

### Bridge Integration Test — The Test We Wish We Had Earlier
`RelayBridgeIntegrationTest` starts a real Ktor Netty server (port=0) and sends relay-format JSON through `FakeWebSocket → RelayConnector → localhost HTTP → Ktor`. This is the test that would have caught the path mismatch bug (`/api/auth` relay path → `/auth` local path) without deploying to hardware. 6 tests, all green.

### Hardware Testing Found 8 Bugs
The Mi Box + phone testing session found issues that no emulator or unit test could: QR code 404, stuck relay status, session limit exhaustion, overscan, focus order. Every real-device session finds bugs. This is not a failure of testing — it's confirmation that real hardware is irreplaceable.

---

## What Didn't Go Well

### QR Code URL Was Wrong From Day One
The QR code pointed to `/tv/{tvId}/connect?secret=...&pin=...` — a path that never existed on the relay Worker. The dashboard lives at `/tv/{tvId}/` and reads `?pin=` from query params. This means the QR code was broken for the entire development cycle and nobody caught it until the first phone test.

**Lesson:** Test the full user flow (scan QR → land on dashboard → auto-login) on real hardware before calling the feature done. Automated tests covered the relay, covered the dashboard, covered the auth — but nobody tested the link between them.

### "Relay: Connecting..." Stayed Stuck
`remember { ServiceLocator.relayConnector.state }` in SettingsScreen captures the value once and never updates. Classic Compose gotcha — `remember` without a changing key is a snapshot, not a subscription. The relay connected fine (dashboard worked) but the UI lied.

**Lesson:** For external mutable state (not a Compose `State`), either expose it as `StateFlow` and collect it, or poll it. `remember {}` alone is not reactive.

### Session Limit Hit During Testing
5 sessions seemed generous in design. In practice: phone browser (1), laptop browser (1), phone incognito for testing (1), another laptop test (1), token refresh creates new token (1) — full. Any new device gets rejected. Grandparents can't connect.

**Lesson:** Session limits should account for the realistic device count of a household, not the developer's mental model. 20 is comfortable for 2 parents + 2 grandparents + a few devices each.

### Debug Intents Silent on API 34 Emulator
`Log.d()` output from DebugReceiver was completely suppressed on the API 34 emulator. Broadcasts were received (confirmed via ActivityManager logs) but `logcat -s KidsWatch-Intent:D` showed nothing. Required falling back to port-forwarded HTTP calls instead.

**Lesson:** Don't rely on `Log.d()` for debug tooling on newer API levels. Consider `Log.i()` or `Log.w()` for debug intents, or add an HTTP endpoint for debug queries.

---

## Learnings

1. **Test the seams, not just the components.** Unit tests proved the relay worked. Unit tests proved the dashboard worked. Unit tests proved the QR code generated a URL. Nobody tested that the URL actually resolved. The bug lived at the seam between components.

2. **`remember {}` is not reactive.** It caches a value, period. For state that changes outside Compose (like `RelayConnector.state`), use `StateFlow.collectAsState()`, `LaunchedEffect` polling, or `snapshotFlow`.

3. **Session limits should be generous.** A family with 2 parents, 2 grandparents, and a mix of phones/tablets/laptops can easily burn through 5 sessions. 90-day TTL makes this worse — tokens accumulate over months.

4. **Relay logging pays off immediately.** The 6 `console.log` points (`[relay] connect:`, `[relay] bridge:`, etc.) + `wrangler tail` gave instant visibility into production relay behavior. Zero-cost when no tail consumer is attached.

5. **`shouldAcceptSecret()` extraction was the right pattern.** Pulling the accept/reject decision out of the DO class into a pure function made it trivially testable. The 5-line function + 5 tests would have caught Bug 3 (secret rotation after disconnect) before it reached hardware.

6. **Deploy relay first, TV APK second.** The relay is backward-compatible by design — it just bridges whatever the TV sends. Deploying the relay before the TV APK means the phone dashboard works as soon as the TV updates. Reverse order means a broken window where the TV connects but the relay doesn't understand it.

7. **ADB helper scripts are worth the 5 minutes.** `kw.sh` with 8 subcommands replaced dozens of copy-pasted `adb shell am broadcast` commands. Should have built it at the start of v0.4, not at the end.

---

## By the Numbers

- **250 → 394 tests** (187 TV unit + 139 relay + 68 other)
- **3 bugs found by bridge integration test** (path mismatch, auth routing, refresh routing)
- **8 bugs found by hardware testing** (QR 404, stuck status, session limit, overscan, focus, video titles, offline stale data, reset-pin session invalidation)
- **1 new codebase** (`relay/` — TypeScript, Cloudflare Workers + Durable Objects)
- **2 scripts** (`kw.sh`, `tail.sh`) — should have existed from day 1
- **1 haiku** — tradition holds

---

## v0.5 Priorities (from this retro + hardware testing)

| Priority | Item | Why |
|----------|------|-----|
| P0 | Video titles in Now Playing + Recent | Dashboard shows raw video IDs — unusable for parents |
| P1 | Dashboard offline state cleanup | Stale data visible behind offline banner is confusing |
| P1 | Reset PIN auto-kicks dashboard | Sessions should invalidate in real-time, not on next API call |
| P2 | Focus order on HomeScreen | D-pad Down from Refresh should go to first video |
| P2 | `Log.d` → `Log.i` for debug intents | Silent on API 34+ emulators |
