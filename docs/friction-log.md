# Friction Log — Moldable Development Tracker

**Rule:** When a pattern appears **3 times**, we build the domain object or view for it.

Each entry captures what question was being answered, how many files had to be read to answer it, and what would have made it instant.

Reference designs (build when friction justifies): [v031-MOLDABLE-DEV-SPEC.md](../v031-MOLDABLE-DEV-SPEC.md)

---

## Template

```
### YYYY-MM-DD — Short description

**Question:** What were we trying to understand?
**Files read:** (list files, ~line count scanned)
**Trace length:** X files, ~Y lines
**Root cause:** What was actually wrong
**What would have helped:** What domain object/view would have made this instant
**Pattern:** (name) | Count: N
```

---

## Entries

### 2026-02-18 — QR code pointed to nonexistent relay path

**Question:** Why does scanning the QR code show "Not found"?
**Files read:** ConnectScreen.kt (~line 78), relay/src/index.ts (~lines 182-328)
**Trace length:** 2 files, ~250 lines
**Root cause:** QR generated URL `/tv/{tvId}/connect?secret=...&pin=...` but relay Worker only serves `/tv/{tvId}/` (dashboard), `/tv/{tvId}/ws`, `/tv/{tvId}/api/*`. No `/connect` route exists.
**What would have helped:** An end-to-end test that resolves the QR code URL against the relay and asserts non-404. Or a shared constant for the dashboard URL pattern used by both ConnectScreen and relay routing.
**Pattern:** seam-between-components | Count: 1

---

### 2026-02-18 — "Relay: Connecting..." stuck in Settings after successful connect

**Question:** Why does Settings show "Connecting..." when the relay is actually connected (dashboard works)?
**Files read:** SettingsScreen.kt (~line 112)
**Trace length:** 1 file, ~10 lines
**Root cause:** `remember { ServiceLocator.relayConnector.state }` captures the value once at composition time. RelayConnector.state is a plain `var`, not a Compose `State` or `StateFlow`, so recomposition never triggers.
**What would have helped:** Either expose `RelayConnector.state` as `StateFlow<RelayConnectionState>` (reactive), or a team convention "never `remember {}` external mutable state without a refresh mechanism."
**Pattern:** compose-remember-not-reactive | Count: 1

---

### 2026-02-18 — Debug intents silent on API 34 emulator

**Question:** Why does `adb logcat -s KidsWatch-Intent:D` show nothing after sending DEBUG_GET_PIN broadcast?
**Files read:** DebugReceiver.kt (~line 72), AppLogger.kt (~line 12)
**Trace length:** 2 files, ~20 lines
**Root cause:** Both `logResult()` and `AppLogger.log()` use `Log.d()` (debug level). API 34 emulator appears to suppress debug-level logs even with explicit tag filter. Broadcasts are received (confirmed via ActivityManager logs) but `Log.d` output is swallowed.
**What would have helped:** Using `Log.i()` or `Log.w()` for debug intent output. Or an HTTP endpoint (`GET /debug/pin`) that doesn't depend on logcat at all.
**Pattern:** logcat-level-suppression | Count: 1

---

### 2026-02-18 — Session limit (5) exhausted during multi-device testing

**Question:** Why can't I log in from my laptop? The relay is connected and the phone dashboard works.
**Files read:** SessionManager.kt (~line 7), AuthRoutes.kt (~line 58)
**Trace length:** 2 files, ~30 lines
**Root cause:** `maxSessions = 5`. Each browser session + each token refresh consumes a slot. Phone + laptop + incognito + refresh cycles = 5 quickly. `createSession()` returns `null` when full, which surfaces as a confusing auth failure.
**What would have helped:** A clearer error message ("session limit reached" vs generic auth failure). Or auto-eviction of oldest session when limit is hit, instead of hard rejection.
**Pattern:** resource-limit-hit-during-testing | Count: 1

---

### 2026-02-18 — Relay path mismatch: `/api/auth` on relay vs `/auth` on TV

**Question:** Why does the phone dashboard get 404 when submitting PIN through the relay, but works fine on local WiFi?
**Files read:** relay/src/relay.ts (~line 270, apiPath forwarding), relay/src/index.ts (~line 286, doUrl.pathname), RelayConnector.kt (~line 139, mapRelayPathToLocal), AuthRoutes.kt (~line 42, route definition)
**Trace length:** 4 files, ~80 lines
**Root cause:** Relay sends `/api/auth` to DO, DO forwards `/api/auth` to TV via WebSocket. But TV's Ktor routes are mounted at `/auth`, `/playlists`, etc. — no `/api` prefix. The path mapping function `mapRelayPathToLocal()` strips `/api` on the TV side, but this was written late and initially missed. Multiple deploy-test-fix cycles on Mi Box to narrow down.
**What would have helped:** The bridge integration test (RelayBridgeIntegrationTest) — starts real Ktor, sends relay-format JSON, would have caught 404 immediately. This test didn't exist until after the bug. Also: relay-side logging would have shown `bridge: POST /api/auth → waiting` followed by a 404 response, making the mismatch obvious.
**Pattern:** path-contract-mismatch | Count: 1

