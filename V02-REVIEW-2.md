# V0.2 Spec Review — Round 2

New inputs from founder:
- Sideload for TV app until public launch (no Play Store for V0.2)
- Need cloud functions later for analytics crunching on ingest
- Need push messages to TV later for usage time control
- Pick the right backend for these future needs
- Reconsider parent PIN for V0.2 vs later
- Add a sync/refresh button to TV for debugging
- Is the pairing flow fully specced with actual API calls?

---

## Round 1: Independent Reviews

### Sarah (PM) — Review with New Inputs

**Sideloading changes things significantly.**

Dropping Play Store for V0.2 removes a whole phase (store listing, review cycles, rejection risk). It also means:
- Distribution is `adb install` or a shared APK file. Fine for V0.2 since this is family/friends testing, not public.
- No auto-update mechanism. When NewPipe breaks, we manually push a new APK. Acceptable for V0.2 scale.
- We don't need to worry about Leanback launcher requirements or store compliance for now.
- Plan Phase 4.3 (Play Store Submission) should be removed from V0.2 entirely.

**Parent PIN — defer to V0.3.**

I've been thinking about this. In V0.2 we're sideloading to our own TVs. The "kid" is our kid or a test family's kid. The parent is the person who set it up. A PIN adds:
- A screen to build (PIN entry UI, PIN setup flow)
- EncryptedSharedPreferences integration
- A flow decision during pairing (when to prompt for PIN)
- A recovery story (even if it's "factory reset")

For what? To protect a Settings screen that just has "re-pair" and "clear cache." If a kid accidentally hits Settings, the worst case is they clear the cache and the app re-resolves playlists. Not dangerous.

I'd cut PIN entirely from V0.2. Add it in V0.3 when we add time controls (which is when "kid can't access settings" actually matters). For V0.2, Settings is just open — it's a debug tool anyway.

**Sync button — yes, absolutely.**

The spec says "resolve on launch, cache for session." But during development and testing, we'll constantly want to force a refresh without restarting the app. A visible "Refresh" button on the home screen (or in Settings) that re-resolves all playlists is a must. In the future this could become a pull-down gesture or an automatic periodic check, but for V0.2 a button is perfect.

Actually — I'd put it right on the home screen, not buried in Settings. A small refresh icon in the top corner. During V0.2 testing this will be used constantly. We can hide it later or move it behind the PIN when we add one.

**Pairing flow — I see a gap.**

The spec describes the pairing flow at a high level, but let me walk through it step by step as a user and check if every API call exists:

1. **User installs app on TV** (sideload) → app launches for first time
2. **App has no device token** → enters pairing mode
3. **App calls backend to register** → but wait, how does the app authenticate with the backend? It has no account, no token. This is an unauthenticated call.
   - Need: a public endpoint that accepts a device ID and returns a pairing code + device token
   - The device token is the TV's "identity" going forward
   - Risk: anyone can call this and generate codes. At V0.2 scale, doesn't matter. For V0.4 rate limiting.
4. **App displays the code** → clear, big, on screen
5. **Parent goes to website, signs in** → parent is authenticated
6. **Parent enters the code** → website calls backend to link this code to the parent's family
   - Need: authenticated endpoint that takes a pairing code and sets the family_id on the matching tv_devices row
   - What if the code doesn't exist? Error: "Code not found"
   - What if the code is already paired? Error: "This code has already been used" (or allow re-pairing to a different family?)
   - Should codes expire? Probably yes — a code generated 24 hours ago shouldn't be valid. Add `created_at` to tv_devices and reject codes older than 1 hour.
7. **TV is polling** → GET endpoint checks if family_id is now set on its device row
   - Authenticated by device token (issued in step 3)
   - Returns: `{ paired: false }` or `{ paired: true, familyId: "..." }`
8. **TV sees paired=true** → transitions to home screen, fetches playlists

This mostly works but the spec needs:
- **Code expiry** (1 hour) — regenerate if expired
- **Unauthenticated register endpoint** — clearly marked as public
- **What happens if TV is already paired and app restarts** — it should skip pairing entirely, go straight to home screen (device token in SharedPreferences)
- **What happens if parent unpairs a TV** — is there an unpair API? For V0.2, probably not needed. If we need to unpair, just delete the tv_devices row in Supabase and clear the app data on the TV.

**One more thing: what if the parent hasn't added any playlists yet when the TV pairs?**

TV pairs → fetches playlists → gets empty list. What does the kid see? We need an empty state: "No playlists yet! Ask your parent to add playlists at kidswatch.app." This is missing from the spec.

---

### Marcus (Dev) — Review with New Inputs

**Backend choice — Supabase vs Firebase.**

The new requirements change the calculus:

| Need | Supabase | Firebase |
|------|----------|---------|
| Data store | Postgres (excellent) | Firestore (good, but NoSQL) |
| Auth | Built-in, email/password | Built-in, email/password |
| Cloud Functions | Edge Functions (Deno) | Cloud Functions (Node.js, mature) |
| Push to devices | No native solution | FCM (Firebase Cloud Messaging) — built in, free, designed for exactly this |
| Realtime | Realtime subscriptions (Postgres changes) | Firestore realtime listeners |
| Analytics crunching | Edge Functions + Postgres queries | Cloud Functions + BigQuery export |
| Free tier | 500MB DB, 50K users, 500K edge function invocations | Spark plan: 1GB Firestore, 50K auth, 2M function invocations |
| Android SDK | Community Kotlin SDK (less mature) | Official Google Android SDK (first-class) |

The **push messages to TV** requirement is the deciding factor. Firebase Cloud Messaging (FCM) is:
- Free, unlimited messages
- Built into every Android device (including Android TV)
- Official SDK, first-class Android support
- Can send to specific devices by token
- Can send data messages (silent, app processes them) — perfect for "time's up" or "new playlist added"

Supabase has no equivalent. You'd have to build push yourself (FCM on top of Supabase, or websockets). That's unnecessary complexity when Firebase gives it to you for free.

For **analytics crunching on ingest**: Firebase Cloud Functions can trigger on Firestore writes. When a play event is written, a function can aggregate it (total watch time per day, per video, etc.) and write the summary. This is cleaner than running batch jobs.

**My recommendation: Firebase.**
- Firestore for data (families, playlists, devices, events)
- Firebase Auth for parent website
- Cloud Functions for analytics crunching (V0.3, but architecture supports it now)
- FCM for push to TV (V0.3+ time controls, but register the device token now)
- Official Android SDK for the TV app (much better DX than Supabase's Kotlin SDK)

The trade-off is NoSQL (Firestore) vs SQL (Postgres). Firestore is fine for our data model — it's all simple documents. We don't need JOINs or complex queries for V0.2. And Firestore's realtime listeners mean the TV doesn't even need to poll — it can subscribe to its family's playlist collection and get instant updates when the parent adds or removes a playlist. That's better than polling AND better than a sync button (though we'd keep the sync button for debugging).

**Sideloading — simplifies things.**

Agreed with Sarah. Remove Play Store phase. For sideloading:
- Build APK, share via Google Drive / direct transfer
- `adb install` for development
- Consider using Firebase App Distribution for beta testing (free, supports sideloading with a test link) — but that's nice-to-have, not V0.2

**Parent PIN — agree, defer.**

Not worth the complexity for V0.2. Settings screen is just "re-pair" and "clear cache" — both are harmless. A kid pressing them just causes a minor inconvenience, not a safety issue.

**Sync button — agreed, but with Firebase we can do better.**

With Firestore realtime listeners, the TV app can subscribe to the family's playlist list. When the parent adds a playlist on the website, the TV gets notified instantly — no polling, no sync button needed for the live case.

But we should still have the sync button for **playlist content resolution** (NewPipe re-fetch of the videos within each playlist). That's local TV-side work, and the button forces it to re-resolve from YouTube. So the button means "re-fetch video lists from YouTube for all my playlists." The playlist list itself (which playlists are configured) stays in sync automatically via Firestore.

**Pairing flow — let me spec the actual Firestore operations.**

With Firebase, the flow maps cleanly to Firestore documents:

```
Collection: tv_devices
Document ID: auto-generated
Fields:
  device_uuid: string (TV's unique ID, generated on first launch, stored in SharedPreferences)
  pairing_code: string (e.g., "BEAR-1234")
  device_token: string (FCM token for push, registered now for future use)
  family_id: string | null (null until paired)
  created_at: timestamp
  paired_at: timestamp | null
```

Step by step:

1. **TV first launch:** Generate UUID, store locally. Generate FCM token (for future push). Call Firestore:
   ```
   // Unauthenticated write to tv_devices (Firestore security rules allow create with no auth, but only specific fields)
   db.collection("tv_devices").add({
     device_uuid: "xxx",
     pairing_code: generateCode(),  // done client-side or via Cloud Function
     device_token: fcmToken,
     family_id: null,
     created_at: serverTimestamp()
   })
   ```
   Wait — there's a problem. Who generates the pairing code? If the TV generates it client-side, two TVs could generate the same code. If we use a Cloud Function, that's server-side compute.

   Options:
   - **(a)** TV generates a random 8-character alphanumeric code. Collision probability at V0.2 scale (10 devices) is near zero. Add a uniqueness check (query Firestore for existing code, retry if collision). Simple, no Cloud Function needed.
   - **(b)** Cloud Function generates the code. Cleaner, guarantees uniqueness. But adds server-side code.

   I'd go with **(a)** for V0.2. The TV generates a random code like `ABCD-1234` (4 letters + 4 digits = 26^4 * 10^4 = ~4.5 billion possibilities). Query Firestore to verify it's unused. If taken, regenerate. Ship it.

2. **TV displays code:** Large text on screen.

3. **TV listens for pairing:** Use Firestore realtime listener on its own document:
   ```
   db.collection("tv_devices")
     .where("device_uuid", "==", myUuid)
     .addSnapshotListener { snapshot ->
       if (snapshot.documents[0].getString("family_id") != null) {
         // PAIRED! Transition to home screen
       }
     }
   ```
   No polling! Instant notification when parent activates the code. Much cleaner than HTTP polling every 3 seconds.

4. **Parent enters code on website:** Website queries Firestore for the code, then updates the document:
   ```
   // Find device by code
   const device = await db.collection("tv_devices")
     .where("pairing_code", "==", enteredCode)
     .where("family_id", "==", null)  // only unpaired devices
     .where("created_at", ">", oneHourAgo)  // code expiry
     .get()

   // Link to family
   await device.docs[0].ref.update({
     family_id: currentUser.familyId,
     paired_at: serverTimestamp()
   })
   ```

5. **TV detects pairing via listener** → fetches playlists → shows home screen.

6. **Subsequent launches:** TV has UUID in SharedPreferences. Query Firestore for its device doc. If `family_id` is set → go to home screen. If null → show pairing screen again (shouldn't happen unless unpaired).

**The pairing API is just Firestore operations. No custom REST endpoints needed.** Both the website (Firebase JS SDK) and the TV app (Firebase Android SDK) talk directly to Firestore with security rules controlling access.

**Firestore security rules for pairing:**
```
match /tv_devices/{deviceId} {
  // Anyone can create a new device (unauthenticated TV app)
  allow create: if request.resource.data.family_id == null;

  // Only authenticated users can activate (set family_id)
  allow update: if request.auth != null
    && request.resource.data.family_id == request.auth.uid
    && resource.data.family_id == null;

  // Device can read its own doc (by device_uuid field)
  allow read: if true;  // V0.2: open reads, tighten later
}
```

**Actually — there's a wrinkle.** The TV app is unauthenticated. Firestore security rules distinguish authenticated vs unauthenticated. The TV can't use Firebase Auth (no email/password to sign in with). Options:

- **(a)** Anonymous auth: TV signs in anonymously via Firebase Auth. Gets a UID. Use that as the device identity. Firestore rules can then be tighter.
- **(b)** Keep unauthenticated. Use the device_uuid in the document as a "poor man's auth." Firestore rules are more permissive but at V0.2 scale it doesn't matter.

I'd go with **(a)** — anonymous auth. It's one line of code (`Firebase.auth.signInAnonymously()`) and it gives us a proper UID for security rules. The anonymous UID persists across app restarts. And if we later want to "upgrade" the TV's auth (e.g., link it to the family's account), Firebase supports that natively.

---

## Round 2: Reading Each Other's Comments

### Sarah reads Marcus's comments

Firebase over Supabase — Marcus makes a compelling case. FCM for push is the killer feature. If we need to tell the TV "time's up, stop playing" in V0.3, FCM data messages are exactly how you'd do it. Building that on Supabase would be a whole project.

Firestore realtime listeners for pairing — much better than polling. The TV gets instant notification when paired. And the same realtime listener can notify the TV when the parent adds/removes playlists. That's better UX than "restart the app."

Anonymous auth for the TV — smart. One line of code, proper security foundation. And we register the FCM token now even though we don't use it until V0.3 — zero cost to record it.

Pairing code generation client-side — fine for V0.2. The collision math checks out. At scale, move to a Cloud Function.

**Revised pairing flow (Sarah's version for the spec):**

For the parent, the complete journey is:
1. Parent installs the app on their TV (sideload APK)
2. TV shows a pairing code: "Go to kidswatch.app, enter code ABCD-1234"
3. Parent opens kidswatch.app on their phone/laptop
4. If new user: signs up (email + password)
5. Parent enters the TV code on the website
6. TV instantly shows "Paired!" and transitions to home screen
7. Parent adds a playlist URL on the website
8. TV instantly shows the new playlist row (via Firestore listener)

That's really clean. Steps 1-6 are one-time setup. Step 7-8 is the ongoing loop.

**Empty state:** when the TV pairs but no playlists exist yet, it should show: "All set! Add playlists at kidswatch.app to start watching." Friendly, actionable, not an error.

**Sync button:** Agreed with Marcus — the button is for "re-fetch video contents from YouTube" (NewPipe resolution), not for "re-fetch playlist list from backend" (Firestore handles that automatically). Label it "Refresh Videos" not "Sync."

### Marcus reads Sarah's comments

**PIN deferral** — agreed. Removing PIN from V0.2 simplifies the pairing flow (no PIN setup prompt), the settings screen (no PIN gate), and the data model (no pin_hash field). Clean cut.

**Code expiry** — Sarah's right, we need it. 1 hour is generous enough. Firestore query filters on `created_at > oneHourAgo`. If the TV's code expires, it just generates a new one. The TV should detect this (show "Code expired, generating new code...").

**Empty state** — good catch. That's a real flow gap. Parent pairs TV before adding playlists. TV needs a welcoming empty state, not a broken-looking blank screen.

**Sarah's "complete parent journey" is the right way to spec this.** It should go in the spec as the canonical flow.

**One thing Sarah missed: what if the TV app is killed and restarted while already paired?**

- App restarts → checks SharedPreferences for anonymous auth UID → signs in → queries Firestore for its device doc → finds family_id is set → goes straight to home screen
- This needs to be in the spec because it's the common case (app restart), not the exception

**Also: what about un-pairing?** For V0.2: not needed in the UI. If someone needs to unpair, delete the Firestore document manually (or we add a "forget this TV" button on the website later). The TV app has "Re-pair" in Settings which clears local state and goes back to pairing screen.

---

## Agreements & Resolved Questions

1. **Backend: Firebase** (Firestore + Auth + FCM token registration + Cloud Functions available for V0.3)
2. **Sideload only for V0.2** — no Play Store. Remove Phase 4.3 from plan.
3. **Parent PIN: deferred to V0.3** — Settings screen is open in V0.2
4. **Sync button: "Refresh Videos" on home screen** — forces NewPipe re-resolution of all playlists. Playlist list itself syncs automatically via Firestore listener.
5. **Pairing uses Firestore realtime listener** — no polling. TV gets instant notification.
6. **TV uses Firebase Anonymous Auth** — one line, proper UID, FCM token registered now for future push.
7. **Pairing codes generated client-side** — random XXXX-NNNN, collision check via Firestore query, 1-hour expiry.
8. **Empty state needed** — "Add playlists at kidswatch.app to start watching" when paired but no playlists.
9. **App restart flow** — check local auth → query Firestore → paired? → home screen. No re-pairing.
10. **No custom REST API** — both website and TV talk directly to Firestore via SDKs. Security rules replace RLS.
11. **The "canonical parent journey"** (8 steps above) should be in the spec as the primary flow description.
12. **Un-pairing: V0.2 is manual** (delete Firestore doc or "Re-pair" in TV Settings which clears local state).
