# V0.2.1 + V0.3 Spec Review — Multi-Round Discussion

*PM, Dev Manager, and Parent perspectives on the local server + relay architecture*

---

## Round 1: Initial Reactions

### Product Manager

I like how clean this is compared to the Firebase spec. One APK, zero infrastructure, parent opens a browser. But I have concerns:

1. **The "Refresh" gap.** In V0.2.1, when a parent adds a playlist on their phone, the TV doesn't update instantly. The parent has to tap "Refresh Videos" on the TV or wait for an auto-refresh. The Firebase version had realtime listeners — playlist appeared on TV within seconds of being added on the website. This feels like a regression. The parent is sitting on the couch, adds a playlist on their phone, then has to... get up and press a button on the TV? Or tell their kid to?

2. **No realtime sync isn't about technology, it's about the moment.** "I just added Bluey Season 3, go watch it!" — and the kid sees it immediately. That's a magic moment. "I just added it, now press the refresh button" kills it.

3. **The stats page is thin.** "Today's videos and total time" is a start, but parents will immediately want: "What did they watch this week?" "Which playlists get the most use?" This is fine for V0.2.1, but should be flagged as a V0.3 enhancement.

4. **Server lifecycle — foreground only — is a problem.** The spec says Ktor runs only while the app is in the foreground. So if the kid is watching a video (app in foreground, ExoPlayer fullscreen), can the parent connect? Yes — the app is still in foreground. But what if the TV is sitting at the Android TV home screen? The parent can't connect. They'd have to ask someone to launch the app first. That's awkward.

### Dev Manager

Solid spec. Much tighter scope than the Firebase version. My notes:

1. **Ktor lifecycle is the #1 risk.** The PM is right — foreground-only is limiting. But a foreground service just for Ktor is overkill for V0.2.1. **Compromise: start the foreground service only when remote access is enabled (V0.3).** For V0.2.1, foreground-only is acceptable because the parent is home, looking at the TV, and the app is running.

2. **The refresh problem has a simple fix.** The parent dashboard is already connected to the TV via HTTP. After adding a playlist, the dashboard's JavaScript can fire a request to a new endpoint: `POST /playlists/:id/resolve`. The TV resolves that playlist immediately and the dashboard polls for completion. The TV updates its UI on the next frame. No manual "Refresh" needed — the act of adding a playlist from the dashboard triggers resolution. This doesn't require websockets or push — just a synchronous resolve-on-add.

3. **oEmbed for playlist validation is limited.** oEmbed returns the playlist title but not the video count or whether it's actually a valid playlist ID vs a video URL with a list parameter. We should test edge cases: private playlists, deleted playlists, very long playlists, mix playlists, auto-generated playlists. Some of these will fail at the NewPipe resolution stage, not at oEmbed. That's fine — the parent sees "Playlist no longer available" on the TV — but we should be clear about what validation means.

4. **Session management needs a "keep alive" path.** The spec says sessions expire after 24h. If a parent bookmarks the dashboard URL, they'll have to re-enter the PIN every day. Should the session auto-extend on activity? Or should we use a longer expiry (7 days)?

5. **Test 7 (Ktor on Android TV) is the real go/no-go.** Everything else we've proven or is well-trodden. Ktor on Android is documented but Ktor on *Android TV* with limited resources and power management is not. This test should happen before any other V0.2.1 work.

### Parent

I love this. Way simpler than the previous version where I had to create a Google account, sign in to a website, enter a pairing code... Now I just scan a code and type 4 — sorry, 6 — digits? Sold.

Questions though:

1. **What if I forget the PIN?** I'm on the couch a week later, I want to add a playlist, I can't remember the PIN. Do I have to walk to the TV and look at the settings? Can the TV just show the PIN on screen somewhere visible? (But not visible to the kids, who might share it at school...) Maybe: PIN is visible on the connect screen, and the connect screen is accessible from the settings gear icon?

2. **What happens when my WiFi router assigns a new IP?** The QR code I saved last week doesn't work anymore. Is this going to happen often? I don't know what DHCP leases are and I don't want to. The spec says the connect screen updates — but I have to go look at the TV again. Could the PIN be enough? Like, can I just type `kidswatch.local` instead of an IP address?