---

### 2026-02-18 — Secret rotation failed after TV disconnect/reconnect

**Question:** Why can't the TV reconnect to the relay after a PIN reset + secret rotation? The old secret was rejected even though the TV sent the new one.
**Files read:** relay/src/relay.ts (~lines 207-219, handleConnect), RelayConfig.kt (~line 34, rotateTvSecret), SettingsScreen.kt (~lines 156-165, reset PIN flow)
**Trace length:** 3 files, ~40 lines
**Root cause:** The DO's `handleConnect` checked `if (!this.tvSecret || !this.authenticated)` to decide whether to accept a new secret. After TV disconnects, `cleanupConnection()` sets `authenticated = false` — but `tvSecret` still holds the old value from storage. The condition `!this.tvSecret` was false (old secret exists), and `!this.authenticated` was true (disconnected). The logic happened to work, but the intent was unclear and fragile. A small refactor could break it.
**What would have helped:** Extracting the decision into `shouldAcceptSecret(storedSecret, authenticated)` as a pure function with explicit test cases. The 5-line function makes the state matrix obvious: no secret → accept, not authenticated → accept, authenticated → validate.
**Pattern:** implicit-state-machine | Count: 1

---

### 2026-02-18 — No relay-side logging; blind debugging on production relay

**Question:** Is the TV actually connecting to the relay? Is the phone's request reaching the DO? Is the DO sending it to the TV?
**Files read:** relay/src/relay.ts (entire file, ~387 lines), wrangler.toml, Cloudflare dashboard
**Trace length:** 1 file read many times, plus Cloudflare dashboard checks
**Root cause:** Zero `console.log` statements in the relay DO. When the phone dashboard showed "TV is offline" but the TV showed "Relay: Connected," there was no way to tell which side was lying. Had to add logging, deploy, reproduce, read `wrangler tail`, fix, deploy again. Multiple round trips.
**What would have helped:** Structured logging from day one. 6 log points at key decisions: connect accept/reject, secret decision, bridge request/response, heartbeat, disconnect. These are zero-cost when no `wrangler tail` consumer is attached. Should be part of the DO template, not an afterthought.
**Pattern:** no-observability | Count: 1

---

### 2026-02-18 — Slow debug cycle: deploy APK → toggle relay → reproduce → read logcat → repeat

**Question:** (meta) Why does every relay bug require 5+ minutes to reproduce?
**Files read:** N/A — this is a workflow friction, not a code friction
**Trace length:** N/A
**Root cause:** The debug loop for relay issues was: (1) edit code, (2) `./gradlew assembleDebug` (10s), (3) `adb install` (5s), (4) restart app, (5) navigate to Settings, (6) enable relay, (7) wait for connection, (8) scan QR on phone, (9) enter PIN, (10) reproduce bug, (11) `adb logcat` to read output. Steps 4-10 take 2+ minutes and must be repeated for every hypothesis.
**What would have helped:** `kw.sh` script (built at end, should have existed from start). Bridge integration test (catches path/auth bugs without deploying). Relay tail script (sees DO decisions without guessing). All three were built retroactively — each would have saved multiple cycles if built upfront.
**Pattern:** slow-feedback-loop | Count: 1

---

### 2026-02-18 — Relay connected but ConnectScreen showed stale QR code after toggle

**Question:** I toggled relay off then on in Settings, came back to ConnectScreen — why is the QR code still showing the old (broken) URL?
**Files read:** ConnectScreen.kt (~lines 56-81, remember blocks for QR generation)
**Trace length:** 1 file, ~25 lines
**Root cause:** ConnectScreen generates QR bitmaps inside `remember { }` keyed on nothing — they're computed once when the composable first enters composition. Navigating away to Settings and back doesn't re-enter composition (backstack preserves state). The QR bitmap is stale.
**What would have helped:** Keying the `remember` on `relayEnabled` (which is read from prefs), or regenerating QR codes in a `LaunchedEffect` triggered by navigation. Same class of bug as the relay status stuck on "Connecting" — `remember {}` without reactivity.
**Pattern:** compose-remember-not-reactive | Count: 2

---

### 2026-02-18 — Variable font renders all text at default thin weight

