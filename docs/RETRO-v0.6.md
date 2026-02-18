# ParentApproved.tv v0.6 Retrospective

**Date:** February 18, 2026
**Scope:** Content Sources — accept any YouTube URL (videos, channels, playlists, shows), source-agnostic data model, screensaver fix, remote toggle UX, version reporting

---

## What Went Well

### ContentSourceParser Was a TDD Win
73 URL fixtures across 9 test methods, written before a single line of parser code. The fixtures caught the uppercase URL bug (`HTTPS://WWW.YOUTUBE.COM/PLAYLIST?LIST=PLuppercase`) that would have slipped through manual testing. Pure regex parsing with no network calls means the parser is fast, deterministic, and testable in isolation.

### DB Migration Was Clean
The Room migration from v2 → v3 (playlists → channels table, title backfill in play_events) went smoothly. The migration SQL copies data from the old table, updates computed fields, backfills titles from the videos table, then drops the old table. No data loss path. The migration is tested by the instrumented test suite.

### Source-Agnostic Data Model Pays Forward
`ChannelEntity` with `source_type` / `source_id` / `source_url` cleanly handles playlists, videos, and channels without separate tables. Adding Vimeo in v0.7+ only requires a new `ContentSourceParser` case and a new `ContentSourceRepository.resolve()` branch — no schema changes.

### Stage A Bugs Were Quick Wins
All three bug fixes (screensaver, remote toggle, appVersion) were independent and took minutes each. `keepScreenOn = true` is one line. The appVersion fix is a constructor parameter. Moving the remote toggle was a copy-paste between two files. These should have been fixed in v0.5.

---

## What Didn't Go Well

### NewPipe Channel API Surprised Us
The initial `resolveChannel()` implementation assumed `ChannelExtractor` works like `PlaylistExtractor` — call `initialPage`, paginate. It doesn't. NewPipe v0.25.2 channels use a tab-based API: you get `extractor.tabs`, find the "Videos" tab, then get a `ChannelTabExtractor` for that tab. This caused a compilation failure that required a full rewrite of the channel resolution code.

**Lesson:** Read the NewPipe API docs/source before assuming symmetry between extractors. Or better: write a spike test that actually calls `getChannelExtractor()` before writing the production code.

### `replace_all` on Sealed Class Declarations
The `ContentSourceParser` initially used bare `Success(` and `Rejected(` inside the sealed class body. A `replace_all` to qualify them as `ParseResult.Success(` and `ParseResult.Rejected(` also changed the class declarations themselves — turning `data class Success(...)` into `data class ParseResult.Success(...)`, which is invalid Kotlin. Two compilation rounds wasted on this.

**Lesson:** When using replace_all, check that the pattern doesn't also match declaration sites. Or use more specific old_string patterns.

### D-pad / Emulator Controls Broken
After v0.6 changes, D-pad navigation doesn't work on the TV_API34 emulator. Can't navigate, can't select, can't play videos without debug intents. This was NOT tested before wrapping up because the user's emulator controls were already broken — unclear if this is a v0.6 regression or pre-existing emulator issue.

**Lesson:** Need to test D-pad navigation on Mi Box before declaring the release verified. Emulator D-pad issues may mask real bugs.

### No Manual Testing on Real Hardware
The Mi Box wasn't available during this session. All testing was emulator-only. The v0.6 changes affect HomeScreen layout, PlaybackScreen (keepScreenOn), and ConnectScreen (toggle move) — all of which have had emulator-vs-hardware differences before.

**Lesson:** Don't ship without Mi Box testing. The emulator hides WiFi, WebSocket, and input issues.

---

## Learnings

1. **NewPipe extractors are not symmetric.** Playlists give you `initialPage` directly. Channels give you tabs, and you need a separate tab extractor. Always check the actual API shape.

2. **73 fixtures > 7 fixtures.** The URL parser handles 15+ URL patterns. A handful of happy-path tests would have missed the uppercase case, the show URL, the bare PL ID, and the reject patterns. Volume matters for parsers.

3. **Case-insensitive regex matters for URLs.** Query parameters like `?LIST=` are valid HTTP. The parser's `[?&]list=` regex was case-sensitive. YouTube itself is case-insensitive for parameter names. Always use `IGNORE_CASE` for URL matching.

4. **One-line bug fixes should ship immediately.** `keepScreenOn = true` and `appVersion = BuildConfig.VERSION_NAME` were known issues in v0.5 that took 30 seconds each to fix. Batching them into v0.6 meant the screensaver bug existed for an entire release.

5. **Room migrations need instrumented tests.** The migration SQL is complex (create table, copy data, backfill, drop). Unit tests can't run Room migrations. The 19 instrumented tests passing gives confidence the migration works, but we should add explicit migration-specific instrumented tests.

---

## By the Numbers

- **~34 files changed** (8 created, 22 modified, 4 deleted)
- **191 unit tests** (was 157 — 34 new)
- **139 relay tests** (unchanged)
- **19 instrumented tests** (unchanged, all pass on emulator)
- **349 total tests passing**
- **73 URL fixtures** covering playlists, videos, channels, shows, rejects
- **3 compilation fix rounds** (sealed class declarations, SettingsScreen reference, channel extractor API)
- **0 tests on real hardware** (Mi Box unavailable)
- **1 haiku** — tradition holds

---

## Biggest Things to Fix

| Priority | Item | Why |
|----------|------|-----|
| **P0** | D-pad / navigation broken on emulator | Can't use the app without debug intents. May be emulator-only or a real regression. Test on Mi Box ASAP. |
| **P0** | Test on Mi Box | Zero real-hardware verification for v0.6. WebSocket reconnect, keepScreenOn, channel resolution all need real-device confirmation. |
| **P1** | Play intent didn't work (`DEBUG_PLAY_INDEX`) | Sent broadcast with index 0, nothing happened. May be related to D-pad issue or empty video list after fresh install (no sources added yet). |
| **P1** | Add migration-specific instrumented tests | The v2→v3 migration is complex. Should have a test that creates a v2 DB, runs migration, verifies data integrity. |
| **P2** | Channel resolution untested against real YouTube | `resolveChannel()` uses NewPipe tab API — tested for compilation but never actually called against YouTube. Could fail on real channels. |
| **P2** | Dashboard "Content Sources" heading untested | HTML/JS changes to both local and relay dashboards — no browser testing done. |