3. **"Refresh Videos" is confusing.** If I add a playlist and it doesn't show up, I'm going to think it's broken. The Dev Manager's idea of auto-resolving on add is critical. I shouldn't have to think about "refreshing."

4. **I like that there's no account.** No email, no password, no "we've updated our privacy policy" emails. It's just a TV app. Thank you.

---

## Round 2: PM + Dev Manager Respond to Feedback

### Product Manager

The parent feedback confirms my concerns. Let me revise my positions:

**On refresh:** The Dev Manager's "resolve-on-add" solution is perfect. When the parent adds a playlist from the dashboard, the TV resolves it immediately. The parent sees "Resolving... done! 47 videos added." right in the dashboard. The TV screen updates on the next UI tick. No manual refresh needed for the add flow. The "Refresh Videos" button on the TV becomes "re-resolve all playlists to pick up new videos added to existing playlists on YouTube" — which is a power-user action, not the default flow.

**On the PIN recall problem:** The parent is right. The PIN should be:
- Visible on the connect screen (accessible via Settings → Show Connect Info)
- Also shown briefly when the app launches (a 5-second splash before the home screen?)
- **Not** shown on the home screen itself (kids would see it)

Actually, better: the TV remote has a button the parent can press. Long-press the settings gear → shows PIN for 10 seconds → fades. Physical access to the remote = you're the parent.

**On IP address changes:** This is real. DHCP leases on home routers are typically 24h-7 days. When the IP changes, the old QR code/URL stops working. The parent has to re-scan. For V0.2.1 this is acceptable friction (the TV always shows the current IP). For V0.3 with the relay, this problem disappears entirely — the relay URL is stable.

**On stats:** V0.2.1 should ship with: today's videos (titles + durations), total watch time today, and last 10 videos. Enough for "what did they watch while I was cooking dinner." Weekly/monthly analytics can come later.

### Dev Manager

Responding to the PM and parent:

**Resolve-on-add — implementing it:** Easy. The `POST /playlists` endpoint already saves to Room. After saving, kick off `PlaylistExtractor` on an IO coroutine. Add a `resolved` field to the response:

```
POST /playlists → 201 { id: 5, title: "Bluey Season 3", status: "resolving" }
GET /playlists/5 → { id: 5, title: "...", status: "resolved", video_count: 47 }
```

The dashboard JS polls every 2 seconds until status is "resolved" or "failed". Simple, no websockets needed.

**On mDNS (kidswatch.local):** The parent asked about this. It's tempting but unreliable:
- Works on iOS/macOS (Bonjour built-in)
- Works on some Android phones (Android 12+ has better mDNS)
- Doesn't work on Windows without Bonjour installed
- Flaky on many home routers

I'd say: try it as a bonus, don't depend on it. Register the mDNS service, and if the parent's phone resolves it, great. If not, they fall back to the IP in the QR code. Low effort to add, nice when it works.

**On session expiry:** I'll change to: sessions last 30 days, auto-extend on activity. The PIN is the security gate, not the session duration. If someone has the session cookie, they already proved they know the PIN. No reason to hassle the parent with re-auth every day.

**On foreground service for V0.2.1:** After more thought, I want to propose a middle ground. When the app launches, start Ktor. When the app goes to background, keep Ktor alive for 5 minutes (using a short-lived foreground service), then shut down. This covers the "parent backgrounded the app briefly to check something" case without a permanent notification. For V0.3 with the relay, upgrade to a persistent foreground service.

**On Test 7 (Ktor on Android TV):** I want to expand this test:
- Serve a page from the TV emulator, load it on a real phone
- Test with the app in foreground, backgrounded for 1 min, backgrounded for 10 min
- Test after TV sleep/wake
- Measure latency of page load (should be <100ms on WiFi)
- Test concurrent connections (parent on two devices)

This is the highest-priority test. If Ktor can't serve pages reliably on Android TV, the whole architecture falls.

