# KidsWatch Feasibility Test — Results & Lessons Learned

Date: 2026-02-16
Platform: Android TV emulator (API 34, arm64, Android 14)
WebView: com.google.android.webview v113.0.5672.136

---

## Test Results Summary

| Hypothesis | Result | Notes |
|-----------|--------|-------|
| AccountManager → YouTube OAuth token | PARTIAL | Account picker launches, but requires signed-in Google account on device to complete token flow |
| WebView Google sign-in | BLOCKED | Google detects embedded WebViews and refuses sign-in ("browser or app may not be secure") |
| Cookie import → signed-in WebView | WORKS | Export cookies from real browser → push to device → CookieManager.setCookie() → YouTube loads signed in |
| YouTube Premium in WebView | WORKS | Ad-free playback confirmed, Premium badge visible, no ad overlays detected |
| CSS/JS injection to hide YouTube UI | WORKS | Header, sidebar, search bar hidden successfully. Some elements (title, channel) need updated selectors |
| YouTube /embed/ in WebView | BLOCKED | Error 152 on all embed variants (standard, nocookie, origin param, IFrame API). Playback refused in embedded context |
| NewPipeExtractor stream extraction | WORKS | Extracts 10-16 video-only + 6-30 audio + progressive streams per video. ~2-2.5s extraction time |
| ExoPlayer native playback | WORKS | Plays extracted progressive streams (360p MPEG-4). BUFFERING→READY→PLAYING. D-pad controls work |
| Sequential video loading | WORKS | Can switch between videos — previous stops, new one extracts and plays cleanly |
| JS bridge (Kotlin ↔ WebView) | WORKS | KidsWatchBridge reports page info, injection status, Premium detection back to Kotlin |
| Playlist loading in WebView | WORKS | Playlists render with full metadata, Play All button functional |
| WebView version detection | WORKS | androidx.webkit detects package and version at runtime |

---

## Critical Technical Findings

### 1. Google Blocks WebView Sign-in (Hard Block)
Google actively detects embedded WebViews and blocks OAuth sign-in with "This browser or app may not be secure." This is NOT bypassable with:
- User-agent spoofing (tried Chrome UA — still blocked)
- X-Requested-With header suppression via `WebSettingsCompat.setRequestedWithHeaderOriginAllowList()` — still blocked
- Header-suppressing WebViewClient wrapper — still blocked

Google uses multiple detection vectors:
- `X-Requested-With` header (contains app package name)
- JavaScript environment fingerprinting (WebView exposes different properties than Chrome)
- Likely server-side device/app fingerprinting

**Implication for KidsWatch:** WebView-based Google sign-in is not a viable path. Must use an alternative.

### 2. Cookie Import is the Working Sign-in Path
The proven flow:
1. User signs into Google in a real browser (Chrome, Firefox, etc.)
2. Export cookies (YouTube + Google domains)
3. Push cookies to the app
4. `CookieManager.setCookie(url, cookieString)` injects them
5. `CookieManager.flush()` persists them
6. WebView loads YouTube — fully signed in, Premium active

Cookie details:
- ~14,000 chars of cookies for a signed-in YouTube session
- Key cookies: SID, HSID, SSID, LOGIN_INFO
- Both Secure and HttpOnly cookies can be set via CookieManager (unlike JavaScript)
- Cookies survive app restarts if not cleared

### 3. Chrome Custom Tabs — Unexplored but Promising
Chrome Custom Tabs run in real Chrome (not WebView), so Google won't block sign-in. After sign-in:
- Cookies *may* propagate to CookieManager (needs testing)
- If not, could extract cookies from Chrome's cookie store
- This is the cleanest UX path for the production app

### 4. CSS/JS Injection Works but is Fragile
- Injection via `WebView.evaluateJavascript()` works
- CSS hides YouTube UI elements (search, sidebar, recommendations, comments, end screens)
- YouTube changes its DOM structure periodically — selectors will break
- Need a maintenance strategy: version the injection CSS, test against live YouTube regularly
- Consider a remote-updatable injection config rather than hardcoded CSS

### 5. Android TV Emulator Quirks
- On-screen keyboard doesn't respond to mouse clicks in WebView
- Physical keyboard input doesn't route to WebView text fields
- `adb shell input text "..."` works for typing into focused fields
- Special characters in passwords need shell escaping
- Emulator with `-no-window` works for automated testing but `-no-audio` is needed to avoid audio driver errors
- `swiftshader_indirect` GPU mode works on Apple Silicon

### 6. YouTube Embed (Error 152 — Hard Block)
All `/embed/` variants return Error 152 in WebView:
- `youtube.com/embed/` — Error 152
- `youtube-nocookie.com/embed/` — Error 152
- IFrame Player API (JS-created) — Error 152
- `?html5=1`, `?origin=` params — no effect

The watch page (`m.youtube.com/watch`) plays in WebView but can't be locked down (SPA navigation, ads, related videos, comments). This rules out WebView-based playback for the kid-safe use case.

### 7. NewPipeExtractor + ExoPlayer (Test 4 — Working Path)
**This is the V0.1 playback approach.** NewPipeExtractor extracts raw stream URLs from YouTube without any API key or sign-in, and ExoPlayer plays them natively with full UI control.

**Setup:**
- NewPipeExtractor v0.25.2 (via JitPack) + custom OkHttp `Downloader` with desktop Firefox UA
- Media3 ExoPlayer 1.5.1 (requires compileSdk 35)
- Core library desugaring required (`desugar_jdk_libs_nio:2.1.4`) — NewPipe uses java.time APIs

