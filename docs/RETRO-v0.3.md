# KidsWatch v0.3 Retrospective

**Date:** February 17, 2026
**Scope:** Playback controls, parent dashboard enrichment, real-hardware polish

---

## What Went Well

### Real Hardware Testing Drove Real Fixes
Testing on the Mi Box caught things the emulator never would:
- D-pad Left/Right mapped to playlist skip broke ExoPlayer's built-in controller navigation. Only obvious when you're holding a real remote trying to seek.
- The "Next" button in ExoPlayer's controller was grayed out — invisible in emulator screenshots, obvious on a TV screen.
- Overscan padding worked perfectly — no content cut off on the Panasonic.
- Session limit (5 max) became a real user-facing bug when testing involved multiple PIN resets.

### The CommandBus Pattern
`SharedFlow<PlaybackCommand>` as a fire-and-forget bridge between HTTP routes and ExoPlayer was clean. HTTP routes emit, PlaybackScreen collects. No circular dependencies, no coupling. The dashboard's Stop/Pause/Next buttons "just worked" once the bus was in place.

### Dashboard Session Persistence
Storing the token in `localStorage` with auto-validation on page load was a small change with big UX impact. Before: every page refresh required re-entering the PIN. After: stays logged in for 30 days. The auto-logout on 401 keeps it secure.

### TDD Caught Real Bugs Early
- `SharedFlow.collect` with `return@collect` doesn't cancel — `take(n).toList()` is the correct pattern. Test caught this immediately.
- `jsonPrimitive` throws on `JsonNull` — test caught this in StatusRoutesTest before it could crash at runtime.

---

## What Didn't Go Well

### D-pad Mapping Iterations
This took three rounds to get right:
1. **Plan:** Left unmapped, Right → SkipNext — user reported Left doesn't work for prev
2. **Fix 1:** Added Left → SkipPrev — user reported Left/Right both broken in ExoPlayer controller
3. **Fix 2:** Unmapped both Left and Right — user reported can't skip to next video at all
4. **Fix 3:** ForwardingPlayer for ExoPlayer's next/prev buttons — buttons still grayed out

**Lesson:** Should have tested D-pad behavior on real hardware before writing the mapping. The ExoPlayer controller and our key handler compete for the same keys. The right answer was always "let ExoPlayer handle D-pad, use dashboard for playlist control" — but it took 3 iterations to learn this.

### ForwardingPlayer Doesn't Work for Button State
`ForwardingPlayer.hasNextMediaItem()` override is not respected by PlayerView's controller — it checks the wrapped player's internal state directly. This means we can override the *behavior* of next/prev but not the *enabled state* of the buttons. Need either:
- A custom controller layout
- Dummy MediaItems in the playlist
- Or accept that playlist skip is a dashboard-only feature

### Reset PIN / Session Integration Gap
`PinManager.resetPin()` and `SessionManager.invalidateAll()` both had unit tests and both passed. But nobody tested that they're called *together*. The bug was at the call site — SettingsScreen called `resetPin()` without `invalidateAll()`. Result: old sessions counted toward the 5-session limit even after PIN change.

**Lesson:** Unit tests prove components work individually. Integration tests prove they work together. When two operations must always happen together, test the combined flow.

### Dashboard Next Button Looked Disabled
The Next button had `background: #0f3460` — a dark blue that was nearly invisible on the dark dashboard background. User reported it as "inactive." It was actually wired up and functional, just invisible.

**Lesson:** Test UI on the actual device/browser. Dark-on-dark looks fine in an IDE but fails in practice.

### ADB Permission Accumulation
By the end of v0.3, `settings.local.json` had 48 overlapping ADB allow rules from different invocation patterns (`$ADB`, `"$ANDROID_HOME/platform-tools/adb"`, absolute path, `ANDROID_HOME=... &&`). Each new pattern triggered a new approval prompt.

**Fix:** Cleaned up to ~20 wildcard rules. Created `/adb` slash command with baked-in paths. Should have done this at the start of v0.3, not at the end.

---

## Learnings

1. **Don't fight ExoPlayer's controller.** It has built-in D-pad handling for seeking, play/pause, and button navigation. Intercepting D-pad keys at the Compose level breaks all of this. Only intercept Back (for exit) and media hardware keys.

2. **ForwardingPlayer has limits.** You can override behavior but not button enabled-state. For custom playlist controls in ExoPlayer's UI, you need a custom controller layout.

3. **Test combined behaviors, not just units.** If operation A and operation B must always happen together, write a test that verifies the combined flow. Individual passing tests create false confidence.

4. **Session token in localStorage is table stakes.** Any web dashboard that requires auth should persist the token client-side. Re-entering a PIN on every page refresh is unacceptable UX.

5. **Consolidate permissions early.** Don't wait for 48 rules to accumulate. Set up wildcards and slash commands at the start of a version, not at the end.

6. **Dark-on-dark is invisible.** Use high-contrast colors for interactive elements. `#0f3460` on `#1a1a2e` looks like a disabled button.

7. **Real hardware reveals real UX.** The emulator can't tell you that a grayed-out button is confusing, that overscan cuts off your layout, or that D-pad seeking feels wrong when your key handler eats the events.

---

## v0.4 Priorities (from this retro)

| Priority | Item | Why |
|----------|------|-----|
| P0 | ExoPlayer next/prev buttons working | Kids need playlist control from the remote |
| P1 | Block/blocklist for videos | Parents need content filtering |
| P1 | Proper app launcher banner | Current placeholder is a red circle |
| P2 | Play event titles in recent activity | Dashboard shows video IDs, not titles |

---

## Numbers

- **35 new tests** added (105 unit total, 173 overall)
- **3 D-pad mapping iterations** before settling on the right approach
- **48 → 20 permission rules** after cleanup
- **1 haiku** per release, as tradition demands
