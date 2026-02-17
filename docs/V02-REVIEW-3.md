# V0.2 Spec Review — Round 3

New inputs from founder:
- Google Auth only (no email/password — all parents have Google since this is YouTube-based)
- Add reset button for debugging pairing
- Add debug tools for faster iteration behind settings
- Initial debugging on emulator
- More review rounds, then a parent-readable version

---

## Round 3: Sarah (PM) + Marcus (Dev)

### Sarah — Google Auth

Switching from email/password to Google sign-in is the right call for this audience. Every parent using YouTube playlists has a Google account by definition. Benefits:
- Zero friction sign-up (one tap on mobile, one click on desktop)
- No password to remember or reset
- We get their display name and photo for free (nice-to-have for the dashboard)
- Firebase Auth supports Google provider natively — `signInWithPopup(googleProvider)` on web

This also simplifies the family model. The family ID = Google UID. No separate account creation step needed — first Google sign-in auto-creates the family document.

One thing: the parent website should still work on both phone and desktop. Google sign-in works on both, so no issue.

### Marcus — Google Auth Implementation

On web: `firebase.auth().signInWithPopup(new firebase.auth.GoogleAuthProvider())` — one line. Returns UID, display name, email, photo URL.

On the family document creation, we can use a Firestore `set()` with `merge: true` on first sign-in:
```
db.doc("families/{uid}").set({
  display_name: user.displayName,
  email: user.email,
  photo_url: user.photoURL,
  created_at: serverTimestamp()
}, { merge: true })
```
This creates on first sign-in, no-ops on subsequent sign-ins. Clean.

The TV app stays on anonymous auth — it doesn't need a Google account. Only the parent website uses Google sign-in.

### Sarah — Debug Tools for Fast Iteration

For V0.2 on emulator, we need to iterate fast. The biggest time sinks during development will be:
1. Re-pairing (testing the pairing flow repeatedly)
2. Waiting for playlist resolution (NewPipe is ~2s per video * N videos)
3. Checking what Firestore state looks like from the TV's perspective
4. Verifying play events are being recorded

I'd add a **debug panel** to the TV settings screen with:

**Reset / Re-pair:**
- "Reset Pairing" button — clears anonymous auth, device doc, local SharedPreferences. Returns to pairing screen. This is different from "Re-pair" which keeps the same UID — this is a full factory reset of the app's identity.
- "Unpair (Keep Identity)" — sets family_id back to null in Firestore, goes to pairing screen. Useful for re-testing the activation flow without regenerating everything.

**Firestore State Inspector:**
- Show: device UID, family ID, pairing code, FCM token, number of playlists, number of cached videos
- This saves constantly checking the Firebase console

**Playlist Debug:**
- "Resolve Single Playlist" — paste a playlist ID manually, see resolution results (bypasses the backend entirely, just tests NewPipe)
- Show resolution time per playlist
- Show cache status (cached vs fresh, last resolved timestamp)

**Play Event Debug:**
- Show pending events count (how many haven't been flushed)
- "Force Flush" button — send all pending events to Firestore now
- "Clear Events" — delete all local pending events

**Log Panel:**
- Similar to our feasibility test's ResultLogPanel — a scrollable log of all operations (Firestore calls, NewPipe resolutions, ExoPlayer state changes)
- Toggle on/off

This all goes behind a "Debug" section in Settings. No PIN needed — V0.2 is for us. We can strip it or hide it behind a PIN later.

### Marcus — Debug Tools Implementation Notes

All of Sarah's debug tools are straightforward. The log panel is the most valuable — if we can see every Firestore call and NewPipe operation in real-time on the TV screen, we barely need `adb logcat`.

A few more debug tools I'd add:

**Network:**
- Show current network state (connected/disconnected, WiFi name)
- "Simulate Offline" toggle — disconnects Firestore and blocks NewPipe. Tests all the offline/cache fallback paths without actually disconnecting the emulator's network.

**Stream Debug:**
- When a video is selected, show the full stream list (all progressive + adaptive streams with resolution, codec, bitrate) before playing. This replaces the need for our feasibility Test 4 screen.

**ADB-friendly shortcuts:**
- For emulator testing, we'll be using `adb shell input keyevent` a lot. We should make sure every screen has a predictable D-pad focus order, and consider adding `adb shell am broadcast` intents for common debug actions:
  - `adb shell am broadcast -a com.kidswatch.tv.RESET_PAIRING` → resets pairing
  - `adb shell am broadcast -a com.kidswatch.tv.REFRESH_PLAYLISTS` → refreshes playlists
  - `adb shell am broadcast -a com.kidswatch.tv.FORCE_FLUSH_EVENTS` → flushes play events
  - These let us trigger debug actions from the terminal without navigating the D-pad to the settings screen every time. Huge time saver.

**Version overlay:**
- Tiny version string in the corner of every screen (like `v0.2.0-debug build 17`). Always visible in debug builds.

### Sarah — Reading Marcus's additions

The ADB broadcast intents are genius. During emulator testing, navigating to Settings → Debug → button every time is painful. `adb shell am broadcast -a com.kidswatch.tv.RESET_PAIRING` is one command and instant. We should absolutely do this.

The "Simulate Offline" toggle is great for testing error states systematically instead of hoping for a network glitch.

Stream debug (showing all available streams) folds Test 6 into the actual app. We don't need a separate feasibility test for quality selection if the debug panel shows us everything.

### Marcus — Reading Sarah's additions

"Resolve Single Playlist" in debug is great for testing NewPipe without needing the backend at all. You could land on the TV, go to debug, paste a playlist ID, and see if resolution works — before the website or Firestore are even set up. That unblocks Phase 3 development from Phase 1+2.

Actually, this gives us a **standalone debug mode**: if the TV app is launched and there's no Firestore connection (or we haven't set it up yet), the debug panel still works for NewPipe testing. We could develop the entire playlist resolution + caching + playback pipeline before writing a single line of backend or website code.

