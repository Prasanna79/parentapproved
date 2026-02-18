# KidsWatch v4 Council — Napkin Discussion

**Date:** 2026-02-17
**Participants:** Sarah (Parent), Marcus (PM), Dev (Dev Manager), Priya (UX Designer, Android TV + kids), Raj (Security Researcher — joined Round 5)
**Topic:** Remote usage as v4 key upgrade

---

## Shared Design Brief

**Current state:** Parent's phone and TV must be on the same WiFi. Ktor server on TV serves dashboard at `http://<tv-ip>:8080`. Auth is PIN displayed on TV screen + bearer token. All data lives on the TV in Room DB.

**The gap:** "Parent at work wants to add a playlist for tonight" — can't do it today. Remote access is the #1 missing capability.

**Prior thinking:** v0.3 spec noted a relay architecture: `Phone → HTTPS → Relay → WebSocket → TV Ktor`. No implementation exists.

**Constraints:** No Firebase. No cloud accounts for the user. Local-first by design. Mi Box has limited CPU/storage. Kid never authenticates — only parents.

---

## Round 1 — Initial Positions

**Sarah (Parent):**
> My biggest frustration is I can't manage things when I'm not home. I hear about a great educational playlist at work, I want to queue it up for when the kids get home. Right now I have to remember it and do it later when I'm on the same WiFi. That's the #1 thing.
>
> But honestly, I also want to check what they watched today while I'm out. "Did they watch 3 hours of Blippi or did they actually watch the science stuff?" That's the remote stats use case.
>
> What I do NOT want: another account to create, another password to remember, another app to install. I already have too many. If this requires me to set up a server or a cloud account, I'm out.

