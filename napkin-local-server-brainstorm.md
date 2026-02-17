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

### How the "Charityware Upsell" Works

```
FREE (local only):
  - TV app with embedded server
  - Parent connects on local WiFi
  - QR code + PIN
  - Full playlist management + playback
  - All data stays local

DONATE (remote access):
  - Parent donates to [charity] → gets a "remote code"
  - Enters code in TV's settings → TV connects to relay
  - Parent gets a URL: relay.kidswatch.app/tv/[slug]
  - Works from anywhere, same dashboard
  - TV still the source of truth, relay is just a pipe
  - Analytics viewable remotely too
```

The "remote code" could be as simple as a token you generate and hand out. No payment processing, no account system. Honor-based. Someone could share their code — who cares, it's charityware.

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
      └── Remote code / charityware donation gate
      └── Analytics endpoints

V0.4  Polish based on feedback
      └── Time controls? Multiple TVs via relay? Better UI?
```

V0.2 is shippable and complete on its own. V0.3 is a clean add-on that doesn't change the core. Good separation.

---

## Round 4: Security — "Don't Let Attackers Put Bad Videos on My Kid's TV"

### The Nightmare Scenario

An attacker guesses or brute-forces their way into the TV's management interface and adds a playlist full of inappropriate content. The kid navigates to it and watches. Parent has no idea.

This is the only threat that really matters. Everything else (DoS, data exfiltration) is irrelevant — there's no valuable data and the TV can be rebooted.

### Attack Surfaces by Version

#### V0.2 (Local Only)

**Threat:** Someone on the same WiFi network hits the TV's IP and guesses the PIN.

**Who can attack:** Only someone on your home network. A neighbor on their own WiFi can't reach you. An attacker on the internet can't reach you (no port forwarding).

**Risk level: Very low.** The attacker has to be:
1. On your WiFi (so: in your house, or on a shared network like an apartment building)
2. Scanning for HTTP servers on unusual ports
3. Willing to brute-force a PIN

**Mitigations (cheap, do all of these):**
- 6-digit PIN (1,000,000 combinations, not 10,000)
- Rate limit: 5 wrong attempts → 5 minute lockout, exponential backoff
- PIN displayed only on TV screen (not transmitted, not stored remotely)
- Lockout notification on TV screen: "Someone tried the wrong PIN 5 times"
- Optional: PIN rotation — new PIN every 24h, shown on TV

**Is this enough for local?** Yes. An attacker on your home WiFi who is brute-forcing HTTP services on random ports has bigger problems (and so do you). The PIN is a speed bump, not a fortress, and that's appropriate for the threat model.

#### V0.3 (Relay — Internet-Exposed)

This is where it gets real. Now the TV's management interface is reachable from anywhere on the internet, through the relay.

**Threat model changes completely:**
- Attacker doesn't need to be on your WiFi
- Attacker can script brute-force attempts from anywhere
- The relay slug is the first gate — if guessable, attacker can find TVs to target
- The PIN is the second gate — if brute-forceable, attacker is in

### Relay Security Deep Dive

#### Layer 1: The Slug (Finding the TV)

The slug is in the URL: `relay.kidswatch.app/tv/{slug}`

**Bad: short sequential slugs** (`/tv/001`, `/tv/002`)
- Enumerable. An attacker can scan all of them.

**Bad: short random slugs** (`/tv/abc`, `/tv/x7q`)
- Small keyspace. With 3 chars alphanumeric = 46,656 combinations. Trivially scannable.

**Good: long random slugs** (`/tv/f7k2-m9p4-x3n8`)
- 12 chars alphanumeric = 4.7 × 10^18 combinations. Unguessable.
- But long slugs are hard to type. Parent needs to enter this somewhere.

**Better: TV-generated, parent never types it.**
- The TV connects to the relay with its remote code (see below)
- The relay assigns a slug and tells the TV
- The TV displays the full URL (or QR code) on screen
- The parent scans the QR code — never types the slug
- The slug can be arbitrarily long because humans don't enter it

**Recommendation:**
- Slug = 24-char random base62 string (e.g., `f7k2m9p4x3n8q5w2j8r6t4v1`)
- ~143 bits of entropy. Not guessable. Not enumerable.
- Parent gets it via QR code on the TV screen. Never types it.

#### Layer 2: The PIN (Authenticating the Parent)

Even if an attacker somehow has the slug, they still need the PIN.

**Problem:** Over the internet, brute-force is faster. An attacker can throw thousands of requests at the relay.

**Mitigations:**

| Mitigation | Where | Effect |
|------------|-------|--------|
| Rate limit on relay | Relay server | Max 5 PIN attempts per slug per 15 minutes. Relay enforces, not TV. |
| Rate limit on TV | TV app | Same limits locally, in case relay is bypassed somehow. Defense in depth. |
| 6-digit PIN | TV app | 1M combinations. At 5 per 15 min = 20 per hour = 480 per day. Average crack time: ~1,042 days. |
| Lockout + alert | TV app | After 10 failed attempts, disable remote access until parent re-enables on TV. Show alert on TV screen. |
| PIN rotation | TV app | New PIN every 24h. Resets any brute-force progress. |
| HTTPS only | Relay | PIN transmitted encrypted. No sniffing. |

**With all mitigations: 6-digit PIN + 5/15min rate limit + 24h rotation = effectively uncrackable via brute force.**

#### Layer 3: The Remote Code (Authorizing the TV to Use the Relay)

This is the "donate to charity" token. The TV enters it in settings to connect to the relay.

**What is it?** A pre-generated secret token, like `KW-7f8a9b2c-4d3e-1f6g`. You generate a batch, hand them out to people who donate.

**Security properties:**
- The relay only accepts WebSocket connections from TVs that present a valid remote code
- Each code is single-use (binds to one TV's anonymous UID on first connection)
- If leaked, it can be revoked (relay maintains a small allowlist — the only state it stores)
- Codes don't expire (charityware — don't hassle people)

**What the relay stores (minimal state):**

```
remote_codes:
  KW-7f8a9b2c: { tv_uid: "abc123", slug: "f7k2m9p4...", active: true }
  KW-3e4f5a6b: { tv_uid: null, slug: null, active: true }  // unused, available
