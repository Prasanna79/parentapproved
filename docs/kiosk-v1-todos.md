# Kiosk v1 — Known Limitations & TODOs

## Ship-blocking: None (v1 is functional)

## Next version (v1.1)

### External app time tracking
- Screen Time only tracks ParentApproved's internal video player (ExoPlayer)
- Netflix/Prime/other whitelisted app usage is NOT counted toward the daily limit
- **Fix:** Use `UsageStatsManager` to track foreground time for whitelisted apps
  - Requires `PACKAGE_USAGE_STATS` permission (user grants in Settings)
  - Enable Settings temporarily via ADB, grant permission, re-disable Settings
  - Or grant via `adb shell appops set tv.parentapproved.app android:get_usage_stats allow`
  - Poll `queryUsageStats()` in `HomeWatcherService` and accumulate toward daily limit
- **Workaround (v1):** Parent uses "Lock TV" button in dashboard to manually cut off screen time

### HOME button from Netflix/Prime
- Netflix on Android TV intercepts HOME and BACK keys
- Press-and-hold BACK works to exit Netflix (kids figured this out)
- `HomeWatcherService` detects HOME via `ACTION_CLOSE_SYSTEM_DIALOGS` but `killBackgroundProcesses()` isn't strong enough to bring PA to front
- **Fix options:**
  - Use `AccessibilityService` with `FLAG_REQUEST_FILTER_KEY_EVENTS` to intercept HOME before Netflix gets it (requires enabling accessibility via ADB)
  - Or use `ActivityManager.forceStopPackage()` via reflection (requires system-level permission)
  - Or accept press-and-hold BACK as the exit mechanism (it works, kids learn it)
- **Device owner path:** On devices that support `android.software.device_admin`, Lock Task Mode handles this correctly. The Mi Box 4 does NOT declare this feature.

### Lock Task Mode on device-owner devices
- When device owner IS set (e.g., emulator, other Android TV boxes), don't launch whitelisted apps with `setLockTaskEnabled(true)` — it traps them
- Instead: exit lock task before launching, re-enter on `onResume()`
- Current code already guards with `isDeviceOwner()` check, but the flow should be: `exitLockTask() → launch app → onResume() → re-enter lock task`

### Hotstar not showing in whitelist
- `in.startv.hotstar` was whitelisted but not appearing in the installed apps list
- May have been uninstalled during factory reset and not reinstalled
- User needs to install from Play Store (re-enable Play Store temporarily)

### Dashboard improvements
- Show which apps kids actually used today (not just PA video playback)
- Show total screen-on time vs per-app breakdown
- Add "Quick Lock" that immediately shows lock screen on TV