**Marcus (Product Manager):**
> Let's be precise about what "remote" means. There are three levels:
>
> 1. **Queue management** — add/remove playlists from anywhere (async, doesn't need TV to be awake)
> 2. **Status/stats** — see what's playing now, what was watched today (read-only, needs TV to be on)
> 3. **Live control** — pause, skip, stop from anywhere (real-time, needs TV to be on and playing)
>
> Level 1 is the killer feature. Sarah's "queue a playlist from work" use case. Levels 2 and 3 are nice-to-haves but require the TV to be on, which limits their value.
>
> Here's my concern: if we build a relay server, we've introduced infrastructure. Who runs it? Who pays for it? This is a family side project, not a SaaS. The relay has to be either free-tier hostable or self-hostable with zero ops burden.
>
> I also want to flag: remote access changes the security model fundamentally. Today, being on the same WiFi IS the security boundary. Remove that, and PIN + bearer token over HTTP is not enough.

**Dev (Dev Manager):**
> Let me lay out the technical options I see:
>
> **Option A — Cloud relay (hosted service).** A lightweight WebSocket relay (Ktor or Go or Cloudflare Worker) that the TV connects to outbound, and the phone connects to. Both sides initiate outbound connections, so no port forwarding needed. Pros: works through any NAT/firewall. Cons: introduces a running service, needs a domain, needs HTTPS, costs money (even if tiny), and now we have a data transit point.
>
> **Option B — Peer-to-peer (Tailscale/WireGuard).** Install Tailscale on the Mi Box, parent's phone joins the same tailnet. TV's Ktor server is directly reachable via Tailscale IP. Pros: no relay needed, encrypted, no infrastructure to run. Cons: requires Tailscale app on Mi Box (Android 9 — possible but adds dependency), Tailscale account, and the parent needs to understand VPN concepts.
>
> **Option C — Push-based queueing (Firebase Cloud Messaging or similar).** Parent sends playlist URL to a lightweight cloud endpoint. TV polls or receives push notification to pull it down. Pros: TV doesn't need to be reachable. Cons: introduces cloud dependency (violates local-first), and only covers queue management — not live control or stats.
>
> **Option D — Hybrid: local + async queue.** Keep the current local server for same-WiFi use. Add a minimal cloud "mailbox" (could be a simple key-value store like a pastebin or a Firebase-free alternative like ntfy.sh or a Cloudflare KV) where the parent drops playlist URLs. TV checks the mailbox periodically. Pros: minimal infrastructure, local-first preserved for real-time, remote covers the #1 use case. Cons: two systems to understand, delayed (polling-based).
>
> My strong preference: don't try to make everything remote. Solve the #1 use case (queue from anywhere) with the simplest possible mechanism, keep live control local-only.

**Priya (UX Designer, Android TV + kids expert):**
> From a UX perspective, I want to flag three things:
>
> **1. The TV experience must not change.** Kids don't know or care about remote vs. local. If remote infrastructure goes down, the TV app must work exactly as it does today. No loading spinners waiting for a relay. No "connecting to server" screens. The TV is a local appliance.
>
> **2. The phone experience should be seamless.** Today the parent opens a browser, enters the TV's IP. For remote, what's the entry point? A different URL? The same dashboard with a mode switch? A native app? I'd argue: same dashboard, same URL (via the relay or tunnel), same UI. The parent shouldn't have to think "am I home or remote?"
>
> **3. Onboarding complexity kills adoption.** If remote requires the parent to: create an account → install Tailscale → configure a VPN → enter IPs — that's 4 steps of friction before they can add a playlist. Compare that to the current flow: scan QR code on TV → enter PIN → done. Remote onboarding needs to be comparably simple.
>
> One more thing: the QR code on the TV currently encodes `http://<local-ip>:8080`. For remote, what does the QR code encode? This is a design decision that shapes the whole architecture.

---

## Round 2 — Deep Dive on Options

**Marcus (PM):**
> Dev's Option D (hybrid with async mailbox) is the most interesting to me. Let me pressure-test it:
>
> The parent is at work. They find a playlist. They open... what? A bookmark? An app? Let's say a bookmarked URL like `https://kidswatch.example/my-tv`. They paste the playlist URL. It goes into a mailbox. The TV, when it's on, checks the mailbox every 5 minutes and pulls new playlists. Done.
>
> This covers Sarah's #1 use case. It doesn't cover live control or real-time stats, but those require the TV to be on AND actively used — meaning the parent is likely home or can just use local.
>
> The question is: what IS the mailbox? Options:
> - **ntfy.sh** — free, no account, pub/sub over HTTP. But: no persistence. If TV is off when message is sent, it's lost.
> - **Cloudflare KV / Workers** — free tier is generous. Persistent. But: requires a Cloudflare account to deploy.
> - **A simple JSON file in a git repo / gist** — absurd but zero-infra.
> - **A tiny self-hosted endpoint** — e.g., a Fly.io or Railway free-tier app with SQLite. Cheapest persistent option.
>
> I want Dev's take: what's the absolute minimum viable mailbox?

**Dev:**
> The minimum viable mailbox is literally a key-value store with two operations: `PUT /queue/{tv-id}` (parent pushes a playlist URL) and `GET /queue/{tv-id}` (TV polls and drains). Could be:
>
> - **Cloudflare Workers + KV**: Free tier gives 100k reads/day and 1k writes/day. A family will use maybe 5-10 writes/day. Zero ops. Deploys with `wrangler publish`. The worker is ~30 lines of JS. KV is persistent. This is my top pick for minimum viable.
>
> - **Fly.io with SQLite**: Free tier exists but needs a Dockerfile, health checks, deployment. More moving parts.
>
> - **Self-hosted on a home NAS/Pi**: Zero cost but requires the parent to run infrastructure at home, which defeats the purpose.
>
> For auth on the mailbox: each TV generates a unique `tv-id` (UUID) and a `queue-secret` (random token). The QR code for remote setup encodes `https://kidswatch-relay.workers.dev/connect/{tv-id}?secret={queue-secret}`. Parent scans it, bookmarks it. Subsequent requests include the secret. Not production-grade security, but the threat model is low — the worst an attacker can do is add a playlist to your kid's TV.
>
> But here's what I want to be honest about: even this "simple" option means we now maintain two things — the TV app AND a cloud worker. When the worker's free tier changes, or Cloudflare deprecates KV, we have a problem. For a family side project, I'd want the mailbox to be replaceable — just a URL that accepts PUT/GET with a secret.
>
> One more thing: for the TV to poll a remote endpoint, it needs internet access. The Mi Box is on WiFi, so it has internet. But the HTTP client is currently only NewPipeExtractor (for YouTube). We'd add an OkHttp/Ktor client call to poll the mailbox. Small addition but worth noting.

**Sarah (Parent):**
> I love the mailbox idea. Let me make sure I understand the flow:
>
> 1. One-time setup: I'm home, I scan a QR code on the TV with my phone. It opens a URL. I bookmark it.
> 2. Later, at work: I open the bookmark. I see a simple page with a text field. I paste a YouTube playlist URL. I hit "Add." Done.
> 3. Within 5 minutes, the TV picks it up and the playlist appears.
>
> That's great. Two questions:
> - Can I see what's already queued? Like, did my husband already add something?
> - What if I add something wrong? Can I undo it before the TV picks it up?
>
> Also — I don't want two different dashboards. If I'm home, I use the local dashboard (full control). If I'm remote, I use... the mailbox page? That's confusing. Can it be one thing?

**Priya (UX):**
> Sarah just identified the core UX problem. Two interfaces = confusion. Let me propose a resolution:
>
> **The dashboard is always the same URL.** When at home (same WiFi), it connects directly to the TV — full features (live playback control, real-time stats, playlist management). When remote, it detects the TV is unreachable and gracefully degrades to mailbox mode — playlist queueing only, with a clear indicator: "You're managing remotely. Playlists will sync when the TV is online."
>
> Implementation: the dashboard JS tries to reach the local TV first (fast timeout, ~2s). If it fails, it switches to the mailbox API. Same UI, different backend. The parent never thinks about it.
>
> For the QR code: it encodes both the local address AND the remote mailbox URL. The dashboard JS stores both and tries local first.
>
> **Degraded mode UI:**
> - Playlist add/remove: works (via mailbox)
> - Queue view: works (read from mailbox)
> - Now Playing: shows "TV is offline or unreachable"
> - Playback controls: disabled with tooltip "Available when connected to TV's WiFi"
> - Stats: shows "Connect to TV's WiFi to view watch history"
>
> This is honest — it tells the parent exactly what they can and can't do, and why.

---

## Round 3 — Initial Convergence (Mailbox Approach)

**Marcus (PM):**
> I think we're converging on: v4 = Async Remote Queue + Unified Dashboard. The TV app gains a "mailbox poller." A minimal cloud worker serves as the mailbox. The parent dashboard auto-detects local vs. remote and adapts UI accordingly. Remote mode: playlist add/remove/view only. No live control, no stats. Local mode: everything works as today.

*(This was the initial consensus before Prasanna's challenge.)*

---

## Round 4 — Prasanna's Challenge

**Prasanna (product owner) pushed back:**
> This doesn't fully make sense. If the endpoint is remote, then even when I am local, I won't be able to see the stats. I also want to see what is being watched right now. And in the future we want to have usage + be able to stop or allow viewing with daily time limits, etc.

**Marcus (PM):**
> Prasanna just punched a hole in our design. Two problems:
>
> First — if the parent's entry point is a remote URL (the QR code, the bookmark), then that's their dashboard forever. They won't switch between local and remote. The remote endpoint IS the dashboard. So everything needs to work through it: playlists, stats, now playing, playback control. A "degraded mode" means the product is always degraded.
>
> Second — time limits, usage controls, stop/allow viewing — these are core parental control features. They're not real-time-optional. A parent at work seeing their kid has watched 2 hours and being able to say "that's enough" — that's the product. A mailbox can't do that.
>
> We need a relay. Let's stop avoiding it.

**Dev:**
> Fair. The question isn't "relay or mailbox" anymore. It's: **what kind of relay, and who runs it?**
>
> The relay needs to:
> 1. Accept connections from the TV (outbound WebSocket — works through NAT)
> 2. Accept connections from the parent's phone (HTTPS)
> 3. Forward requests from phone → TV and responses back
> 4. Be always-on (or at least: on when the TV is on)
>
> Architecturally, the TV opens a persistent WebSocket to the relay on startup. The relay holds it. When the parent hits the relay's HTTPS endpoint, the relay forwards the request through the WebSocket to the TV's Ktor server, gets the response, and returns it. The TV's Ktor routes don't change at all — they just get requests from the WebSocket bridge instead of directly from the phone.
>
> ```
> Phone ──HTTPS──► Relay ──WebSocket──► TV Ktor (unchanged routes)
> ```
>
> This means ALL existing features work remotely: playlists, stats, now playing, playback control. The relay is a transparent tunnel.
>
> **Who runs the relay?** My recommendation: **Managed relay service.** We deploy to Cloudflare Workers / Fly.io / Railway. Free tier. We (the developers) run it. It's shared infrastructure — all KidsWatch TVs connect to the same relay. Zero ops for parents. The relay is tiny (~150 lines), stateless, free-tier hostable.

**Sarah (Parent):**
> I don't care who runs the relay. I care that:
> 1. I scan one QR code, one time
> 2. I bookmark one URL
> 3. That URL always works — home, work, anywhere
> 4. I can see what my kids are watching right now
> 5. I can say "that's enough" from wherever I am
> 6. When the TV is off, the dashboard says "TV is off" — not an error page

**Priya (UX):**
> One URL, always works. The relay makes the TV feel like a cloud device, without being one. The parent's mental model is "my TV has a URL." They don't know about relays or WebSockets.
>
> Setup flow: QR code on TV encodes `https://relay.kidswatch.dev/tv/{tv-id}/connect?secret={secret}`. Parent scans, enters PIN, bookmarks. Done.
>
> When TV is offline: Dashboard shows "TV is offline. Last seen 2 hours ago." No cached data in v4, just a clean message.

**Dev:**
> On TV offline: v4 ships with stateless relay (TV offline = "TV offline, try later"). v4.1 adds cached last-known state. The relay interface supports both from day one.
>
> For implementation: TV connects via WebSocket on startup, heartbeat every 30s. Relay holds connection. Phone hits HTTPS endpoints. Relay wraps phone's HTTP request as a WebSocket message, waits for TV's response, returns it.

**Marcus (PM) — Revised v4 proposal:**
> v4 = Full Remote Relay. All features work through relay. TV app barely changes (just add WebSocket connector). Relay is a separate deployable (~150 LoC). If relay is down, TV still works locally. Single QR code, single bookmark, single dashboard.

---

## Round 5 — Security Deep Dive (Raj joins)

**Prasanna's concern:** Can someone use the relay for nefarious purposes — to build an ad-hoc VPN tunnel? How do we prevent that?

**Raj (Security Researcher):**
> Prasanna's instinct is correct. A WebSocket relay that transparently forwards HTTP requests is functionally a tunnel. Let me enumerate the abuse scenarios:
>
> **Abuse 1 — Free proxy/VPN.** An attacker registers a fake "TV" that's actually a proxy server. It connects to the relay via WebSocket. Now anyone who knows the tv-id can send arbitrary HTTP requests through the relay, which forwards them to the fake TV, which proxies them to the internet. Congratulations — you've built a free, anonymous proxy service. The relay operator (you) pays for the bandwidth and takes the legal liability.
>
> **Abuse 2 — Command and control.** Malware on a compromised machine connects to your relay as a "TV." The attacker sends commands via the HTTPS endpoint. The relay is now a C2 channel. It's encrypted (WSS), looks like normal web traffic, and is hosted on a legitimate domain.
>
> **Abuse 3 — Data exfiltration.** Similar to C2 — the "TV" is actually a machine on a corporate network. Someone sends requests through the relay to access internal resources via the WebSocket tunnel.
>
> **Abuse 4 — Resource exhaustion.** Thousands of fake TVs connect, holding WebSocket connections open. The relay runs out of connections/memory on the free tier. Legitimate TVs can't connect.
>
> These aren't hypothetical. Every public relay/tunnel service (ngrok, Cloudflare Tunnel, localtunnel) deals with these problems. They solve them with: identity verification, domain restrictions, payload inspection, and rate limiting.

**Dev:**
> How bad is this really? We're on a free tier with limited resources. Isn't the small scale self-limiting?

**Raj:**
> Scale doesn't protect you. One bad actor using your relay as a proxy for illegal content makes YOU the operator of record. Cloudflare or Fly.io will shut your account. And "it's a small project" isn't a legal defense if your relay is used to access CSAM or exfiltrate data.
>
> Let me propose mitigations in layers:
>
> **Layer 1 — Protocol restriction (most important).** The relay MUST NOT be a generic HTTP proxy. It should only forward requests that match the KidsWatch API contract. Specifically:
>
> - **Allowlisted paths only.** The relay only forwards requests to paths it knows: `/auth`, `/playlists`, `/playback/*`, `/stats/*`, `/status`, `/debug/*`. Anything else → 404 at the relay. This kills the generic proxy use case dead.
> - **Allowlisted methods.** Only GET, POST, DELETE. No CONNECT (which is how HTTP proxies work), no arbitrary methods.
> - **Response validation.** The relay checks that responses from the "TV" are JSON (Content-Type: application/json) or HTML (for the dashboard). Binary blobs, streaming data, or unexpected content types → blocked.
>
> This turns the relay from "transparent tunnel" to "application-aware gateway." It can ONLY forward KidsWatch API calls. It's useless as a VPN.

**Marcus (PM):**
> But doesn't this mean the relay needs to know the API contract? If we add endpoints later, we have to update the relay too?

**Raj:**
> Yes, and that's a feature, not a bug. The relay being aware of the protocol means you explicitly opt in to what's exposed remotely. Maybe you DON'T want `/debug/*` routes accessible remotely. The allowlist forces that decision.
>
> **Layer 2 — Connection limits.**
> - Max 1 WebSocket per tv-id. If a second connection tries to claim the same tv-id, reject it (or disconnect the first — your choice on conflict resolution).
> - Max 5 tv-ids per source IP. No one legitimately runs 50 KidsWatch TVs from one IP.
> - WebSocket idle timeout: if no heartbeat in 90s, disconnect. Prevents connection hoarding.
>
> **Layer 3 — Request rate limiting.**
> - 60 requests/minute per tv-id from the phone side. A parent clicking around the dashboard will never hit this.
> - 10 requests/minute per tv-id from the TV side (for any TV-initiated pushes). Prevents a compromised "TV" from flooding.
>
> **Layer 4 — Payload size limits.**
> - Request body max: 10KB. A playlist URL is ~200 bytes. There's no legitimate reason to send 1MB through the relay.
> - Response body max: 100KB. Stats and playlist data are small. This prevents using the relay to shuttle large files.
>
> **Layer 5 — Registration friction (optional but recommended).**
> - When a TV first connects to the relay, it registers its tv-id. The relay stores it. But you could add a simple step: the relay generates a one-time claim code that must be entered on the TV (or vice versa) to bind the tv-id to the relay. This makes it slightly harder to programmatically spin up thousands of fake TVs.
> - At minimum: require the tv-secret on WebSocket connect. Don't allow unauthenticated WebSocket connections.

**Dev:**
> I like the layered approach. Implementation cost estimate:
>
> - Allowlisted paths: trivial. A list of regex patterns checked before forwarding. ~10 lines.
> - Connection limits: straightforward with an in-memory map. ~20 lines.
> - Rate limiting: token bucket per tv-id. ~30 lines. Or use Cloudflare's built-in rate limiting.
> - Payload size: check Content-Length header before forwarding. ~5 lines.
> - Response validation: check Content-Type on response before returning to phone. ~5 lines.
>
> Total: maybe adds 70 lines to the relay. Still under 250 LoC. Doable.

**Raj:**
> One more concern. The relay forwards auth headers transparently — it CAN see session tokens. If the relay is compromised, someone can intercept tokens and impersonate parents.
>
> For v4, this is acceptable — threat model is low (family project, playlist data is not sensitive). But flag for future: if you ever handle truly sensitive data, you'd want end-to-end encryption between phone and TV (shared key derived from PIN, payloads encrypted client-side). That's v5+ territory.

**Sarah (Parent):**
> Does all this security stuff make it harder for me to set up or use?

**Raj:**
> No. All of this is invisible to you. You still scan a QR code, enter a PIN, bookmark the URL. The protections are all relay-side.

**Priya (UX):**
> Security that's invisible to the user is the only kind that works for a family product. Confirmed: if the relay goes down for any reason, the TV still works locally.

**Dev:**
> Correct. The TV's Ktor server runs regardless. Relay is additive, not a dependency.

### Raj's Security Requirements Summary (v4)

| Control | What | Why |
|---------|------|-----|
| Path allowlist | Only forward known KidsWatch API paths | Prevents generic proxy abuse |
| Method allowlist | GET, POST, DELETE only | Blocks CONNECT/tunnel methods |
| Response type check | Only JSON/HTML responses forwarded | Prevents binary data tunneling |
| Payload size caps | 10KB request, 100KB response | Prevents file transfer abuse |
| 1 WebSocket per tv-id | Reject duplicate connections | Prevents connection multiplication |
| 5 tv-ids per source IP | Cap registrations per IP | Prevents mass fake TV registration |
| Rate limits | 60 req/min phone-side, 10 req/min TV-side | Prevents flooding |
| WebSocket auth | tv-secret required on connect | No anonymous TV connections |
| Idle timeout | 90s without heartbeat → disconnect | Prevents connection hoarding |

**Not needed in v4 (noted for future):**
- End-to-end encryption
- TV identity verification beyond shared secret
- Payload content inspection (beyond Content-Type)
- Abuse reporting / alerting

---

## Round 6 — Build vs. Buy

**Prasanna's question:** Do we use an existing tool for this tunnel or do we need to build it?

### Research Summary

The council evaluated four categories of existing solutions:

**Tunnel services (ngrok, Cloudflare Tunnel, Tailscale, bore, frp):**
- All require running a native binary on the Mi Box (Android 9) — hacky, fragile, unsupported
- Generic tunnels (ngrok, bore) have zero abuse prevention — they ARE the VPN problem Raj warned about
- Cloudflare Tunnel has good security (Access policies) but requires embedding `cloudflared` binary
- Tailscale mesh is secure but requires parents to install Tailscale — UX dealbreaker

**Pub/sub platforms (Ably, PubNub, Pusher):**
- Clean Android SDK integration, no native binaries
- Excellent abuse prevention (no tunnel — TV only processes messages matching your schema)
- BUT: requires rewriting from REST to pub/sub. All `fetch()` calls in `app.js` become channel subscriptions. Request-response becomes correlated message pairs. Significant refactor for a solved problem.

**Serverless WebSocket (Cloudflare Workers + Durable Objects, AWS API GW, Fly.io):**
- TV uses OkHttp WebSocket (already in dependency tree via Ktor)
- Phone keeps using `fetch()` — just change the base URL
- YOU write the relay code — full control over allowlists, rate limits, payload caps
- Minimal architecture change to existing TV app and dashboard

**IoT platforms (AWS IoT Core, MQTT):**
- Massively overengineered for one TV

### Comparison Table

| Solution | Android 9? | Path Allowlist | Cost | Ops | Abuse Prevention | Arch Change |
|----------|-----------|---------------|------|-----|-----------------|-------------|
| ngrok | Hacky | No (free) | Free | Low | Poor | None |
| CF Tunnel | Hacky | Yes (Access) | Free+domain | Medium | Strong | None |
| Tailscale | App needed | No | Free | Medium | Strong (mesh) | Phone needs Tailscale |
| Ably | Yes (SDK) | Yes (schema) | Free | Very Low | Excellent | REST→pub/sub rewrite |
| **CF Workers+DO** | **Yes (OkHttp)** | **Yes (your code)** | **~$5/mo** | **Low** | **Excellent** | **Minimal** |
| Fly.io | Yes (OkHttp) | Yes (your code) | Free | Medium | Excellent | Minimal |

### Council Discussion

**Dev:**
> The answer is clear to me: **build a small relay, don't adopt a tunnel product.**
>
> Here's why. Every off-the-shelf tunnel (ngrok, bore, Cloudflare Tunnel) is designed to expose a generic server to the internet. That's exactly what Raj said NOT to do. The security controls we need (path allowlist, payload limits, response type validation) are application-specific. No tunnel product gives us those out of the box. We'd have to bolt them on, which means writing the same amount of code anyway, but now it's bolted onto someone else's abstraction.
>
> The relay is ~150-200 lines of TypeScript on Cloudflare Workers + Durable Objects. It does exactly three things:
> 1. Holds a WebSocket to the TV
> 2. Accepts HTTPS from the phone
> 3. Forwards between them with allowlisting
>
> That's a weekend of work, not a project.

**Raj (Security):**
> I strongly agree with building over buying here. The entire point of my security requirements is that the relay is **application-aware**. A generic tunnel is application-unaware by design — that's its value proposition and its danger.
>
> When you write the relay yourself, the path allowlist isn't bolted on — it's the core routing logic. "What paths does this relay forward?" is the first line of code, not an afterthought.
>
> I'd add: writing it yourself means you can audit every line. It's 150 lines. That's auditable in an hour. Try auditing ngrok or Cloudflare Tunnel's codebase.

**Marcus (PM):**
> What about Ably? Zero tunnel risk, clean SDK, generous free tier.

**Raj:**
> Ably is technically excellent but it's an architectural change, not a deployment change. You'd rewrite how the phone talks to the TV — from synchronous REST to async pub/sub with correlation IDs. That's real work with real bugs. And you'd be locked into Ably's SDK on both sides. If Ably changes pricing or shuts down, you're rewriting again.
>
> With a custom relay on CF Workers, the phone still uses `fetch()`. The TV still runs Ktor routes. The relay is a thin bridge that can be reimplemented on Fly.io, Railway, or any WebSocket-capable host in a day.

**Sarah (Parent):**
> I don't have opinions on build vs. buy. I have one requirement: whatever you pick, I should never have to interact with it. No "install this app," no "create this account," no "enter this API key." I scan the QR, I enter the PIN, I bookmark the URL. That's it.

**Priya (UX):**
> Sarah's requirement rules out Tailscale (requires app) and makes the build-your-own relay the clear winner. The relay is invisible infrastructure. The parent's experience is:
>
> 1. TV shows QR code → encodes `https://relay.kidswatch.dev/tv/{tv-id}/`
> 2. Parent scans, enters PIN, bookmarks
> 3. All future use: open bookmark, use dashboard
>
> Same flow as today, just a different URL scheme. The relay is as invisible as DNS.

**Dev — Architecture sketch:**
> ```
> Phone (anywhere)                CF Workers Edge              TV (home WiFi)
>      |                               |                            |
>      | HTTPS POST /playlists ------→ |                            |
>      |                               | [path allowlist ✓]         |
>      |                               | [rate limit ✓]             |
>      |                               | [payload size ✓]           |
>      |                               |                            |
>      |                               | WS: {path, method, body} → |
>      |                               |                            | [Ktor processes locally]
>      |                               | ← WS: {status, body}      |
>      |                               |                            |
>      |                               | [response type ✓]          |
>      | ←── 200 JSON response         |                            |
> ```
>
> The TV's Ktor routes don't change. The dashboard's `fetch()` calls don't change (just the base URL). The relay is a separate repo, ~150 lines TypeScript, deployed with `npx wrangler deploy`.
>
> **Implementation pieces:**
> 1. `relay/` — new directory (or separate repo), Cloudflare Worker + Durable Object
> 2. `tv-app/` — add `RelayConnector` class: OkHttp WebSocket, heartbeat, reconnect
> 3. `tv-app/` — `ConnectScreen` QR code encodes relay URL
> 4. `tv-app/assets/app.js` — base URL changes to relay URL
>
> **Fallback**: if we ever want to leave Cloudflare, rewrite the relay as a 200-line Node.js server on Fly.io. The TV and phone code don't change — they just point to a different relay URL.

### Council Conclusion: Build It

**Build a small, purpose-built, application-aware relay on Cloudflare Workers + Durable Objects.**

Reasons:
1. Off-the-shelf tunnels are generic — they ARE the security problem
2. The relay is too small (~150 LoC) to justify adopting a framework or platform
3. Full control over security (path allowlist is core, not bolted on)
4. Minimal change to existing TV app and dashboard (base URL swap)
5. Auditable in an hour
6. Portable — relay logic reimplementable on any WebSocket-capable platform
7. ~$5/month (CF Workers paid plan for Durable Objects)
8. OkHttp WebSocket works on Android 9 (already in dependency tree)

What NOT to use:
- Tunnel products (ngrok, bore, CF Tunnel) — generic tunnels = abuse vector
- Pub/sub platforms (Ably, PubNub) — unnecessary REST→pub/sub rewrite
- IoT platforms (AWS IoT) — massive overengineering
- VPN solutions (Tailscale) — UX burden on parents

---

## Round 7 — Open Questions Deep Dive

### Debug Routes via Relay

**Decision: Local-only by default. Opt-in remote flag as fast-follow.**

- `/debug/*` routes NOT in the relay allowlist for v4
- If friction log shows we need remote debug access, add a `debug_enabled` flag: TV sends it in WebSocket heartbeat, relay checks before forwarding `/debug/*`
- Enable/disable via debug intent (`DEBUG_ENABLE_REMOTE_DEBUG`), resets on app restart
- Textbook moldable dev moment — don't build until friction proves the need

### Dashboard Serving

**Decision: Relay serves dashboard. TV only handles API calls.**

- Dashboard assets (HTML/JS/CSS) deployed with the relay Worker
- Phone loads dashboard from Cloudflare edge (fast, cached, globally)
- Dashboard JS makes API calls to same origin → Worker forwards to TV via WebSocket
- Route split at the relay:
  - `GET /tv/{tvId}/` → serve dashboard HTML (from Worker, static)
  - `GET /tv/{tvId}/app.js` → serve dashboard JS (from Worker, static)
  - `GET /tv/{tvId}/style.css` → serve dashboard CSS (from Worker, static)
  - `POST /tv/{tvId}/api/playlists` → forward to TV via WebSocket
  - `GET /tv/{tvId}/api/status` → forward to TV via WebSocket
  - etc.

**Benefits:**
- Page loads in milliseconds (Cloudflare edge), even if TV is slow/offline
- TV offline → page loads instantly, shows "TV is offline"
- Dashboard updates without TV app update (deploy Worker, no APK)
- TV only handles API traffic, not static asset serving

**Versioning:** Dashboard JS calls `GET /api/status` on load, checks TV API version. Mismatch → "Your TV app needs updating."

### WebSocket Reliability on Mi Box

**Decision: Build robust RelayConnector, test on real hardware early.**

- Exponential backoff reconnection (1s → 2s → 4s → 8s → cap 60s)
- Re-authenticate with tv-secret on every reconnect
- Listen for WiFi state change broadcast → immediate reconnect attempt
- Runs inside existing Ktor foreground service (survives Android Doze)
- **Moldable dev candidate:** when WebSocket reliability issues hit 3 times in friction log, build `ConnectionLog` domain object

### Conflict Resolution

**Decision: New connection replaces old.**

- When a WebSocket connects with an already-connected tv-id, close the old connection, use the new one
- Matches physical reality — there's only one TV
- Re-verify tv-secret on new connection

### TV Offline Caching

**Decision: Defer to v4.1.**

- v4: TV offline → "TV is offline" (no cached data)
- v4.1: relay caches recent GET responses, serves stale data with "last updated" timestamp
- v4 relay design should not preclude caching (don't make architectural decisions that block it)

### Daily Time Limits

**Decision: Defer to v4.2. Architecture supports it already.**

- Parent POSTs time limit config through relay → TV enforces locally
- No special relay logic needed — just another API path in the allowlist
- Domain object (`WatchBudget`) built when the feature is built

### Domain Name

**Decision: `workers.dev` for dev, buy domain for shipping.**

- Dev/testing: `kidswatch-relay.workers.dev` (free, immediate)
- Production: `kidswatch.dev` (~$12/year, HSTS preloaded) or similar
- Don't block v4 on a domain purchase

### Which Questions Benefit from Moldable Development?

**Build now (predictable, design is clear):**
- Relay path allowlist — static list, known paths
- Rate limiting — standard pattern
- Payload size limits — number check
- Dashboard serving from relay — standard SPA pattern
- tv-id and tv-secret generation — straightforward

**Friction-log candidates (build when pain proves it, after 3 strikes):**

| Candidate | Trigger | Domain Object |
|-----------|---------|---------------|
| WebSocket lifecycle debugging | "Why is TV showing offline?" × 3 | `ConnectionLog` — connect, disconnect, reconnect, heartbeat timeout with timestamps and reasons |
| Request forwarding failures | "Dashboard spun forever" × 3 | `RelayRequest` — full request lifecycle: received, forwarded, responded, returned, with error states (TV_OFFLINE, TV_TIMEOUT, RATE_LIMITED, PATH_BLOCKED) |
| Auth flow through relay | PIN entry broken via relay × 3 | `RemoteAuthAttempt` — relay forwarded? TV received? Token returned? |
| Dashboard version mismatch | Weird UI bugs from version skew × 3 | Version handshake log |
| Stale data confusion (v4.1) | "Why am I seeing old data?" × 3 | `CacheEntry` with staleness tracking |
| Abuse detection | Suspicious relay traffic patterns × 3 | `AbuseSignal` — source IP, request rate, path distribution, payload sizes |
| Connection state UX | Users confused by connection states × 3 | Refined state machine with real lifecycle data |

**Principle:** The relay is new infrastructure. We don't know where the friction will be. Ship with structured logging, use the friction log, build domain objects when patterns emerge.

---

## Decisions Summary (v4)

| Question | Decision |
|----------|----------|
| Build or buy relay? | Build (~150-200 LoC TypeScript, CF Workers + Durable Objects) |
| Debug routes remote? | No (local-only). Opt-in flag as fast-follow if friction proves need |
| Dashboard serving? | Relay serves static assets. TV handles API only |
| WebSocket reliability? | Robust reconnection + test on Mi Box early |
| Duplicate connections? | New replaces old |
| TV offline? | "TV is offline" in v4. Cached state in v4.1 |
| Time limits? | v4.2. Relay architecture supports it (just another API path) |
| Domain? | `workers.dev` for dev, buy domain later |
| Moldable dev? | Friction-log approach. 7 candidates identified. Build at 3 strikes |
| Security model? | Application-aware gateway with Raj's 9-control table |

## Round 8 — Local Fallback, Testing & CI/CD

### Local Fallback

**Decision: No automatic local fallback in the dashboard.**

- Relay URL is the only parent-facing entry point
- One code path = one security model = easier to test and audit
- Latency through relay (~50-100ms round trip) is imperceptible for dashboard actions
- Local Ktor server stays running for:
  - Processing requests forwarded from relay via WebSocket
  - Developer debug access (`http://<tv-ip>:8080/debug/*`)
  - Emergency fallback if relay service is permanently discontinued

### Testing Strategy — 100% TDD

**Test Pyramid:**

```
         /  1 E2E smoke test (real Worker, real HTTP)       \
        /   ~10 contract tests (protocol format)             \
       /    ~15-20 dashboard JS tests (jsdom + Vitest)         \
      /     ~25-30 TV RelayConnector unit tests (JUnit)          \
     /      ~60 relay unit + security tests (Vitest)               \
    /       ~105+ existing TV app unit tests (unchanged)             \
   /_______________________________________________________________\
```

**Layer 1 — Relay unit tests (TypeScript, Vitest):**

Path allowlist:
- forwards known API paths (GET/POST/DELETE /api/playlists, /api/status, etc.)
- rejects unknown paths → 404
- rejects disallowed methods (CONNECT, PUT) → 405

Rate limiting:
- allows 60 req/min per tv-id from phone
- rejects excess → 429
- resets after window
- isolated between tv-ids

Payload limits:
- accepts under-limit request (10KB) and response (100KB)
- rejects over-limit → 413 (request) / 502 (response)

Response type validation:
- forwards application/json and text/html
- rejects application/octet-stream → 502

WebSocket management:
- accepts TV connection with valid tv-secret
- rejects wrong tv-secret
- replaces old connection on reconnect
- returns 503 when TV not connected
- disconnects after 90s heartbeat timeout

Connection limits:
- 1 WebSocket per tv-id
- max 5 tv-ids per source IP

Auth forwarding:
- forwards Authorization header transparently
- forwards TV's 401 back to phone
- does not cache/log tokens

Static asset serving:
- serves dashboard HTML/JS/CSS with correct Content-Type
- static assets don't require auth

Security-specific (Raj):
- cannot proxy to external URLs
- rejects invalid WebSocket frames
- timing-safe tv-secret comparison
- oversized WebSocket frames rejected
- malformed JSON handled gracefully

**Target: ~60 relay tests**

**Layer 2 — TV RelayConnector unit tests (Kotlin, JUnit):**

Connection lifecycle:
- connects with tv-id and tv-secret
- heartbeat every 30s
- exponential backoff reconnection (1s → 2s → 4s → 8s → cap 60s)
- re-authenticates on reconnect
- stops reconnecting on explicit disconnect
- resets backoff after success
- WiFi state change triggers reconnect

Request bridging:
- converts WebSocket message to local Ktor HTTP request
- sends Ktor response back as WebSocket message
- handles concurrent requests
- times out after 10s if Ktor doesn't respond
- returns error response on Ktor exception

Config:
- generates tv-id (UUID) on first launch
- generates tv-secret (256-bit random) on first launch
- persists in SharedPreferences
- rotates tv-secret on PIN reset

**Testing approach:** FakeRelaySocket (mock WebSocket), injectable clock for backoff, ServiceLocator.initForTest()

**Target: ~25-30 TV tests**

**Layer 3 — Protocol contract tests:**

Shared WebSocket message format:
```json
// Request: Phone → Relay → TV
{
  "id": "req-123",
  "method": "GET",
  "path": "/api/playlists",
  "headers": {"Authorization": "Bearer xyz"},
  "body": null
}

// Response: TV → Relay → Phone
{
  "id": "req-123",
  "status": 200,
  "headers": {"Content-Type": "application/json"},
  "body": "{\"playlists\": [...]}"
}
```

Relay-side tests: "given this HTTP request, I send this WS frame" and "given this WS frame, I return this HTTP response"
TV-side tests: "given this WS frame, I make this local HTTP request" and "given this HTTP response, I send this WS frame"

Both sides test same message format. Drift → test failure.

**Target: ~10 contract tests (split across both suites)**

**Layer 4 — Dashboard JS tests (Vitest + jsdom):**

- Shows "TV offline" when API returns 503
- Shows "Now Playing: X" when API returns playing status
- Shows playlists correctly
- Handles auth token storage/retrieval
- Version check on load

**Target: ~15-20 tests**

**Layer 5 — E2E smoke test:**

- Deploy relay to staging Worker
- Script sends real HTTPS requests through staging relay
- Verifies: allowlisted path works, blocked path returns 404, oversized payload returns 413
- Runs in CI after all unit tests pass

**Target: 1 comprehensive smoke test script**

### CI/CD Pipeline

**Monorepo structure:**
```
KidsWatch/
├── tv-app/           # Android TV app (existing)
├── relay/            # Cloudflare Worker (new)
│   ├── src/
│   │   ├── index.ts          # Worker entry point
│   │   ├── relay.ts          # Durable Object (WebSocket handler)
│   │   ├── allowlist.ts      # Path/method/content-type rules
│   │   ├── ratelimit.ts      # Rate limiting logic
│   │   └── protocol.ts       # Message format types
│   ├── test/
│   │   ├── allowlist.test.ts
│   │   ├── ratelimit.test.ts
│   │   ├── relay.test.ts
│   │   ├── security.test.ts
│   │   ├── dashboard.test.ts
│   │   └── protocol.test.ts
│   ├── assets/               # Dashboard HTML/JS/CSS
│   │   ├── index.html
│   │   ├── app.js
│   │   └── style.css
│   ├── wrangler.toml
│   ├── package.json
│   ├── tsconfig.json
│   └── vitest.config.ts
├── docs/
└── CLAUDE.md
```

**Relay CI:**
```
on: push to relay/**
1. npm ci
2. eslint
3. tsc --noEmit
4. vitest run (unit + security + dashboard + contract tests)
5. wrangler deploy --env staging
6. E2E smoke test against staging
7. wrangler deploy --env production (main branch only, all tests pass)
```

**TV CI:**
```
on: push to tv-app/**
1. ./gradlew testDebugUnitTest (existing + new RelayConnector tests)
2. ./gradlew assembleDebug
3. Instrumented tests (if emulator available)
```

**Protocol version check:** Both `relay/package.json` and `tv-app/build.gradle.kts` reference a PROTOCOL_VERSION. CI fails if they don't match on PRs that touch both.

**Deployment order:** Relay first (backward compatible). TV APK second (sideload). Relay supports current + previous protocol version during rollout.

**Environments:**
- Staging: separate Worker + Durable Object namespace. Debug APKs point here.
- Production: separate Worker. Mi Box production APK points here. Never mix.

### Test Count Summary

| Suite | Count | Runner |
|-------|-------|--------|
| Relay unit tests | ~50 | Vitest |
| Relay security tests | ~10 | Vitest |
| Dashboard JS tests | ~15-20 | Vitest + jsdom |
| Protocol contract tests | ~10 | Vitest + JUnit |
| TV RelayConnector tests | ~25-30 | gradlew testDebugUnitTest |
| Existing TV unit tests | 105+ | gradlew testDebugUnitTest |
| E2E smoke test | 1 | curl/node script |
| **New tests** | **~110-120** | |
| **Total all** | **~215-225** | |

---

## Updated Decisions Summary (v4)

| Question | Decision |
|----------|----------|
| Build or buy relay? | Build (~200 LoC TypeScript, CF Workers + Durable Objects) |
| Debug routes remote? | No (local-only). Opt-in flag as fast-follow if friction proves need |
| Dashboard serving? | Relay serves static assets. TV handles API only |
| Local fallback? | No auto-fallback. Relay URL is only parent entry point. Local Ktor for debug + relay processing |
| WebSocket reliability? | Robust reconnection + test on Mi Box early. Moldable dev candidate |
| Duplicate connections? | New replaces old |
| TV offline? | "TV is offline" in v4. Cached state in v4.1 |
| Time limits? | v4.2. Relay architecture supports it (just another API path) |
| Domain? | `workers.dev` for dev, buy domain later |
| Moldable dev? | Friction-log approach. 7 candidates identified. Build at 3 strikes |
| Security model? | Application-aware gateway with Raj's 9-control table |
| Testing? | 100% TDD. ~110-120 new tests. Protocol contract tests. E2E smoke |
| CI/CD? | Monorepo. Path-based triggers. Staging + production Workers. Protocol version check |
| Repo structure? | Monorepo: `tv-app/` + `relay/` + `docs/` |

## Round 9 — Platform Decision & Remaining Questions

### Platform Decision: Cloudflare Workers + Durable Objects

**Decisive factor: MCP integration.**

Cloudflare has the most mature MCP ecosystem (`workers-mcp`, `mcp-server-cloudflare`). Claude Code can deploy Workers, view logs, manage KV, and debug Durable Objects directly — enabling a tight TDD loop without context-switching to a deploy dashboard.

| Platform | MCP Maturity | Deploy via MCP? | Cost |
|----------|-------------|----------------|------|
| **Cloudflare Workers** | Production-ready (~600 stars) | Yes | $5/mo |
| Fly.io | Experimental | Yes | Free |
| Vercel | Production-ready | Read-only | Free |
| AWS Lambda | Production-ready | Yes | Free |

$5/month for Durable Objects. Compute negligible at family scale.

### Remaining Questions Resolved

**tv-id rotation:** Permanent. Only tv-secret rotates (on PIN reset). tv-id changes only on factory reset.

**Relay monitoring:** Cloudflare dashboard + MCP. No additional tooling. Moldable dev candidate if monitoring friction hits 3 strikes.

**Dashboard asset bundling:** Workers Sites (KV-backed). Standard, edge-cached, editable HTML/JS/CSS.

**Durable Object cold starts:** Not an issue. TV WebSocket + heartbeat keeps DO warm. TV off → DO wakes in ~100ms, returns "TV offline." Imperceptible.

**Token expiry via relay:** New `POST /api/auth/refresh` endpoint. Dashboard calls on each load. Parent only needs PIN for initial pairing. Rate-limited (5/hour). Inactive >30 days → re-pair at TV.

**Dashboard tvId:** Client-side URL parsing (`window.location.pathname.split('/')[2]`). Assets stay truly static.

---

## Final Council Status

**All open questions resolved.** Full spec written to `docs/V04-REMOTE-RELAY-SPEC.md`.

### Participants
- **Sarah (Parent):** One URL, one bookmark, works everywhere. Scan QR, enter PIN, done.
- **Marcus (PM):** Minimal scope — relay is a transparent bridge, not a product. ~$6/month total cost.
- **Dev (Dev Manager):** ~200 LoC relay, ~100 LoC TV connector. 110-120 new tests. Monorepo CI/CD.
- **Priya (UX):** Dashboard served from edge, instant loads. TV offline handled gracefully. No local fallback confusion.
- **Raj (Security):** Application-aware gateway with 12 security controls. Not a generic tunnel. Abuse-resistant by design.

### Key Decisions Log
1. Build the relay, don't buy a tunnel product
2. Cloudflare Workers + Durable Objects (MCP integration)
3. Dashboard served from relay edge, not from TV
4. No local fallback in dashboard (one URL, one code path)
5. Debug routes local-only (opt-in remote as fast-follow)
6. Token refresh for remote session continuity
7. 100% TDD, ~215-225 total tests
8. Monorepo with path-based CI
9. Moldable dev: 7 friction-log candidates, build at 3 strikes
10. $5/mo Cloudflare + domain later

---

## Round 10 — Testing Refinement, UX Polish, Process Flows

### Cloudflare Testing: Vitest + Miniflare

From the official docs: use **Vitest + `@cloudflare/vitest-pool-workers`** with Miniflare (CF's local runtime). Durable Objects, KV, WebSocket all run locally in-process — no mocking needed. This is better than our original plan of faking WebSocket/DO.

```ts
// vitest.config.ts
export default defineConfig({
  test: {
    pool: "@cloudflare/vitest-pool-workers",
    poolOptions: {
      workers: {
        main: "./src/index.ts",
        miniflare: { durableObjects: { RELAY: "RelayDurableObject" } }
      }
    }
  }
});
```

**Tracetest + OpenTelemetry:** Interesting for production trace-based testing but overkill for v4. Moldable dev candidate — add if relay debugging friction hits 3 strikes.

### QR Code with Embedded PIN

**Decision: PIN embedded in QR code. Scanning = instant auth.**

QR encodes: `https://relay.kidswatch.dev/tv/{tvId}/connect?secret={tvSecret}&pin={pin}`

Dashboard auto-submits PIN from query param. No typing. QR refreshes whenever PIN changes. PIN is single-use — old QR becomes invalid after successful auth.

Security: both QR code and PIN already require physical presence (seeing the TV screen). Combining them doesn't weaken security. Approved by Raj.

### Debug QR Code (Local IP)

**Decision: Debug QR code under settings gear on ConnectScreen.**

Encodes: `http://<tv-ip>:8080/debug/`. Scan → opens local debug dashboard directly. Developer-facing, not parent-facing.

### Settings Gear Panel

**Decision: Settings gear icon on ConnectScreen (bottom-right, small).**

Contents:
- TV Info: tv-id, relay status, app version, API version
- Debug QR code (local IP → debug endpoints)
- Debug controls: enable/disable remote debug
- Relay config: URL, connection status, last connected
- Current PIN (text, fallback for manual entry)

No access control on the gear — simple tap. It's a family project, developer-only use.

### Process Flows (all user interactions documented)

**Flow 1 — First-time Setup (Pairing)**
```
1. Install APK on TV → app generates tv-id, tv-secret, PIN
2. TV connects to relay via WebSocket (background)
3. TV shows ConnectScreen with QR code (relay URL + secret + PIN)
4. Parent scans QR → phone opens URL → dashboard auto-submits PIN
5. Relay forwards auth → TV validates → returns bearer token
6. Dashboard stores token in localStorage, strips secret/pin from URL
7. Dashboard shows "Connected! Bookmark this page."
8. Parent bookmarks. TV rotates PIN. Done.
```

**Flow 2 — Daily Use (Remote)**
```
1. Parent opens bookmark → dashboard loads from CF edge (instant)
2. Dashboard refreshes token (POST /api/auth/refresh via relay → TV)
3. Dashboard loads status, playlists, stats
4. Parent adds playlist / pauses / stops / views stats
5. Dashboard polls status every 5s (playing) or 30s (idle)
```

**Flow 3 — TV Offline**
```
1. Dashboard loads from CF edge (instant)
2. API calls return 503 → "TV is offline"
3. Retries every 30s. Resumes when TV reconnects.
```

**Flow 4 — Session Expired (>90 days inactive)**
```
1. Dashboard loads, token refresh fails (401)
2. Shows "Session expired. Go to TV and scan QR to reconnect."
3. Parent re-pairs at TV (same as Flow 1)
```

**Flow 5 — Second Parent Pairing**
```
1. Second parent scans QR on TV → same flow as Flow 1
2. Gets own bearer token. Both parents share same tvId, different tokens.
3. TV is source of truth — both see same state.
```

**Flow 6 — App Update**
```
1. Sideload new APK → TV restarts
2. SharedPreferences preserved (tv-id, tv-secret, session tokens)
3. Room DB preserved (playlists, history)
4. RelayConnector reconnects. Dashboard works immediately. No re-pairing.
```

**Flow 7 — Developer Debug**
```
1. Tap settings gear on ConnectScreen
2. Scan debug QR code (local IP → /debug/)
3. Opens local debug dashboard. No relay needed.
```

**Flow 8 — PIN Reset (nuclear)**
```
1. Trigger PIN reset → new PIN + new tv-secret + clear all sessions
2. RelayConnector disconnects, reconnects with new secret
3. All parents must re-pair (scan new QR)
```

**Flow 8b — Clear Sessions (targeted)**
```
1. Clear sessions only → all tokens invalidated, tv-secret unchanged
2. Relay stays connected. Parents must re-enter PIN (scan QR).
3. Use case: "remove someone's access without full reset"
```

**Flow 9 — Relay Down**
```
1. Relay URL returns HTTP error (502/503)
2. TV unaffected — local Ktor still runs
3. If on same WiFi: parent can use http://<tv-ip>:8080/ directly
4. Relay recovers → TV reconnects automatically → dashboard works
```

### Token Expiry Updated

**90-day TTL** (up from 30). Refresh on every page load. Parent inactive >90 days → re-pair. Vacation-proof.

### Session Token Persistence (critical v4 fix)

Persist session tokens in SharedPreferences (not just in-memory). Survives app restart/update. Without this, every APK update forces all parents to re-pair.

---

## Round 10 — Remote Default & Charityware Specifics

*Post-implementation council session. Relay is deployed, code is written. Two open questions: (1) Should remote be on or off by default? (2) What are the specific charityware donation targets?*

### Remote Access: Opt-In vs Default-On

> **Dev:** The spec says "Enable Remote Access" toggle in Settings, off by default. But now that v4 is built and the relay is deployed, I'm questioning that. The relay is the whole point of v4. If it's buried in Settings as an opt-in checkbox, most users will never find it. The QR code on ConnectScreen already points to the relay URL. Are we shipping a feature and then hiding it?

> **Architect:** Let's think about what "off by default" actually means in the current implementation. Right now, `RelayConnector.connect()` is called in `KidsWatchApp.onCreate()` — it's always on. The QR code on ConnectScreen encodes the relay URL. There IS no toggle. We built it always-on. The spec said opt-in, but the code says always-on. Which one is wrong?

> **Dev:** The code is wrong per the spec. But I think the spec might be wrong per the product intent.

> **Skeptic:** Hold on. Let me argue both sides.
>
> **Case for opt-in (spec's position):**
> - Privacy-first brand promise. "No data leaves your home" is the headline. If remote is on by default, that's not true from moment one.
> - Attack surface. Every connected TV is reachable via the relay. Opt-in means only TVs whose owners explicitly chose remote are exposed.
> - The landing page says "local-first by default, remote only if you choose." Changing this changes the pitch.
>
> **Case for default-on:**
> - The QR code IS the onboarding. If scanning the QR code on the TV gives a URL that doesn't work because remote isn't enabled, that's confusing.
> - Parents don't read Settings. If remote requires finding a toggle, most parents will only use the app on the same WiFi. They'll never discover the killer feature.
> - The relay sees zero user data — it's a dumb pipe with an allowlist. There's no privacy cost to being connected. The TV initiates the outbound WebSocket; no ports are opened.
> - "Local-first" can mean "works without internet" rather than "never connects to internet." The TV still works fully offline if the relay is down.

> **Architect:** The Skeptic's last point is key. "Local-first" doesn't have to mean "air-gapped." It means the TV is the source of truth, the local server works without internet, and the relay is a convenience layer. The TV doesn't depend on the relay — it degrades gracefully.
>
> Here's my proposal: **Remote on by default, with a toggle to disable it in Settings.**
>
> Reasoning:
> 1. The QR code flow only makes sense if remote is on. Otherwise scan → broken URL → confused parent.
> 2. Same-WiFi still works (local IP shown as secondary on ConnectScreen). Parents on the same network can use either path.
> 3. Settings gets a "Remote Access" toggle (on by default) with clear labeling: "Your TV connects to a relay so your dashboard works from anywhere. Turn off to restrict access to your home WiFi only."
> 4. Turning it off calls `relayConnector.disconnect()` and hides the relay QR code. Only shows the local IP QR code.
> 5. The privacy story becomes: "Works from anywhere by default. Want to lock it to your WiFi? One toggle."

> **Dev:** I like that better. It's honest — the app connects to a relay, here's why, here's how to turn it off. Hiding the feature behind opt-in is security theater for a charityware app that doesn't collect data.

> **Skeptic:** Agreed, but one condition: the ConnectScreen should clearly state "Connected via relay at parentapproved.tv" so parents know their TV is reachable remotely. Informed default, not hidden default.

> **Architect:** Yes. And the Settings toggle should show relay status — connected/disconnected/disabled.

**Decision: Remote on by default. Toggle in Settings to disable. ConnectScreen shows relay status. Landing page updated from "opt-in" to "on by default, one toggle to disable."**

Implementation:
- `RelayConnector.connect()` stays in `KidsWatchApp.onCreate()` (current behavior — already correct)
- Settings: add "Remote Access" toggle (SharedPreferences, default true)
- When toggled off: `relayConnector.disconnect()`, ConnectScreen shows only local IP QR
- When toggled on: `relayConnector.connect()`, ConnectScreen shows relay QR as primary
- ConnectScreen subtitle: "Connected via parentapproved.tv" or "Local only (same WiFi)"

---

### Charityware: Specific Donation Targets

> **Dev:** The spec has `[Charity Name]` and `[charity-url]` placeholders everywhere. We need to decide. The ask is:
> - India users → donate to MettaVipassana.org
> - Global users → donate to dhammasukha.in
>
> Both teach Metta (loving-kindness) meditation in the Theravada Buddhist tradition. The idea is: an app about mindful parenting suggests a practice about cultivating kindness. There's a thematic link.

> **Skeptic:** Wait — India users donate to the US-adjacent org, and global users donate to the India org? That's cross-border on purpose?

> **Dev:** Yes. Each organization can use international donations more — they're less likely to get them organically. Indian donors can easily give to a US nonprofit via their website; international donors can support the Indian center. It's a cross-pollination model.

> **Architect:** I like the intentionality. Let's nail down the specifics:
>
> - **India users**: [MettaVipassana.org](https://mettavipassana.org) — they don't have a dedicated /donate page, but the site has donation info.
> - **Global users**: [dhammasukha.in/donation](https://www.dhammasukha.in/donation) — dedicated donation page.
>
> How do we detect "India user"? Options:
> 1. Device locale (`Locale.getDefault().country == "IN"`)
> 2. IP geolocation via relay (adds complexity)
> 3. Don't detect — show both, let the user choose
>
> Option 1 is simplest and good enough. Locale is already on the device, no network call needed.

> **Skeptic:** Option 3 is more honest. "Here are two organizations we admire. Pick whichever resonates." Geolocation or locale detection feels like targeting, which conflicts with the "we don't track you" message.

> **Dev:** Fair point. But showing two options means more UI, more decision fatigue. The parent is on the ConnectScreen or Settings — they want to set up the app, not choose a charity. One clear suggestion is better UX.

> **Architect:** Compromise: **Locale-based default suggestion, with "or see other options" link.** The main message shows one charity based on locale. Below it, smaller text: "or donate to [other org]." Both are always visible, but there's a clear primary suggestion.

> **Skeptic:** That works. And the wording should be warm, not transactional. Not "pay us" but "if this was useful, here's a way to spread kindness."

**Decision: Region-specific suggestion, no detection logic.**

```
KidsWatch is free, forever. If it's been useful to your family,
consider supporting loving-kindness meditation.

India: mettavipassana.org/donate
Worldwide: donate to a Buddhist charity near you.
```

Placement:
- **ConnectScreen**: Below the QR code, small text, always visible
- **Settings**: In an "About" section
- **Landing page**: In the "No business model" section (replace `[charity]` placeholder)
- **GitHub README**: In project description

Wording tone: warm, brief, no guilt. No locale detection — just text with one link and an open suggestion.

---

### Remote Access: Revised — Local by Default (Opt-In Relay)

*Revised after further discussion. Original decision was "on by default." Changed to local-first default to match the project's privacy-first brand. Can revisit based on real user behavior.*

**Decision: Local by default. Relay opt-in via Settings toggle.**

QR code behavior:
- **Default (relay off)**: ConnectScreen shows local IP QR code (`http://<tv-ip>:8080/?pin=...`). Works on same WiFi.
- **Relay enabled**: ConnectScreen shows relay QR code (`https://parentapproved.tv/tv/{tvId}/?pin=...`). Settings page shows local IP QR as secondary.
- Toggle in Settings: "Enable Remote Access" — when turned on, `relayConnector.connect()` is called. When off, `relayConnector.disconnect()`. Persisted in SharedPreferences.

Implementation:
- `RelayConnector.connect()` moves OUT of `KidsWatchApp.onCreate()`
- Settings: new "Enable Remote Access" toggle (default: false)
- When toggled on: connect relay, ConnectScreen switches to relay QR, Settings shows local IP QR
- When toggled off: disconnect relay, ConnectScreen shows local IP QR only
- SharedPreferences key: `relay_enabled` (boolean, default false)

Rationale: Let's see what parents prefer. If most parents turn it on, we can flip the default in a future version. Data-driven, not assumption-driven.

---

### How the Relay Knows About TVs (Architecture Note)

Q: *When the TV app starts, it registers with the relay. The relay has to store a list of TVs and their slugs/pins right? Otherwise how will the QR code take the parent to the right TV?*

A: The relay does NOT maintain a central list of TVs. Here's how it works:

1. **tvId is pre-generated on the TV** — `RelayConfig` creates a UUID on first launch, persists it in SharedPreferences. This is permanent for the life of the app install.

2. **The QR code contains the tvId** — e.g., `https://parentapproved.tv/tv/a1b2c3d4/connect?pin=123456`. The tvId is baked into the URL path.

3. **Durable Objects are addressed by name** — when any request hits `/tv/{tvId}/...`, the Worker calls `env.RELAY.idFromName(tvId)` to get a Durable Object instance. Cloudflare creates a unique DO per tvId automatically. No registration needed.

4. **TV connects via WebSocket** — on connect, the TV sends a `ConnectMessage` with tvId + tvSecret. The DO stores the tvSecret (first-connect-wins) and holds the WebSocket reference in memory.

5. **Parent hits the relay** — browser loads `https://parentapproved.tv/tv/{tvId}/`. The Worker routes API calls to the same DO (by tvId). The DO checks if a TV WebSocket is connected — if yes, bridges the request; if no, returns 503 "TV offline."

```
Parent scans QR → browser hits relay → Worker routes by tvId → DO for that tvId
                                                                    ↕ WebSocket
                                                              TV (connected earlier)
```

So the relay is stateless at the Worker level. Each Durable Object is an isolated room for one TV. No central registry, no list of TVs. The tvId in the URL IS the addressing mechanism. If the TV hasn't connected yet (or relay is disabled), the parent sees "TV offline."

The PIN and session tokens are NOT stored on the relay — they're on the TV. The relay just forwards the auth request to the TV via WebSocket. The relay only stores the tvSecret (for authenticating the TV's WebSocket connection).

---

### Updated Decisions Table

| Question | Decision |
|----------|----------|
| Remote default | Local by default. Opt-in toggle in Settings. |
| QR code (relay off) | Local IP QR on ConnectScreen |
| QR code (relay on) | Relay QR on ConnectScreen, local IP QR in Settings |
| Charityware (India) | mettavipassana.org/donate |
| Charityware (worldwide) | "donate to a Buddhist charity near you" |
| Donation placement | ConnectScreen (subtle), Settings, landing page, README |
| Donation tone | Warm, brief, no guilt. |
| Relay TV registration | None — Durable Objects addressed by tvId in URL, no central list |
