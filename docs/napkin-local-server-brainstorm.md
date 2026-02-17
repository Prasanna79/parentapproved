# Napkin: TV App as Local Web Server — Brainstorm

*2026-02-17 — Exploring an alternative to Firebase where the TV app hosts its own HTTP server*

---

## The Idea

Instead of both the parent's phone and the TV app talking to Firebase, the TV app itself runs a lightweight HTTP server. The parent connects directly to it — either on the local network, or via dynamic DNS for remote access. A shared PIN or passphrase secures the connection.

No cloud. No Firebase. No Firestore. The TV *is* the backend.

---

## How It Would Work

```
Parent's Phone                        Android TV App
     |                                      |
     |  1. TV shows: "Go to 192.168.1.42    |
     |     or kidswatch-smith.dyndns.org"    |
     |     PIN: 7294                         |
     |                                      |
     |  2. Parent opens URL in browser       |
     |------ GET / ----------------------->  |  (Ktor/NanoHTTPD serves web UI)
     |<----- HTML dashboard ---------------|  |
     |                                      |
     |  3. Enters PIN                        |
     |------ POST /auth ------------------>  |
     |<----- session cookie ---------------|  |
     |                                      |
     |  4. Pastes playlist URL               |
     |------ POST /playlists -------------->  |  (saved to local Room DB)
     |                                      |
     |  TV resolves via NewPipe, shows videos |
```

---

## Product Manager Perspective

**What I like:**

- **Zero infrastructure cost.** No Firebase bill, no cloud dependency, no terms of service to worry about. The product is fully self-contained.
- **Privacy story is incredible.** "Your data never leaves your home." That's a headline feature for privacy-conscious parents. No accounts, no cloud, no data collection.
- **Simpler mental model.** Parent connects to their TV. That's it. No "sign up for yet another service."
- **No account management headaches.** No password resets, no email verification, no GDPR data deletion requests, no account deactivation flows.

**What worries me:**

- **The setup UX is harder.** "Open 192.168.1.42:8080 in your browser" is not "Go to kidswatch.app." IP addresses scare normal people. Dynamic DNS helps but adds its own setup friction.
- **"Works outside the house" is a real use case.** Parent at work wants to add a playlist for tonight. With Firebase, trivial. With local server, you need dynamic DNS + port forwarding or a tunnel, and now we're in power-user territory.
- **No multi-device sync.** Two TVs = two separate servers, two separate playlist sets, two URLs to remember. Firebase handles this for free.
- **Can't build a business on this.** No accounts means no user base, no analytics, no upgrade path. If this ever needs to be a product (not a hobby project), we'd have to bolt on a cloud layer anyway.
- **Offline parent = blind parent.** With Firebase, the parent can see what their kid watched (play events). With local-only, the parent has to be on the same network (or tunnel in) to check anything.

**My take:** This is compelling for a V0.2 that's just "get it working for my own family" — but it caps the ceiling. If we ever want multiple families, remote access, or analytics, we rebuild.

---

## Parent Perspective

**What I like:**

- **No account.** I don't want another login. I don't want another password. I don't want another service that has my email and my kid's viewing data.
- **It just works on my network.** TV is on, I open my phone, I'm there. No cloud outage can break it.
- **I trust it more.** The data is on my TV. Not on someone's server. Not getting sold. Not getting breached.
- **PIN is simple.** I look at the TV, I type 4 digits. Done. Easier than a pairing code flow that involves a website sign-in.

**What frustrates me:**

- **"What's my TV's IP address?"** I don't know what that means. Can the TV just show a QR code I scan? (Yes — that would help a lot.)
- **I can't do it from work.** I'm at my desk, kid is home, I want to add a playlist I just found. If I'm not on my home WiFi, I can't reach the TV. That's a real limitation.
- **My TV turned off and now the server is gone.** Android TV goes to sleep. Does the server survive? What if the TV rebooted? Do I lose my playlists? (No — Room DB persists. But the server being unreachable is confusing.)
- **Two TVs, two everything.** We have a TV in the living room and one in the kids' room. I have to manage playlists separately for each? That's annoying.

**My take:** For one TV, one house, one parent — this is actually simpler. The moment you add complexity (multiple TVs, remote access, second parent), it falls apart.

---

## Dev Manager Perspective

**What I like:**

