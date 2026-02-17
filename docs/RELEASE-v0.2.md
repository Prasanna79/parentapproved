# KidsWatch v0.2 Release Notes

**Date:** February 17, 2026
**Milestone:** First run on real hardware (Xiaomi Mi Box 4)

---

## It's alive!

KidsWatch v0.2 is running on an actual TV in the living room. A Lego Ninjago playlist, loaded from a phone, thumbnails glowing on a Panasonic screen. Kids can watch. Parents can manage. No ads. No algorithm. No YouTube chrome. Just the videos you chose.

```
Pixels on the screen—
from code to a child's delight,
Ninjago plays on.
```

---

## What's in v0.2

### Local-First Architecture
Replaced the Firebase-based v0.1 prototype with a fully local embedded server. The TV is the server. The phone is the remote. No cloud, no accounts, no third-party dependencies at runtime.

### Phone Dashboard
- Connect via QR code or URL (http://TV-IP:8080)
- PIN-based authentication (6-digit, rate-limited)
- Add/remove YouTube playlists from your phone
- View watch activity: videos played, total watch time

### TV App
- Home screen with playlist rows and video thumbnails
- Video playback via NewPipeExtractor + ExoPlayer
- Auto-advance through playlist
- Connect screen with QR code and PIN display
- Settings screen with PIN management
- 2-5s cold start for playback, 1-2s warm

### Under the Hood
- Room DB for playlist and video cache persistence
- Ktor embedded HTTP server on port 8080
- Session-based auth with 30-day expiry, max 5 concurrent
- PIN rate limiting with exponential backoff
- 16 debug intents for CI/CD automation
- NewPipe stream extraction with progressive + adaptive fallback

### Test Coverage
- 70 unit tests (Room, PIN, Session, Ktor routes, URL parser, StreamSelector)
- 19 instrumented tests (DB, intents, dashboard assets)
- 15 ADB intent + HTTP integration tests
- 34 automated UI tests
- **138 total automated tests, all passing**

### Verified Hardware
- **Emulator:** TV_API34 (Android 14, arm64)
- **Real device:** Xiaomi Mi Box 4 (MIBOX4, Android 9, API 28)

---

## What's Next (v0.3)
- Play/pause and next/prev D-pad controls during playback
- App launcher icon/banner for Android TV home
- Overscan safe area padding
- Playlist display names instead of raw IDs