**Extraction results (3 videos tested):**
| Video | Extraction Time | Progressive | Video-Only | Audio |
|-------|----------------|-------------|------------|-------|
| Rick Astley (dQw4w9WgXcQ) | 2035ms | yes | 16 | 6 |
| rfscVS0vtbw | 2589ms | yes | 10 | 30 |
| BabyShark (M7lc1UVf-VE) | 2241ms | yes | 10 | 6 |

**Playback behavior:**
- Progressive streams (video+audio combined) preferred — 360p MPEG-4 consistently available
- Fallback: MergingMediaSource (video-only + audio) for higher resolutions
- ExoPlayer built-in PlayerView with D-pad controls (play/pause/seek) works on TV
- State transitions: IDLE → BUFFERING → READY → PLAYING
- Sequential loading works: tap new video → stops current → extracts → plays

**Advantages over WebView approach:**
- Total UI control — no YouTube chrome, no ads, no related videos, no comments
- No sign-in needed for extraction (though Premium won't suppress ads since we bypass YouTube player)
- No fragile CSS injection or DOM mutation observers
- Native Android media pipeline — better performance and TV remote integration

**Risks:**
- YouTube may block NewPipe's extraction (cat-and-mouse with YouTube changes)
- NewPipeExtractor updates lag behind YouTube changes — may break periodically
- No Premium account benefits (ad-free is moot since we don't use YouTube's player, but no access to Premium-only content)
- ~2s extraction latency on each video load (acceptable but noticeable)

### 8. AccountManager Token Flow
- `AccountManager.newChooseAccountIntent()` works on API 26+ to show account picker
- Falls back to `getAccountsByType("com.google")` with GET_ACCOUNTS permission on API 24-25
- Requires a Google account signed into the device (not just the app)
- `getAuthToken()` with scope `oauth2:https://www.googleapis.com/auth/youtube.readonly` should work once account is present
- MergeSession (token → cookie conversion) is undocumented and likely deprecated — not a reliable path

---

## Architecture Decisions for Full App

### Playback Strategy (Recommended)
**V0.1:** NewPipeExtractor + ExoPlayer — total UI control, no WebView, no sign-in needed for playback
**Future:** Evaluate WebView + Premium cookies if Premium-only content access is needed

### Sign-in Strategy (Recommended)
**Primary:** Chrome Custom Tabs for initial sign-in → cookies flow to CookieManager → WebView uses them
**Fallback:** AccountManager token flow (for devices with OS-level Google account)
**Manual:** Cookie import from browser (power user / debugging tool)
**Note:** Sign-in is not needed for V0.1 playback (NewPipe extracts without auth), but may be needed for personalized playlists or Premium content

### WebView Configuration Checklist
```
- JavaScript: enabled
- DOM storage: enabled
- Third-party cookies: enabled (CookieManager.setAcceptThirdPartyCookies)
- User-agent: Chrome desktop UA (for full YouTube experience, not mobile)
- Media autoplay: enabled (mediaPlaybackRequiresUserGesture = false)
- Wide viewport: enabled
- Multiple windows: disabled
```

### UI Injection Strategy
- Inject CSS immediately on page load via `onPageFinished()`
- Use MutationObserver in JS to re-inject if YouTube SPA navigation changes DOM
- Keep injection rules in a JSON config that can be updated OTA
- Monitor for ad elements to detect Premium status

### Cookie Persistence
- CookieManager persists cookies across app restarts by default
- Call `CookieManager.flush()` after setting cookies
- Google session cookies expire — need refresh strategy
- Consider periodic cookie health checks (verify SID/HSID still valid)

---

## File Inventory

```
android-tv-test/
├── push-cookies.sh                    # Firefox → emulator cookie export
├── app/src/main/
│   ├── AndroidManifest.xml            # INTERNET, GET_ACCOUNTS, leanback
│   └── java/com/kidswatch/feasibility/
│       ├── MainActivity.kt            # Single activity, Compose nav
│       ├── ui/
│       │   ├── theme/Color.kt, Theme.kt
│       │   ├── navigation/AppNavigation.kt
│       │   ├── screens/
│       │   │   ├── HomeScreen.kt
│       │   │   ├── AccountManagerTestScreen.kt
│       │   │   ├── WebViewTestScreen.kt
│       │   │   ├── EmbedTestScreen.kt        # Test 3: embed/iframe diagnostics
│       │   │   └── NewPipeTestScreen.kt      # Test 4: NewPipe + ExoPlayer
│       │   └── components/ResultLogPanel.kt
│       └── util/
│           ├── YouTubeApiClient.kt     # OkHttp YouTube Data API v3
│           ├── CookieConverter.kt      # MergeSession experiment
│           ├── WebViewConfigurator.kt  # CSS/JS injection, URL filtering
│           └── NewPipeDownloader.kt    # OkHttp Downloader for NewPipeExtractor
```

---

## Build & Test Commands

```bash
# Set environment
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools

# Build
cd /Users/prasanna/src/KidsWatch/android-tv-test
./gradlew assembleDebug

# Create TV emulator (one-time)
avdmanager create avd -n TV_API34 -k "system-images;android-34;android-tv;arm64-v8a" -d tv_1080p

# Launch emulator (with window for manual testing)
$ANDROID_HOME/emulator/emulator -avd TV_API34 -no-audio -gpu swiftshader_indirect

# Launch emulator (headless for automated testing)
$ANDROID_HOME/emulator/emulator -avd TV_API34 -no-window -no-audio -gpu swiftshader_indirect

# Install and run
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.kidswatch.feasibility/.MainActivity

# Push cookies (close Firefox first!)
./push-cookies.sh

# Type into focused fields
adb shell input text "your@email.com"
adb shell input keyevent KEYCODE_ENTER
```
