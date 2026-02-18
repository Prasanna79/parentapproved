# ParentApproved.tv

A free, open-source Android TV app that plays only parent-approved videos. No algorithm, no ads, no surprises. Manage everything from your phone.

**Website:** [parentapproved.tv](https://parentapproved.tv)

## How It Works

1. Install the APK on any Android TV (Mi Box, Shield, Chromecast with Google TV)
2. Scan the QR code on the TV with your phone
3. Enter the 6-digit PIN, paste any YouTube URL — video, playlist, or channel
4. Kids see only what you added. No search, no browse, no algorithm.

## Architecture

```
┌──────────────┐                              ┌──────────────┐
│    Phone     │ ◄──── WiFi (HTTP/8080) ────► │  Android TV  │
│  (browser)   │                              │              │
│  Dashboard   │ ◄── or via Cloudflare ─────► │  Ktor server │
│              │     relay (WebSocket)        │  Room DB     │
└──────────────┘                              │  ExoPlayer   │
                                              │  NewPipe     │
                                              └──────────────┘
```

**Local mode:** Phone and TV on same WiFi. Ktor embedded server on port 8080 serves the dashboard and REST API.

**Remote mode:** TV opens outbound WebSocket to a Cloudflare Workers relay. Phone sends HTTPS to the relay, relay forwards to TV. Dashboard served from the edge. TV's Ktor routes don't know about the relay.

## Tech Stack

| Component | Tech |
|-----------|------|
| TV app | Kotlin, Jetpack Compose, ExoPlayer, Ktor 2.3.7, Room |
| Video extraction | NewPipeExtractor (no API key, no sign-in) |
| Parent dashboard | Vanilla HTML/CSS/JS served from TV or relay |
| Relay | TypeScript, Cloudflare Workers + Durable Objects |
| Auth | 6-digit PIN + session tokens, rate-limited |
| DI | ServiceLocator (no Hilt) |

## Project Structure

```
├── tv-app/                    # Android TV app
│   ├── app/src/main/java/     # Kotlin source
│   ├── app/src/main/assets/   # Dashboard (HTML/CSS/JS)
│   ├── app/src/test/          # Unit tests
│   └── app/src/androidTest/   # Instrumented tests
├── relay/                     # Cloudflare Workers relay
│   ├── src/                   # TypeScript source
│   ├── assets/                # Dashboard (relay copy)
│   └── test/                  # Vitest tests
├── marketing/landing-page/    # parentapproved.tv website
└── docs/                      # Specs, release notes, retros
```

## Development

**Prerequisites:** Java 17, Android SDK, Node.js (for relay)

```bash
# Build TV app
cd tv-app && ./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest

# Run relay tests
cd relay && npx vitest run

# Install on device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Deploy relay
cd relay && npx wrangler deploy
```

See [CLAUDE.md](CLAUDE.md) for full environment setup, device configs, and conventions.

## Key Design Decisions

- **No cloud dependency.** Everything works on local WiFi. Remote access is opt-in.
- **No YouTube API key.** NewPipeExtractor does client-side extraction.
- **No algorithm.** Videos play in playlist order and stop.
- **No account.** PIN auth, session tokens, all local.
- **Charityware.** Free forever. If useful, donate to [mettavipassana.org](https://mettavipassana.org/donate).

## Compatibility

- Android TV 7.0+ (API 24)
- Tested: Xiaomi Mi Box 4 (Android 9), Android TV emulator (API 34)
- Dashboard: any mobile or desktop browser

## License

Open source. Code on GitHub for anyone to read and verify.