---

## Round 4: Final Refinements

### Sarah

Looking at the overall plan — I think we have one more gap. **The parent website auth flow with Google has a UX detail we should specify:**

1. Parent opens kidswatch.app
2. Sees a landing page: "KidsWatch — Safe YouTube for your kids"
3. One button: "Sign in with Google"
4. Google OAuth popup/redirect
5. Returns to dashboard
6. If first time: family document auto-created
7. Dashboard shows playlists (empty at first) + "Pair a TV"

That's clean. One button to sign in. No forms.

**Also: what does the parent website look like on a phone?**

Most parents will open kidswatch.app on their phone (they're sitting on the couch, looking at the TV code, and grabbing their phone). The website must be mobile-first. Simple responsive layout:
- Big "Sign in with Google" button
- Playlist list with add/remove
- Pairing code input
- All stacked vertically, thumb-reachable

### Marcus

**One last architecture concern: the tv_devices document ID strategy.**

Currently: `tv_devices/{uid}` where uid = anonymous auth UID. The anonymous auth UID persists across app restarts BUT can be lost if the user clears app data or reinstalls. If that happens:
- New anonymous auth → new UID → new device doc
- Old device doc still exists in Firestore (orphaned)
- The old pairing code is "used" but the device is gone

For V0.2 this is fine — we're on the emulator, and the "Reset Pairing" debug button handles it. For V0.3+, we'd want cleanup logic (expire old device docs, let parents "forget" a TV from the website).

**Also: Firestore security rules need a small update for Google Auth.**

The current rules use `request.auth.uid == familyId` which assumes family doc ID = auth UID. With Google Auth, the UID is the Google-specific UID (a string like `gXa2bC...`). This still works — we just set the family document ID to the Google UID. No change needed.

But the `tv_devices` list query (for pairing activation) needs the parent to query by `pairing_code`. The current rule `allow list: if request.auth != null` is correct — any authenticated user (parent with Google auth) can list tv_devices to find a matching code. The query is filtered client-side by code + unpaired + not expired. At V0.2 scale this is fine. At scale, we'd add a Cloud Function for activation instead.

**One more thing for debug: the website should also have a debug view.**

A simple "Debug" page on the website that shows:
- Raw Firestore state: family doc, playlist docs, device docs
- Useful for verifying that pairing worked, playlists saved correctly
- Just a JSON dump of the relevant collections
- Only visible in development (localhost or a `?debug=1` query param)

### Sarah — Final

I agree on the website debug view. When something isn't working, being able to see "is the playlist actually in Firestore?" without opening the Firebase console is a huge time saver.

**I think the spec is ready. Let me list what's changed from the previous version:**

1. Google Auth (not email/password) for parent website
2. Reset Pairing button (full reset) + Unpair button (keep identity)
3. Full debug panel in TV settings: state inspector, single playlist resolver, event debug, log panel
4. ADB broadcast intents for common debug actions (no D-pad navigation needed)
5. "Simulate Offline" toggle for testing error states
6. Stream debug (show all available streams before playing)
7. Version overlay on all screens in debug builds
8. Website debug page (raw Firestore state)
9. Mobile-first parent website design
10. Standalone debug mode (TV can test NewPipe without backend)

---

## Parent Persona Review

After the spec is updated, we need to produce a simplified version for a techie parent tester and have them review it. This parent:
- Understands V0.2 is a debug/test build
- Is technical (can sideload APKs, understands what an emulator is)
- Will provide feedback on the flow, not the code
- Cares about: "does this actually solve my problem?"