- **Massively reduced scope.** No Firebase project setup, no security rules, no auth flows, no Firestore schema, no realtime listeners, no FCM token registration. The TV app is the entire system.
- **One codebase.** The TV app serves HTML/JS for the parent UI. No separate web project, no deployment, no hosting, no domain.
- **Testability is excellent.** Everything is local. No cloud state to set up/tear down. Unit tests can spin up the server, hit endpoints, verify Room DB state.
- **No cloud flakiness.** Firestore listener reconnection, offline persistence sync conflicts, anonymous auth token refresh — all gone.
- **Ktor or NanoHTTPD are proven.** Ktor runs beautifully on Android. NanoHTTPD is a single file. Both handle what we need (serve static HTML, accept JSON POSTs, basic auth).

**What worries me:**

- **Android TV power management will kill us.** Android aggressively kills background services. The HTTP server needs to survive app backgrounding, Doze mode, and TV sleep. We'll need a foreground service with a persistent notification — which looks weird on a TV. Or we accept the server is only up when the app is in the foreground (which might be fine — the parent configures while looking at the TV).
- **Network discovery is non-trivial.** Getting the TV's IP is easy (`WifiManager`). But the parent needs to reach it. Options:
  - **Show IP on screen** — works but ugly. QR code helps.
  - **mDNS/Bonjour** — `kidswatch.local` works on iOS/macOS, flaky on Android phones, doesn't work on Windows without Bonjour installed. Not reliable.
  - **Dynamic DNS** — requires account with a DDNS provider, port forwarding on the router. Way too much setup for a parent.
  - **Tailscale/ZeroTier** — elegant but requires install on both devices. Power-user only.
- **HTTPS is important.** Browsers increasingly block mixed content and warn on HTTP. Self-signed certs cause scary warnings. Let's Encrypt needs a public domain. This is solvable (mkcert, or just accept HTTP on local network) but it's friction.
- **Security model is thinner.** A 4-digit PIN over HTTP on a local network is... fine for a home. But anyone on the same WiFi (guest network, neighbor) could brute-force it. We'd want rate limiting at minimum. For local-only, this is acceptable. For anything internet-facing, it's not.
- **The "embedded web UI" is a hidden project.** Serving HTML from the TV means we're building a web frontend anyway — it's just bundled in the APK as assets. Same React/vanilla JS work, just deployed differently. We don't eliminate the web work, we just move where it's hosted.

**Technical recommendation:**

If we go this route:
- **Ktor** for the embedded server (Kotlin-native, coroutines, clean routing)
- **Static HTML/JS** bundled as Android assets, served by Ktor
- **Room DB** as the sole data store (already planned for cache)
- **QR code** on the pairing screen encoding `http://192.168.x.x:8080?pin=XXXX`
- **Foreground service** to keep the server alive (or accept foreground-only)
- **PIN** — 6 digits, rate-limited to 5 attempts then 60s lockout

---

## Side-by-Side Comparison