### Parent

The "resolve on add" fix makes me happy. I add a playlist, I see it working, done. Good.

On the PIN: long-press on the settings gear to show PIN for 10 seconds sounds perfect. I can remember that.

On the IP address thing: if it changes once a week and I just re-scan the QR code, that's fine. It's a 2-second thing. And you said V0.3 fixes this permanently with the relay URL? Good. I can live with it.

One more thing: **can the dashboard show me what's on screen right now?** Like "Currently playing: Bluey S3E5 — Sleepover." Not the video itself, just the title. So I can check from the kitchen whether they're actually watching what I put on.

---

## Round 3: Tightening the Spec

### Product Manager

The parent's "what's playing now" request is great. It's trivial to add:

```
GET /status → { currently_playing: { title: "Bluey - Sleepover", playlist: "Bluey Season 3", elapsed: "4:32" } | null }
```

The dashboard shows a "Now Playing" card at the top when something is playing. Small addition, high value. **Add to V0.2.1 scope.**

Now let me raise the things I think are missing from the spec:

1. **What happens when the parent removes a playlist while the kid is watching a video from it?** The video should finish, then the app goes to the home screen (playlist row is gone). Don't yank the video mid-play.

2. **Playlist ordering on the home screen.** Most recently added on top? Alphabetical? The parent should probably be able to reorder (drag on the dashboard), but for V0.2.1: most recently added at the top.

3. **Max playlists.** The spec says 20. I think 10 is enough for V0.2.1. It keeps the home screen clean. 20 rows of playlists is overwhelming for a kid. We can increase it later.

4. **What if two parents manage the TV?** (One from their phone, one from a tablet.) Both have the PIN. Both can add/remove playlists. No conflict — Room DB handles concurrent writes fine. The dashboard should show the current playlist list on load. This just works, but it should be explicitly stated.

### Dev Manager

Addressing the PM's points and locking down implementation concerns:

1. **Remove playlist while playing:** Agreed. The video list is snapshotted when the kid enters a playlist. Removing the playlist from Room doesn't affect the in-memory playback list. When playback ends (video finishes or kid presses back), the app re-reads from Room and the row is gone. Simple, no special code needed.

2. **Playlist ordering:** Most recent first. Store `added_at` timestamp, sort descending. Done.

3. **Max playlists at 10:** Fine with me. Enforce at the API level: `POST /playlists` returns 400 if count >= 10. Dashboard shows "Maximum 10 playlists. Remove one to add another."

4. **Concurrent parents:** Room is SQLite — handles concurrent reads/writes fine from the same process. Ktor is single-process, so all requests go through one Room instance. No conflicts. The only UX quirk: if parent A adds a playlist, parent B's dashboard won't see it until they refresh the page (no push). Acceptable for V0.2.1. Could add a polling refresh (every 30s) to the dashboard JS.

**My spec change requests for V0.2.1:**

- Add `POST /playlists/:id/resolve` endpoint (resolve-on-add)
- Add `GET /status` with `currently_playing` field
- Change session expiry from 24h to 30 days with activity extension
- Add 5-minute background grace period for Ktor (short foreground service)
- Set max playlists to 10
- Add mDNS registration as best-effort bonus
- Expand Test 7 scope as described above

**My spec change requests for V0.3:**

- Explicit note that slug changes on every TV reconnect (parent must re-scan QR)
- Add `GET /relay/status` endpoint on the relay for basic monitoring (connection count, uptime)
- Note that relay restart drops all connections (TVs auto-reconnect, parents see brief "offline")

### Parent

Everything sounds right. One last question:

**Can the kid see the connect screen accidentally?** If they navigate to Settings and tap "Show Connect Info," they see the PIN and the IP. Then they could... what? Open a browser on their tablet and add their own playlists?

Honestly, if my kid is savvy enough to find the PIN, type the IP into a browser, enter the PIN, and paste a YouTube playlist URL — I'm impressed, not worried. And they could only add playlists I can see and remove. So it's self-correcting.

But if it bugs me later, a parent PIN on the Settings screen (V0.4?) would solve it. For now, not a concern.