**Question:** Why is all text on every screen paper-thin and nearly unreadable, even though Theme.kt specifies Bold/SemiBold/Medium weights?
**Files read:** Theme.kt (~lines 13-50, NunitoSans FontFamily definition), res/font/nunito_sans.ttf (verified file exists)
**Trace length:** 1 file, ~40 lines
**Root cause:** Android Compose's `Font(R.font.nunito_sans, weight = FontWeight.Bold)` does NOT send weight axis values to variable TTF fonts. The single variable font file is treated as one weight. All `FontWeight` specifications are silently ignored. Every text style renders at the font's default weight (~400/Regular), which looks thin on TV.
**What would have helped:** A typography test screen showing all text styles side-by-side. One glance would have revealed identical weights before deploying to hardware. Also: documentation — this Compose limitation isn't called out in Android docs.
**Pattern:** compose-font-rendering | Count: 1

---

### 2026-02-18 — Green (#22A559) on white fails readability on TV

**Question:** Why can't I read the green text on the light-background parent screens?
**Files read:** Color.kt (~line 17, ParentAccent), style.css (~lines 21-26, h1/stat colors)
**Trace length:** 2 files, ~10 lines
**Root cause:** #22A559 on #FAFAFA has a WCAG contrast ratio of ~3.2:1 — below AA (4.5:1) for normal text. On a phone held close, it's borderline. On a TV at 6-10 feet, it's unreadable. The green was chosen for buttons (white text ON green = fine) but reused for text (green ON white = bad).
**What would have helped:** A WCAG contrast checker as part of the color definition. Or a convention: "accent colors are for backgrounds and icons, never for text. Use AccentText variant for text."
**Pattern:** contrast-failure | Count: 1

---

### 2026-02-18 — Light palette unusable on TV screens

**Question:** Why do ConnectScreen and SettingsScreen look washed out and uncomfortable on the TV?
**Files read:** ConnectScreen.kt (~line 95, ParentBackground), SettingsScreen.kt (~line 60, ParentBackground)
**Trace length:** 2 files, ~10 lines
**Root cause:** Off-white (#FAFAFA) background on a 55-inch TV in a dim living room is a flashlight in the face. The design spec (napkin) proposed dual palettes — dark for kids, light for parents — without validating on actual hardware. Light themes are standard on phones/laptops (held close, well-lit rooms) but hostile on TVs (far away, dim rooms). Netflix, YouTube, Disney+ are all dark for this reason.
**What would have helped:** A 2-minute mockup on the TV before implementing. Literally: change one screen's background to white, install, look. Would have killed the idea in minutes instead of after full implementation.
**Pattern:** design-assumption-not-validated | Count: 1

---

### 2026-02-18 — Favicon SVG not served by relay or Ktor

**Question:** Why doesn't the favicon appear in the browser tab when viewing the dashboard?
**Files read:** relay/src/index.ts (~lines 30-45, CONTENT_TYPES map, static routes), DashboardRoutes.kt (~lines 35-50, content type mapping)
**Trace length:** 2 files, ~30 lines
**Root cause:** Two independent serving paths, both missing SVG support. (1) Relay Worker had no route for `/favicon.svg` and no `image/svg+xml` in CONTENT_TYPES. (2) Ktor's DashboardRoutes.kt had no `.svg` content type mapping. The favicon file existed in `relay/assets/` but was never reachable via HTTP.
**What would have helped:** An integration test that fetches all referenced assets from the dashboard HTML and asserts 200. Or a checklist: "new asset type? Update BOTH relay and Ktor serving."
**Pattern:** multi-server-asset-gap | Count: 1

---

### 2026-02-18 — Now Playing shows video ID instead of title

**Question:** Why does the Now Playing card show `dQw4w9WgXcQ` instead of the video title?
**Files read:** app.js (~line 182, loadStatus), PlayEventRecorder.kt (currentTitle), ContentSourceRepository.kt (resolveVideo), StatusRoutes.kt (NowPlayingResponse)
**Trace length:** 4 files, ~60 lines
**Root cause:** The title exists in `VideoItem.title` after resolution, but multiple objects carry the same video info — `VideoItem`, `PlayEventEntity`, `NowPlayingResponse`, `StatusResponse` — each with its own `title` field that may or may not be populated. On first play of a single `yt_video` source, timing can cause the title to be empty, falling back to the videoId slug in the JS `np.title || np.videoId` logic.
**What would have helped:** A single `WatchableContent` domain object (from `v031-MOLDABLE-DEV-SPEC.md`) as the source of truth for video display metadata, ensuring title is always resolved before any display path can access it.
**Pattern:** video-metadata-not-flowing | Count: 3 (v0.4 Recent Activity, v0.6 playlist list, v0.6.1 Now Playing) — **3-STRIKE: candidate for WatchableContent domain object**
