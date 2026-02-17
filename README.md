# KidsWatch

A kid-safe YouTube player for Android TV. Parents pick the playlists, kids watch the videos. No ads, no algorithm, no rabbit holes.

## How It Works

1. **Install** the app on your Android TV (Mi Box, Shield, Chromecast with Google TV, etc.)
2. **Open** the app and scan the QR code with your phone
3. **Enter** the 6-digit PIN shown on the TV
4. **Add** YouTube playlist URLs from your phone
5. **Kids watch** — only the videos you chose, with full D-pad remote control

## Why?

The YouTube Kids app still surfaces unexpected content. The regular YouTube app has ads, autoplay, and an algorithm designed to keep watching. KidsWatch gives parents full control: you pick the playlists, the TV plays them, nothing else.

## Architecture

```
┌──────────────┐         WiFi          ┌──────────────┐
│    Phone     │ ◄──── HTTP/8080 ────► │  Android TV  │
│  (browser)   │                       │  (KidsWatch)  │
│              │   PIN auth + REST     │              │
│  Dashboard:  │   ─────────────►      │  Ktor server │
│  • Add/remove│                       │  Room DB     │
│    playlists │   ◄─────────────      │  ExoPlayer   │
│  • View stats│   JSON responses      │  NewPipe     │
└──────────────┘                       └──────────────┘
```

Everything runs locally on your network. No cloud, no accounts, no data leaves your home.

## Features

- **Playlist management** from your phone browser
- **NewPipe extraction** — no YouTube API key needed
- **ExoPlayer playback** — native Android media player
- **PIN + session auth** — rate-limited, no cloud accounts
- **Watch activity tracking** — see what was watched and for how long
- **QR code pairing** — point your phone camera at the TV
- **D-pad remote control** — designed for TV remotes
- **Offline resilient** — cached videos play without internet

## Compatibility

- Android TV devices running Android 7.0+ (API 24)
- Tested on: Xiaomi Mi Box 4 (Android 9), Android TV emulator (API 34)

## Development

Built with Kotlin, Jetpack Compose, ExoPlayer, Ktor, Room, and NewPipeExtractor.

```bash
# Prerequisites: Java 17, Android SDK
cd tv-app
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

See [CLAUDE.md](CLAUDE.md) for full development setup and conventions.

## License

Private project — not yet open source.