| Dimension | Firebase (V0.2 spec) | Local Server |
|-----------|---------------------|--------------|
| Setup complexity (parent) | Sign in with Google, enter code | Open URL (or scan QR), enter PIN |
| Setup complexity (dev) | Firebase project, Auth, Firestore, rules, web hosting | Ktor server, Room DB, static assets |
| Remote access | Works anywhere | Local network only (without tunneling) |
| Multi-TV | Automatic (shared family) | Separate management per TV |
| Privacy | Data in Google Cloud | Data on TV only |
| Cost | Firebase free tier (then pay) | Zero |
| Reliability | Depends on Google Cloud | Depends on TV being on + reachable |
| Offline parent | Can still browse playlists | Can't reach TV |
| Offline TV | Cache + queue events | Just works (it's all local) |
| Play analytics | Firestore → future dashboard | Room DB on TV only |
| Web UI work | Separate project, deploy to hosting | Same work, bundled in APK |
| Power management | N/A | Must keep server alive |
| Security | Firebase Auth + Firestore rules | PIN + rate limiting |
| Path to product | Clear (accounts, analytics, scale) | Dead end without adding cloud |

---

## Hybrid Option Worth Considering

What if we did **both**? Local server for the core loop, Firebase as an optional cloud sync layer.

1. TV always runs the local server. Parent on the same network connects directly. Fast, private, no cloud needed.
2. Optionally, the parent signs in with Google on the TV's web UI. This enables Firebase sync: playlists replicate to Firestore, play events upload, remote access works through the website.
3. The TV's local server is the source of truth. Firebase is a mirror.

This gives privacy-first parents the no-cloud option, and convenience-first parents the full experience. But it's also twice the engineering work.

---

## Round 2: It's Never Commercial

**Key context update:** This will never be a commercial product. At best charityware ("donate to a charity if you like this"). We never want to get in trouble with YouTube. Does that change anything?

### What changes

**Everything the PM said about "path to product" is irrelevant.** No accounts to grow, no user base to monetize, no analytics dashboard to sell. The entire "Firebase sets us up for scale" argument collapses. We're building for one family (maybe a handful of friends).

**The YouTube risk calculus shifts.** With Firebase:
- We host a website (`kidswatch.app`) that's publicly discoverable
- We store YouTube playlist data on Google's own cloud (ironic)
- There's a Google account linked to the service
- If YouTube/Google notices, there's an identity and infrastructure to send a C&D to

With local-only:
- No public website, no domain, no hosted infrastructure
- Data lives on the TV in the living room
- No Google account involved (anonymous NewPipe extraction)
- Nothing to send a C&D to — it's software running on someone's own device
- Same legal posture as NewPipe itself (which has operated for years)

**The privacy argument goes from "nice" to "the whole point."** A charityware project for parents who care about what their kids watch — of course it should keep data local.

**Firebase free tier doesn't matter if zero is an option.** Even free tiers have quotas, require credit cards for Blaze plan upgrades, and tie you to Google's ToS. Zero dependencies > free dependencies.

**The "remote access" use case shrinks dramatically.** For your own family: you're home when the kids are watching. You add playlists while sitting on the couch. "Add a playlist from work" is a nice-to-have that maybe 1 in 10 sessions would use. Not worth an entire cloud layer.

### What stays the same

- The TV app still needs NewPipe + ExoPlayer regardless
- The parent still needs some UI to manage playlists
- The embedded web UI is roughly the same amount of frontend work either way
- Android TV power management challenges exist either way (Firestore listeners also need a service)

### Revised Verdict

**Local server. No question.**

The only argument for Firebase was "path to product." That's gone. What's left:
- Simpler (one moving part instead of three)
- More private (data on your TV, not Google Cloud)
- Lower YouTube risk profile (no public infrastructure)
- Zero cost forever (not "free tier for now")
- Easier to share (APK + instructions, no Firebase project setup per user)

The one real loss — remote playlist management — is solvable later with a Tailscale-style overlay if anyone ever actually needs it.

---

## Effort Estimate: Local Server vs Firebase

### Local Server Version

| Component | Work | Notes |
|-----------|------|-------|
| **TV App: Ktor embedded server** | 2-3 days | Routes: GET / (dashboard), POST /auth, POST /playlists, DELETE /playlists/:id, GET /status. Serve static assets from APK. |
| **TV App: Room DB for playlists** | 1 day | Already planned for video cache. Add playlist table. Single source of truth. |
| **TV App: PIN auth + session** | 0.5 day | Generate PIN on first run, show on screen. Session token in cookie after PIN entry. Rate limiting. |
| **TV App: QR code on pairing screen** | 0.5 day | Encode `http://<ip>:<port>?pin=XXXX`. ZXing library. Parent scans, browser opens, auto-authenticates. |
| **TV App: Network state display** | 0.5 day | Show IP address, QR code, PIN on a "connect" screen. Handle WiFi changes. |
| **Parent Web UI (static HTML/JS/CSS)** | 3-4 days | Bundled in APK as assets. Mobile-first dashboard: playlist list, add/remove, connection status. Vanilla JS or Preact — no build step needed. |
| **TV App: Playlist resolution (NewPipe)** | 2 days | Already proven in feasibility. Wire up PlaylistExtractor, cache results in Room. |
| **TV App: Home screen + playback** | 3-4 days | Leanback rows, thumbnails, ExoPlayer. Same as V0.2 spec. |
| **TV App: Foreground service** | 1 day | Keep Ktor alive. Test Doze/sleep behavior. May not even need this if server only runs while app is visible. |
| **TV App: Debug panel** | 1-2 days | State inspector, log panel, resolve tester. Simpler than V0.2 spec since no Firestore state to inspect. |
| **Total build** | **~15-18 days** | |

| Testing | Time | Notes |
|---------|------|-------|
| Server reachability (WiFi, sleep, reboot) | 2 days | The main risk area. Test on emulator + real device if possible. |
| Parent UI on phone browsers (Chrome, Safari) | 1 day | Mobile layout, PIN flow, add/remove playlists. |
| Playlist resolution + caching | 1 day | 10/50/200 video playlists, offline fallback. |
| Playback end-to-end | 1 day | Stream selection, auto-advance, error states. |
| QR code scanning | 0.5 day | Various phones, camera apps, lighting conditions. |
| Power management / long-running | 1 day | Leave it overnight. Does the server survive? |
| **Total testing** | **~6-7 days** | |

**Grand total: ~21-25 days**

### Firebase Version (current V0.2 spec)

| Component | Work | Notes |
|-----------|------|-------|
| **Firebase project setup** | 1 day | Create project, enable Auth + Firestore + FCM. Configure security rules. Set up web hosting. |
| **Parent website** | 4-5 days | Separate project. Google sign-in, playlist CRUD, pairing flow, debug page. Deploy to Firebase Hosting. Domain setup. |
| **TV App: Firebase Auth (anonymous)** | 1 day | Sign-in, persist UID, token refresh. |
| **TV App: Firestore integration** | 2-3 days | Device registration, pairing listener, playlist listener, play event batching. Offline persistence config. |
| **TV App: Pairing flow** | 1-2 days | Generate code, collision check, display, expiry/regeneration, listen for activation. |
| **TV App: FCM token registration** | 0.5 day | Register token, store in Firestore. Not used until V0.3 but spec says register now. |
| **TV App: Playlist resolution (NewPipe)** | 2 days | Same as local version. |
| **TV App: Home screen + playback** | 3-4 days | Same as local version. |
| **TV App: Play event recording** | 1-2 days | Room DB queue, batch flush to Firestore, retry logic. |
| **TV App: Debug panel** | 2-3 days | More complex — Firestore state, pairing state, event queue, simulate offline. ADB intents. |
| **Firestore security rules testing** | 1 day | Verify all rules work: owner access, TV read, pairing activation, edge cases. |
| **Total build** | **~20-25 days** | |

| Testing | Time | Notes |
|---------|------|-------|
| Google sign-in flow (web) | 1 day | Popup vs redirect, mobile browsers, error states. |
| Pairing end-to-end | 1-2 days | Code generation, expiry, collision, activation, re-pairing. |
| Firestore realtime sync | 1-2 days | Add playlist on web → appears on TV. Remove → disappears. Latency, reconnection. |
| Firestore offline/reconnect | 1 day | Kill network, verify cache, restore, verify sync. |
| Security rules | 1 day | Unauthorized access attempts, cross-family isolation. |
| Play event batching | 1 day | Flush timing, offline queue, retry. |
| Playlist resolution + caching | 1 day | Same as local version. |
| Playback end-to-end | 1 day | Same as local version. |
| Website on mobile browsers | 1 day | Chrome, Safari, responsive layout. |
| **Total testing** | **~9-12 days** | |

**Grand total: ~29-37 days**

### Summary

| | Local Server | Firebase |
|---|---|---|
| **Build** | ~15-18 days | ~20-25 days |
| **Test** | ~6-7 days | ~9-12 days |
| **Total** | **~21-25 days** | **~29-37 days** |
| **Separate projects** | 1 (TV app) | 2 (TV app + website) |
| **Infra to manage** | None | Firebase project, hosting, domain |
| **Risk areas** | Android power management, network discovery | Firestore sync edge cases, auth flows, security rules |
| **Shareable?** | APK + README | APK + "set up your own Firebase project" (or shared project with trust issues) |

Local server is **~30-40% less work**, produces **one artifact** (an APK), and has **zero ongoing maintenance**. For a charityware project that's never going commercial, it's the clear winner.

---

## Round 3: Remote Access via Lightweight Relay

**Context:** We want the local server as the base. But remote access (analytics from work, add a playlist from the bus) would be nice as a future add-on. Could even be the "upsell" — donate to a charity and get remote access. The developer can afford to host a tiny relay backend indefinitely.

### The Problem with Real DynDNS

Traditional dynamic DNS (noip, duckdns, etc.) requires:
1. The TV to register its public IP with a DNS provider
2. Port forwarding on the home router (parent has to log into router admin)
3. The home IP to not be behind CGNAT (many ISPs do this now — your "public IP" is shared)

Step 2 kills it. Asking a parent to configure port forwarding is a non-starter. And CGNAT (#3) makes it impossible for a growing number of households even if they could.

### What Actually Works: A Tiny Relay Server

Instead of exposing the TV directly to the internet, the TV opens an **outbound** connection to a relay. The parent connects to the relay. The relay forwards traffic.

```
Parent's phone            Relay (your VPS)              Android TV
     |                         |                            |
     |                         |<-- persistent connection --|  (TV connects OUT)
     |                         |    (WebSocket or SSE)      |
     |                         |                            |
     |-- HTTPS GET /tv/abc --> |                            |
     |                         |-- forward over WS -------> |
     |                         |<-- response --------------|
     |<-- dashboard HTML ------|                            |
```

**Why this works:**
- TV connects *out* to the relay — no port forwarding, no CGNAT problem
- Parent connects *out* to the relay — normal HTTPS, works from anywhere
- The relay is a dumb pipe — it doesn't store playlists, videos, or user data
- The TV is still the source of truth for everything

### Three Options for the Relay

#### Option A: WebSocket Tunnel (Custom ~200 lines)

The TV opens a WebSocket to `relay.kidswatch.app`. The relay assigns it a slug (e.g., `abc123`). Parent visits `relay.kidswatch.app/tv/abc123`, relay pipes HTTP request/response over the WebSocket.

```
Relay server: ~200 lines of Node.js or Go
  - Accept WS connections from TVs, assign slugs
  - Accept HTTP from parents, match slug, forward over WS
  - PIN verification forwarded to TV (relay never sees the PIN)
  - No state, no database, no disk

TV side: ~100 lines of Kotlin
  - OkHttp WebSocket to relay
  - Receive HTTP requests, route to local Ktor, send response back

Cost: $5/mo VPS (or free on fly.io/render free tier)
```

#### Option B: Cloudflare Tunnel (Zero server)

Cloudflare has a free tunnel product (`cloudflared`). The TV runs a tunnel client that exposes the local Ktor server at a Cloudflare URL. No relay server needed at all.

**Problem:** `cloudflared` is a Go binary. There's no official Android build. You'd have to cross-compile or run it via Termux-style hacks. Fragile on a TV. **Not practical.**

#### Option C: Tailscale/ZeroTier (Zero server, power-user)

Both create a virtual private network. Install on TV + phone, they can see each other anywhere.

**Problem:** Requires installing another app on both devices. Parent has to create a Tailscale account. Power-user territory. Fine for the developer's own family, not distributable as charityware.

### Recommendation: Option A (Custom Relay)

It's the only option that's:
- Transparent to the parent (just a URL)
- No extra software on any device
- Fully under your control
- Cheap enough to run forever

### Charityware Model (Honor System)

```
The app includes remote relay support for everyone.
Settings screen has a "Enable Remote Access" toggle.
Below it: "If you find KidsWatch useful, please donate to [charity]."
That's it. No codes, no gates, no verification. Honor system.
```

No remote codes. No tokens to hand out. No gating. The relay is open to any TV running the app. The parent taps "Enable Remote Access" on the TV, the TV connects to the relay, done.

### Architecture: V0.2 Local → V0.3 Relay Add-on

The beautiful part: **the relay changes nothing about the TV app's core architecture.**

```
V0.2 (local only):
  Phone → WiFi → Ktor server on TV → Room DB

V0.3 (add relay):
  Phone → HTTPS → Relay → WebSocket → Ktor server on TV → Room DB
                    ↑
              (just a pipe)
```

The Ktor server handles the same requests either way. The only new code on the TV is ~100 lines of WebSocket client that tunnels HTTP. The parent UI is identical — it doesn't know or care whether it's hitting the TV directly or via relay.

### Relay Server Effort Estimate

| Component | Work | Notes |
|-----------|------|-------|
| **Relay server (Node.js or Go)** | 2-3 days | WebSocket accept, slug assignment, HTTP forwarding. No database. Stateless. |
| **TV app: WS tunnel client** | 1-2 days | OkHttp WebSocket, receive requests, forward to localhost Ktor, return responses. Reconnection logic. |
| **TV app: Settings UI for relay** | 0.5 day | Enter remote code, toggle relay on/off, show relay URL/status. |
| **HTTPS + domain** | 0.5 day | Let's Encrypt on the VPS. `relay.kidswatch.app` or similar. |
| **Testing** | 2-3 days | Latency, reconnection, concurrent connections, TV sleep/wake with active tunnel. |
| **Total** | **~6-9 days** | On top of the V0.2 local base |

### Hosting Cost

| Option | Cost | Notes |
|--------|------|-------|
| fly.io free tier | $0 | 3 shared VMs, 160GB outbound. Plenty for dozens of families. |
| Hetzner VPS (CX22) | ~$4/mo | 2 vCPU, 4GB RAM, 20TB traffic. Hundreds of families. |
| Oracle Cloud free tier | $0 | ARM VM, 24GB RAM. Overkill but free forever. |
| Your own Pi at home | $0 | If you already have one running. |

For charityware usage levels, the free tiers would last years.

### What About Analytics Remotely?

The TV already has all the play event data in Room. The Ktor server already serves the parent dashboard. Just add a `/stats` endpoint:

- Videos watched today/this week
- Total watch time
- Most-watched playlists
- Last 10 videos played

Same HTML page, served locally or through the relay. No extra backend work. The TV computes the stats from its own Room DB.

---

## Revised Roadmap

```
V0.2  Local-only TV app (21-25 days)
      └── Ktor server + Room DB + parent web UI + NewPipe + ExoPlayer
      └── QR code + PIN pairing
      └── Fully functional, zero infrastructure

V0.3  Remote relay add-on (6-9 days, whenever demand warrants)
      └── Tiny relay server on fly.io or Hetzner
      └── WebSocket tunnel from TV
      └── Honor-system charityware (donate prompt, no gates)
      └── Relay rate-limits by counting 401s per slug
      └── Analytics endpoints

V0.4  Polish based on feedback
      └── Time controls? Multiple TVs via relay? Better UI?
```

V0.2 is shippable and complete on its own. V0.3 is a clean add-on that doesn't change the core. Good separation.

---

## Round 4: Security — "Don't Let Attackers Put Bad Videos on My Kid's TV"

### The Nightmare Scenario

An attacker adds a playlist of inappropriate content to a kid's TV. The kid watches it. Parent has no idea. This is the only threat that matters. Data exfiltration is irrelevant (there's no data). DoS is irrelevant (reboot the TV).

### V0.2 (Local Only) — Easy

**Threat:** Someone on the same WiFi guesses the PIN.

**Who can attack:** Only someone on your home network. Internet attackers can't reach the TV (no port forwarding).

**Mitigations:**
- 6-digit PIN (1M combinations)
- Rate limit: 5 wrong attempts → 5 min lockout, exponential backoff
- Lockout alert on TV screen
- All enforced by the TV's Ktor server — simple, self-contained

**Verdict:** Fine. If someone on your WiFi is brute-forcing random HTTP ports, you have bigger problems.

### V0.3 (Relay) — The Hard Questions

With no remote codes (honor-system charityware), any TV can connect to the relay. This changes the security model.

#### Question 1: "Does the relay have the PIN?"

**No.** The relay never sees or stores the PIN. Here's the flow:

```
Parent                    Relay                      TV (Ktor)
  |                         |                           |
  |-- POST /tv/{slug}/auth  |                           |
  |   body: { pin: "1234" } |                           |
  |                         |-- forward over WS ------> |
  |                         |   (raw HTTP request)      |
  |                         |                           |-- check PIN
  |                         |                           |-- return 200 or 401
  |                         |<-- forward response ----- |
  |<-- 200 + session cookie |                           |
```

The relay forwards the raw request. The TV checks the PIN and responds. The relay doesn't parse the body — it's just bytes.

**But wait:** The relay *could* read the request body (it's forwarding it). It would see `{ pin: "1234" }` in plaintext in the WebSocket message. So the relay *technically* has access to the PIN.

**Is this a problem?** No — you run the relay. It's your server. If your relay is compromised, the attacker has bigger power (they can MITM everything). The PIN-in-transit concern is only relevant if you don't trust your own infrastructure, which is a different problem.

#### Question 2: "How does the relay rate-limit if it's a dumb pipe?"

This is the real design question. Three options:

**Option A: Relay inspects HTTP response status codes**

The relay is *almost* a dumb pipe — but it peeks at one thing: the response status code from the TV.

```
POST /tv/{slug}/auth → forward to TV → TV returns 401 → relay counts a failure
POST /tv/{slug}/auth → forward to TV → TV returns 200 → relay doesn't count it
```

- Relay keeps: `{ slug → { fail_count, last_fail_time } }` in memory
- After 5 failures in 15 min → relay stops forwarding `/auth` requests for that slug
- Returns 429 (Too Many Requests) directly, never reaches the TV
- Successful auth (200) doesn't count toward the limit
- Counter resets after 15 min of no failures

**How it differentiates right from wrong:** It reads the HTTP status code from the TV's response. `200` = correct PIN (don't count). `401` = wrong PIN (count it). The relay never reads the PIN itself — just the success/failure signal the TV already returns.

**This is the right answer.** Minimal intelligence. The relay is still nearly a dumb pipe — it doesn't parse request bodies, doesn't store PINs, doesn't know what a playlist is. It just counts 401s per slug.

**Option B: TV signals the relay out-of-band**

After a failed PIN attempt, the TV sends a separate WebSocket message: `{ "event": "auth_failed" }`. Relay counts these.

- Cleaner separation (relay doesn't inspect HTTP at all)
- But adds a protocol layer (TV has to speak WebSocket control messages *and* HTTP tunneling)
- More complex for no real benefit

**Option C: TV does all rate limiting, relay does nothing**

- Simplest relay (truly a dumb pipe)
- But: attacker can flood the TV with thousands of requests per second
- The TV is an Android device — it can't absorb a brute-force flood the way a server can
- The relay *should* protect the TV from request volume

**Verdict: Option A.** Relay peeks at response codes, counts failures, blocks after threshold. Simple, effective, protects the TV.

#### Question 3: "How does the relay handle open access without remote codes?"

No remote codes means any TV can connect. This raises:

**Abuse risk:** Someone could connect 10,000 fake "TVs" to the relay, exhausting resources.

**Mitigations:**
- Rate limit WebSocket connections per IP (e.g., 3 per hour)
- Each TV must maintain a heartbeat or get disconnected after 60s
- Max concurrent connections on the relay (e.g., 500 — way more than charityware needs)
- If abuse becomes a problem (unlikely for charityware), add a simple proof-of-work or CAPTCHA to the "Enable Remote Access" flow on the TV

**Realistically:** No one is going to DDoS a charityware relay for kids' YouTube playlists. The mitigations above are backstops, not daily necessities.

#### The Full Security Picture (Revised)

```
LOCAL (V0.2):
  WiFi boundary          → attacker must be on your network
  6-digit PIN            → 1M combinations
  Rate limit on TV       → 5 fails / 5 min lockout
  ✓ Sufficient

REMOTE (V0.3):
  Layer 1: Slug          → 24-char random (143 bits), QR-code delivered
                           Unguessable. Attacker can't find any TV to attack.
  Layer 2: Relay gate    → counts 401s per slug, blocks after 5 fails / 15 min
                           Attacker who has slug gets 5 guesses per 15 min.
  Layer 3: TV gate       → also rate-limits locally (defense in depth)
                           10 total fails → disable remote until re-enabled on TV.
  Layer 4: PIN           → 6-digit, rotates daily
                           At 5/15min: avg crack time ~1,042 days. Daily rotation
                           resets progress. Effectively uncrackable.
  Layer 5: HTTPS         → encrypted transit. PIN not sniffable.
  ✓ Sufficient for charityware
```

**The key insight remains:** The 24-char slug is the real security. An attacker who doesn't have the slug can't even find a TV to attempt the PIN against. The slug has 143 bits of entropy and is delivered via QR code on the physical TV screen. The PIN + rate limiting is a belt-and-suspenders second factor.

### What About Abuse of the Relay Itself?

| Concern | Mitigation |
|---------|------------|
| Flood of fake TV connections | Rate limit per IP, heartbeat timeout, max connections |
| Attacker enumerates slugs | 24-char = 10^43 possibilities. Scanning at 1000/sec = heat death of universe |
| Attacker has slug, brute-forces PIN | 5 attempts / 15 min at relay. 10 total fails = remote disabled on TV |
| Relay compromised | Attacker can MITM. Rotate slugs. Rebuild. No persistent data lost (TV has everything) |
| Parent's QR code photo shared/leaked | Slug + PIN exposed. Mitigate: PIN rotation daily. Worst case: parent re-enables remote on TV, gets new slug |

### Round 5: HTTPS vs HTTP — End to End

#### Local (V0.2): Plain HTTP

The TV has an IP address (`192.168.x.x`), not a domain name. No domain = no Let's Encrypt cert. Self-signed cert would be *worse* UX — browsers show a full-page "Your connection is not private" warning that would terrify parents. Plain HTTP just shows a small "Not Secure" badge in the address bar.

```
Phone --HTTP--> TV Ktor (192.168.x.x:8080)
       ^^^^^^^^
       local WiFi only, no cert possible
```

The PIN travels in plaintext on your home WiFi. Same as every other local device: your router admin page, your printer, your NAS, your smart home hub. All HTTP. Sniffing requires already being on the same network.

Modern browser features that require HTTPS (service workers, clipboard API, geolocation) aren't needed for a playlist management dashboard. Forms, fetch, cookies all work fine over HTTP.

#### Remote (V0.3): HTTPS + WSS

```
Phone --HTTPS--> Relay --WSS--> TV
       ^^^^^^           ^^^^
       Let's Encrypt    encrypted WebSocket
       on relay domain  (TV connects outbound)
```

- Parent → Relay: HTTPS with a real cert (Let's Encrypt on `relay.kidswatch.app`)
- TV → Relay: WSS (encrypted WebSocket, TV initiates outbound)
- Inside the TV: relay WS client → localhost Ktor. Unencrypted but it's `127.0.0.1` — never leaves the device.

The relay terminates TLS from the parent, forwards the request over the encrypted WS to the TV. **The relay sees the decrypted request in memory** as it forwards (standard for any reverse proxy / load balancer). You run the relay, so this is acceptable.

#### PIN Exposure Summary

| Path | PIN encrypted in transit? | Who can sniff? |
|------|--------------------------|----------------|
| Local (V0.2) | No (HTTP on WiFi) | Someone on your home network with a packet sniffer |
| Remote (V0.3) | Yes (HTTPS + WSS) | Only the relay operator (you) sees it in memory |

#### Is Local HTTP Acceptable?

Yes. The threat model for local is "someone on your WiFi." If they're sniffing packets, they can also see your router admin password, your printer jobs, your Chromecast traffic, and everything else that runs on HTTP locally. The PIN isn't the weakest link — your network is. And it's your home network.

If this ever becomes a concern (e.g., shared apartment WiFi), the parent can always switch to remote mode where everything is encrypted.

### Email Collection?

**Don't.** No remote codes means no reason to track who's using the relay. Zero data on the relay (just in-memory slug→WebSocket mappings). No GDPR concerns. No breach risk (nothing to breach). If you need to communicate with users: GitHub issues, README, or the charity's channels.

---

## Open Questions

1. **Server-only-when-visible vs foreground service?** If the parent always configures while the TV is on the KidsWatch app, we don't need a background service at all. Simpler. But relay (V0.3) definitely needs a foreground service for the persistent WS connection.
2. **QR code libraries on Android TV?** ZXing is standard. Need to verify it works with Compose on TV.
3. **Can we skip the PIN entirely for local network?** If someone is on your WiFi, they're already in your house. Counter-argument: guest WiFi, shared apartment buildings. PIN is cheap insurance — keep it.
4. **Charityware distribution:** GitHub releases with APK? F-Droid? Sideload instructions?
5. **Multiple TVs (local version):** Each TV is its own island. Is that a problem? For charityware, probably not — most families have one TV the kids use. Relay (V0.3) could let the parent see all TVs in one dashboard.
6. **Relay abuse:** Open relay (no codes) means anyone can connect a TV. Is per-IP rate limiting + heartbeat + max connections enough? For charityware scale, almost certainly yes.
7. **Relay latency:** WebSocket round-trip adds ~50-100ms. For a dashboard serving HTML, imperceptible. For video playback, doesn't matter — the TV still streams directly from YouTube.