---

## Round 4: Final Consensus

### Product Manager — Sign-off

**V0.2.1: Approved with revisions.** The local server architecture is right for charityware. The key additions from this review:

- Resolve-on-add (not manual refresh)
- "Now Playing" in dashboard status
- Long-press settings gear to show PIN
- 10 playlist limit
- 30-day session expiry
- 5-minute background grace for Ktor

**V0.3: Approved as-is.** The relay design is sound. The "almost dumb pipe" rate limiting is clever and appropriate. Honor-system charityware is the right call. No gates, no codes.

**One concern to carry forward:** When the TV reconnects to the relay (after sleep, reboot, WiFi blip), the slug changes and the parent has to re-scan the QR code. For a parent who bookmarked the relay URL, this is going to be confusing. "It worked yesterday, why doesn't it work today?" We should think about slug persistence — maybe the TV can request the same slug on reconnect? (This requires the relay to have minimal persistence — a small complication to the "zero state" design.)

### Dev Manager — Sign-off

**V0.2.1: Approved.** Tests 5-8 are go/no-go. Test 7 (Ktor on Android TV) is highest priority and should be done first. If Ktor can't serve reliably, we fall back to... I'm not sure what. NanoHTTPD as a backup? Let's cross that bridge if we get there.

**V0.3: Approved with one flag.** The PM's point about slug persistence is valid. Two options:

1. **Relay stores slug → TV-UID mapping in a file.** Tiny state. TV reconnects, presents its UID (could be a random token generated on first relay connection, stored in Room), relay looks up and re-assigns the same slug. File is ~1KB per TV. Survives relay restarts.

2. **TV generates its own slug deterministically** from a secret key (e.g., HMAC of a device-local secret). TV always connects with the same slug. Relay just routes. No server-side state at all.

I prefer option 2. Zero state on the relay. The slug is effectively a long-lived address for the TV. The security properties don't change — 143 bits of entropy, delivered via QR code.

**Risk register for implementation:**

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Ktor doesn't run reliably on Android TV | Low | Blocks V0.2.1 entirely | Test 7 first. NanoHTTPD as fallback. |
| QR scanning doesn't work well | Low | Setup friction | Also show IP + PIN as text fallback |
| 5-min background grace isn't enough | Medium | Parent can't connect when app is backgrounded | Upgrade to persistent foreground service if feedback warrants |
| Relay WebSocket drops frequently | Medium | Parent sees "TV offline" often | Auto-reconnect with exponential backoff. Slug persistence. |
| Android TV kills foreground service (V0.3) | Low-Medium | Remote access stops | Test thoroughly. Battery optimization whitelisting. |

### Parent — Sign-off

Ship it. I don't understand half the technical stuff but the experience sounds right:

1. I sideload the app
2. I scan a code
3. I type a PIN
4. I paste playlist URLs
5. My kid watches what I chose
6. I can see what they're watching from the kitchen
7. If I want remote access later, I flip a switch

That's it. That's what I want.

---

## Summary of Spec Changes

### V0.2.1 Additions (from this review)

1. `POST /playlists` triggers immediate resolution (resolve-on-add)
2. `GET /playlists/:id` includes `status` field (resolving/resolved/failed)
3. `GET /status` includes `currently_playing` field
4. Session expiry: 30 days, auto-extend on activity
5. Ktor background grace: 5-minute foreground service after app backgrounds
6. Max playlists: 10
7. Long-press settings gear → show PIN for 10 seconds
8. mDNS registration (best-effort, `kidswatch.local`)
9. Dashboard JS: poll for resolution status after adding playlist
10. Dashboard: "Now Playing" card at top when video is active

### V0.3 Additions (from this review)

1. Slug persistence: TV generates slug deterministically (HMAC of device secret) so relay URL survives reconnects
2. `GET /relay/status` on relay: connection count, uptime (for monitoring)
3. Explicit note: relay restart = all connections drop, TVs auto-reconnect
4. Explicit note: parent's bookmarked relay URL now survives reconnects (with persistent slug)