```

This is the only state on the relay. A JSON file or SQLite DB. No user data, no playlists, no viewing history.

#### Layer 4: The Relay Itself (What Can It See?)

The relay forwards HTTP traffic. It can see:
- The slug (which TV)
- The URL paths being requested (`/playlists`, `/auth`, `/stats`)
- Request/response bodies (including the PIN on `/auth`)

**Mitigation options:**

**Option A: Trust the relay (pragmatic).** You run it. You're the developer. The relay sees PIN attempts and playlist management requests. This is fine for charityware where the operator and the developer are the same person.

**Option B: End-to-end encryption (overkill).** The parent and TV establish a shared secret (the PIN itself, or a key derived from it), and encrypt the payload before the relay sees it. The relay forwards opaque blobs.

**Recommendation:** Option A for V0.3. The relay operator is you. If the project grows to the point where trust is a concern, revisit. E2E encryption adds significant complexity for a threat that requires compromising your own server.

### Email Collection?

**Do we need to collect email for the remote code?**

**Arguments for:**
- Can notify if their code is compromised/revoked
- Can contact them for critical security updates
- Creates a minimal record of who has access

**Arguments against:**
- It's charityware. Don't collect data you don't need.
- GDPR/privacy overhead for storing emails
- The "donate to charity" model is honor-based anyway
- If you need to reach people, do it through the charity or GitHub

**Recommendation: Don't collect email.** Keep it zero-data. Hand out codes, codes work. If a code is compromised, revoke it. The person can request a new one through whatever channel they used to get the first one (GitHub issue, email to you, etc.).

If you want *optional* email for security notifications: fine. But don't require it.

### Summary: Security by Layer

```
LOCAL (V0.2):
  WiFi boundary         → attacker must be on your network
  6-digit PIN           → rate-limited, lockout after 10 fails
  ✓ Sufficient for home use

REMOTE (V0.3):
  Layer 1: Slug         → 24-char random, unguessable, QR-code delivered
  Layer 2: PIN          → 6-digit, rate-limited at relay (5/15min), rotates daily
  Layer 3: Remote code  → pre-generated, single-use, revocable
  Layer 4: HTTPS        → encrypted transit, relay is trusted (you run it)
  Lockout: 10 fails     → remote access disabled until parent re-enables on TV
  ✓ Strong enough for charityware. Attacker can't find you, can't brute-force you.
```

**The key insight:** The 24-char slug is the real security gate, not the PIN. The slug has 143 bits of entropy and is never typed — it's scanned via QR code. An attacker who doesn't have the slug can't even attempt the PIN. The PIN is a second factor for the (astronomically unlikely) case that someone has the slug.

### What Could Still Go Wrong

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Attacker on home WiFi brute-forces PIN | Very low | Kid sees bad playlist | Rate limit + lockout + TV alert |
| Relay server compromised | Very low | Attacker gets slugs + can MITM | Rotate slugs, revoke codes, bring relay back on clean server |
| Remote code leaked/shared | Low | Unauthorized TV on relay | Codes are single-bind; leaked unused code → revoke |
| Someone scans QR code off the TV screen | Near zero | Needs physical access to your living room | If they're in your house, you have bigger problems |
| YouTube serves harmful content in a "safe" playlist | Medium | Kid sees something bad | Out of scope — this is the parent's curation problem, not a security issue |

---

## Open Questions

1. **Server-only-when-visible vs foreground service?** If the parent always configures while the TV is on the KidsWatch app, we don't need a background service at all. Simpler. But relay (V0.3) definitely needs a foreground service for the persistent WS connection.
2. **QR code libraries on Android TV?** ZXing is standard. Need to verify it works with Compose on TV.
3. **Can we skip the PIN entirely for local network?** If someone is on your WiFi, they're already in your house. Counter-argument: guest WiFi, shared apartment buildings. PIN is cheap insurance — keep it.
4. **Charityware distribution:** GitHub releases with APK? F-Droid? Sideload instructions?
5. **Multiple TVs (local version):** Each TV is its own island. Is that a problem? For charityware, probably not — most families have one TV the kids use. Relay (V0.3) could let the parent see all TVs in one dashboard.
6. **Relay auth:** How does the relay know which TVs are authorized? Simple: TV presents a token (the "remote code") when connecting. Relay just matches parent requests to TV connections by slug. No user accounts on the relay.
7. **Relay latency:** WebSocket round-trip adds ~50-100ms. For a dashboard serving HTML, imperceptible. For video playback, doesn't matter — the TV still streams directly from YouTube.
