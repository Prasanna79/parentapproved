# ParentApproved.tv Security Review

**Date:** 2026-02-20
**Reviewer:** Senior Security Researcher (Claude Opus 4.6)
**Scope:** Full codebase audit — TV app (v0.7.1), Cloudflare Workers relay, parent dashboard
**Methodology:** Manual source code review, OWASP Top 10 mapping, threat modeling

---

## Executive Summary

ParentApproved.tv demonstrates a security-conscious design for a local-first family app. The architecture sensibly separates concerns: a local Ktor server for LAN access, a Cloudflare Workers relay for remote access, and PIN-based authentication with session tokens. The relay implements a defense-in-depth model with path allowlists, rate limiting, payload size caps, and timing-safe secret comparison.

However, the audit identified **2 HIGH**, **5 MEDIUM**, and **7 LOW** severity findings, plus several informational observations. The most impactful issues are: (1) the `/status` endpoint is unauthenticated, leaking operational state to any LAN user, and (2) PIN comparison uses direct string equality (`==`) rather than constant-time comparison, creating a theoretical timing side-channel. No CRITICAL vulnerabilities were found.

The app's threat model is inherently bounded by its use case (family device on a home LAN, kids cannot install apps or use ADB), which significantly reduces the practical exploitability of most findings. The relay's security posture is strong, with well-implemented controls for a public-facing service.

### Risk Summary

| Severity | Count | Key Findings |
|----------|-------|-------------|
| CRITICAL | 0 | -- |
| HIGH | 2 | Unauthenticated `/status` endpoint; PIN timing side-channel |
| MEDIUM | 5 | Debug receiver in prod manifest; no CSP headers; relay allowlist incomplete for time-limits; no CORS on Ktor; `usesCleartextTraffic=true` |
| LOW | 7 | PIN in QR URL; no release code obfuscation; `innerHTML` with escaped content; per-isolate rate limiting; session tokens in SharedPrefs; 90-day TTL; relay secret logged |
| INFO | 5 | Various defense-in-depth recommendations |

---

## 1. Authentication & Authorization

### FINDING 1.1 — PIN Comparison Not Timing-Safe [HIGH]

**File:** `tv-app/app/src/main/java/tv/parentapproved/app/auth/PinManager.kt`, line 53
**Code:**
```kotlin
if (pin == currentPin) {
```

**Description:** The PIN is compared using Kotlin's `==` operator, which compiles to `String.equals()` — a non-constant-time comparison. An attacker on the same LAN could theoretically perform a timing attack against the `/auth` endpoint to deduce the PIN character-by-character.

**Impact:** With network jitter on a LAN, this is difficult but not impossible to exploit. The 6-digit numeric PIN has only 1,000,000 combinations, and the rate limiter (5 attempts before lockout) already provides strong protection against brute-force. However, a timing attack could narrow the search space before triggering lockout.

**Contrast:** The relay correctly uses `crypto.subtle.timingSafeEqual` for tvSecret comparison (`relay/src/relay.ts`, line 54-68). The TV-side PIN check should follow the same discipline.

**Remediation:**
```kotlin
import java.security.MessageDigest

if (MessageDigest.isEqual(pin.toByteArray(), currentPin.toByteArray())) {
```

**CVSS Estimate:** 5.3 (AV:A/AC:H/PR:N/UI:N/S:U/C:H/I:N/A:N) — Adjacent network, high complexity, but high confidentiality impact if PIN is leaked.

---

### FINDING 1.2 — `/status` Endpoint Unauthenticated [HIGH]

**File:** `tv-app/app/src/main/java/tv/parentapproved/app/server/StatusRoutes.kt`, lines 31-58
**Code:**
```kotlin
fun Route.statusRoutes() {
    get("/status") {
        // No validateSession() call
        ...
        call.respond(StatusResponse(
            version = BuildConfig.VERSION_NAME,
            serverRunning = true,
            playlistCount = playlistCount,
            activeSessions = activeSessions,
            currentlyPlaying = nowPlaying,
        ))
    }
}
```

**Description:** The `/status` endpoint requires no authentication. Anyone on the LAN (or via the relay, where it is allowlisted as `/api/status`) can query it. It returns: app version, number of active sessions, number of playlists, and currently playing video (including title, playlist title, elapsed time, video ID).

**Impact:** This leaks what the child is watching to any device on the same WiFi network, or to anyone who knows the tvId for the relay URL. The video information is privacy-sensitive data about a minor.

**Remediation:** Add session validation, or return a minimal unauthenticated response (just `serverRunning` and `version`) and the full response only when authenticated.

**CVSS Estimate:** 5.7 (AV:A/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N)

**Note:** The `/status` endpoint is in the relay allowlist. Anyone who discovers a tvId can poll what a child is currently watching remotely without authentication.

---

### FINDING 1.3 — `/time-limits/request` Endpoint Unauthenticated [LOW]

**File:** `tv-app/app/src/main/java/tv/parentapproved/app/server/TimeLimitRoutes.kt`, lines 178-188

Intentionally unauthenticated (child triggers from lock screen). Has a 2-minute cooldown. Acceptable design choice.

---

### FINDING 1.4 — Session Token Entropy and Lifecycle [INFORMATIONAL]

**Positive findings:**
- Tokens generated using `java.security.SecureRandom` with 32 bytes (256 bits) of entropy
- Token format is hex-encoded, yielding 64-character tokens
- Max 20 concurrent sessions with automatic pruning
- Token refresh creates new token and invalidates old one

**Observation:** 90-day TTL is long. For a family app where re-auth is trivial (glance at TV for PIN), consider 30 days.

---

### FINDING 1.5 — Authorization Header Parsing Accepts Any Prefix [LOW]

**File:** `tv-app/app/src/main/java/tv/parentapproved/app/server/AuthRoutes.kt`, line 29
```kotlin
val token = authHeader?.removePrefix("Bearer ")
```

`removePrefix` returns the original string if prefix doesn't match. Functionally harmless but not RFC 6750 compliant.

---

## 2. Network Security

### FINDING 2.1 — No CORS Configuration on Ktor Server [MEDIUM]

**File:** `tv-app/app/src/main/java/tv/parentapproved/app/server/ParentApprovedServer.kt`

No CORS plugin installed. Any website loaded in a browser on the LAN could make cross-origin requests to the TV's API. The auth token is passed via `Authorization` header (not cookies), so CSRF is not directly applicable, but lack of CORS means JavaScript on any origin can read responses.

**Remediation:**
```kotlin
install(CORS) {
    allowHost("localhost:8080")
}
```

---

### FINDING 2.2 — `usesCleartextTraffic=true` in AndroidManifest [MEDIUM]

**File:** `tv-app/app/src/main/AndroidManifest.xml`, line 22

Necessary for local Ktor server but means API traffic (including session tokens and PINs) travels unencrypted on the LAN. An attacker on the same WiFi network could sniff the PIN or steal a session token.

**Remediation:** Use Android's `network_security_config.xml` to restrict cleartext traffic to only `127.0.0.1` and the local subnet.

---

### FINDING 2.3 — Local Server Binds to All Interfaces [INFORMATIONAL]

Intentional — parent phone needs to reach the TV. PIN + session token model provides adequate access control for a home LAN.

---

## 3. Input Validation & Injection

### FINDING 3.1 — Room DB Queries Are Properly Parameterized [POSITIVE]

All Room queries use parameterized `:variable` syntax. Room generates type-safe queries at compile time. **No SQL injection risk.**

---

### FINDING 3.2 — Content Source URL Parser Is Well-Defended [POSITIVE]

Multiple layers of defense: YouTube domain allowlist, private IP blocking, file extension blocking, regex-based ID extraction with strict character classes. **No SSRF or URL injection vectors identified.**

---

### FINDING 3.3 — Dashboard XSS Mitigated by `escapeHtml()` [LOW]

Dashboard uses `innerHTML` but consistently calls `escapeHtml()` before insertion. The function uses the safe DOM method (`textContent` then `innerHTML`). **Correct and effective XSS mitigation.** Using `textContent` directly instead of `innerHTML` would be more robust.

---

### FINDING 3.4 — Path Traversal in Dashboard Asset Route [INFORMATIONAL]

Classloader resource loading constrains path traversal. Cannot escape the APK boundary. Not forwarded by relay allowlist. **No practical exploitation path.**

---

## 4. Secrets Management

### FINDING 4.1 — Secrets Stored in SharedPreferences (Plaintext on Disk) [LOW]

Session tokens, tvSecret, and tvId in SharedPreferences. Protected by Linux file permissions (`MODE_PRIVATE`). An attacker would need physical access + ADB or root.

**Remediation (defense-in-depth):** Consider `EncryptedSharedPreferences` from AndroidX Security library.

---

### FINDING 4.2 — Relay Logging Is Disciplined [LOW]

Logs the *decision* ("accept_new" or "validate_stored"), not the actual secret. No secret values appear in log statements.

---

### FINDING 4.3 — PIN Visible in Debug State Dump [LOW]

Debug intents can retrieve the PIN via logcat. Gated by `BuildConfig.IS_DEBUG`. See Finding 5.1 regarding the debug receiver being exported.

---

## 5. Android-Specific Security

### FINDING 5.1 — Debug Receiver Exported in Production Manifest [MEDIUM]

**File:** `tv-app/app/src/main/AndroidManifest.xml`, lines 34-68
```xml
<receiver
    android:name=".debug.DebugReceiver"
    android:exported="true">
```

No `android:permission` guard. While `onReceive` checks `BuildConfig.IS_DEBUG`, the receiver should not be in the release manifest at all.

**Remediation:** Move to debug-only source set:
```
tv-app/app/src/debug/AndroidManifest.xml  (add receiver here)
tv-app/app/src/main/AndroidManifest.xml   (remove it)
```

---

### FINDING 5.2 — Release Builds Not Obfuscated [LOW]

`isMinifyEnabled = false` for release builds. APK contains full class names, method names, and string literals. Low impact for current project scope.

---

### FINDING 5.3 — Permissions Are Minimal [POSITIVE]

Only three permissions: `INTERNET`, `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE`. No dangerous permissions. **Excellent for a kid-focused app.**

---

## 6. Relay Security

### FINDING 6.1 — Relay Allowlist Missing Time-Limits Routes [MEDIUM]

**File:** `relay/src/allowlist.ts`, lines 11-21

Missing routes: `GET/PUT /api/time-limits`, `POST /api/time-limits/lock`, `POST /api/time-limits/bonus`, `POST /api/time-limits/request`. Additionally, `PUT` is not in `validMethods`.

Parents cannot manage time limits remotely. The relay dashboard UI suggests they can, creating a confusing experience.

---

### FINDING 6.2 — Per-Isolate Rate Limiting [LOW]

Rate limiters stored in per-isolate memory. State lost on isolate restart. Acceptable for current scale — Durable Object provides the real per-tvId security.

---

### FINDING 6.3 — Timing-Safe Secret Comparison Is Well-Implemented [POSITIVE]

Relay uses `crypto.subtle.timingSafeEqual` with proper length-mismatch handling. Correct and prevents timing attacks.

---

### FINDING 6.4 — Secret Rotation on Disconnect Logic [INFORMATIONAL]

`shouldAcceptSecret` accepts new secret when not authenticated. Handles rotation correctly. tvId is a UUID (122 bits entropy), making blind guessing infeasible.

---

### FINDING 6.5 — WebSocket Frame Size Enforcement [POSITIVE]

Frames exceeding 100KB rejected. Request bodies capped at 10KB, responses at 100KB. Consistently enforced.

---

## 7. Dashboard Security

### FINDING 7.1 — No Content Security Policy Headers [MEDIUM]

Neither local Ktor server nor relay sets CSP, X-Frame-Options, or X-Content-Type-Options headers.

**Remediation:**
```
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' https://fonts.googleapis.com; font-src https://fonts.gstatic.com; img-src 'self' https://img.youtube.com
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
Referrer-Policy: no-referrer
```

---

### FINDING 7.2 — PIN Transmitted in QR Code URL [LOW]

QR code encodes `http://192.168.0.101:8080/?pin=123456`. PIN visible in browser history. Low impact since PIN is already displayed on TV screen.

**Remediation:** Clear URL query parameter after extraction: `window.history.replaceState({}, '', window.location.pathname);`

---

### FINDING 7.3 — localStorage Token Storage [INFORMATIONAL]

Standard practice for SPAs. Token not accessible to other origins. Accepted pattern.

---

## 8. Privacy Concerns

### FINDING 8.1 — No Analytics or Tracking [POSITIVE]

No analytics SDKs, no tracking pixels, no third-party JavaScript. Only external requests: Google Fonts, YouTube thumbnails, NewPipeExtractor. **Excellent for a kid-focused app and aligns with COPPA principles.**

---

### FINDING 8.2 — Watch History Stored Locally Only [POSITIVE]

Play events stored in local Room database only. Never transmitted to any cloud service.

---

### FINDING 8.3 — Child's Viewing Data Accessible Without Auth [HIGH — see Finding 1.2]

Cross-reference with Finding 1.2: The `/status` endpoint leaks what the child is currently watching to any unauthenticated user.

---

## 9. Dependency Security

### FINDING 9.1 — Dependencies Are Reasonably Current [INFORMATIONAL]

| Dependency | Version | Notes |
|-----------|---------|-------|
| Ktor | 2.3.7 | Ktor 3.x available but not required |
| OkHttp | 4.12.0 | Current stable 4.x |
| Room | 2.6.1 | Current stable |
| ExoPlayer/Media3 | 1.5.1 | Recent release |
| NewPipeExtractor | v0.25.2 | Community-maintained; monitor |
| kotlinx-serialization | 1.6.2 | Slightly behind |

No known critical CVEs in listed versions.

---

### FINDING 9.2 — Dependency Versions Pinned [POSITIVE]

All dependencies use exact version pinning. Prevents supply chain attacks via dependency confusion.

---

## 10. Denial of Service

### FINDING 10.1 — Local Server DoS Resistance [INFORMATIONAL]

No explicit request rate limiting on Ktor. Mitigated by: dedicated device, Android resource limits, relay rate limiting, LAN-only access.

---

### FINDING 10.2 — WebSocket Reconnection Backoff [POSITIVE]

Exponential backoff (1s to 60s cap). Prevents overwhelming the relay.

---

### FINDING 10.3 — Pending Request Timeout [POSITIVE]

Bridge requests timeout after 10 seconds. Pending requests cleaned up on disconnect.

---

## 11. Error Handling

### FINDING 11.1 — Error Messages May Leak Internal State [INFORMATIONAL]

`StatusPages` returns `cause.message` in API responses. Could leak stack traces or file paths.

**Remediation:** In release builds, return generic error message.

---

## Prioritized Remediation Plan

### Immediate (Before Public Release)

1. **[HIGH] Authenticate `/status` endpoint** (Finding 1.2)
   - Add `validateSession()` check, or return minimal unauthenticated response
   - Update dashboard auto-login to use `/auth/refresh` instead
   - Estimated effort: 1-2 hours

2. **[MEDIUM] Move DebugReceiver to debug manifest** (Finding 5.1)
   - Create `src/debug/AndroidManifest.xml`
   - Estimated effort: 30 minutes

3. **[MEDIUM] Add CSP and security headers** (Finding 7.1)
   - Add headers to Ktor dashboard routes and relay static asset responses
   - Estimated effort: 1 hour

### Short-Term (Next Release)

4. **[HIGH] Timing-safe PIN comparison** (Finding 1.1)
   - Replace `==` with `MessageDigest.isEqual()`
   - Estimated effort: 15 minutes

5. **[MEDIUM] Add CORS to Ktor server** (Finding 2.1)
   - Install Ktor CORS plugin
   - Estimated effort: 30 minutes

6. **[MEDIUM] Fix relay allowlist for time-limits** (Finding 6.1)
   - Add time-limits routes and PUT to valid methods
   - Estimated effort: 1 hour

7. **[LOW] Clear PIN from URL after auto-login** (Finding 7.2)
   - Add `history.replaceState` call
   - Estimated effort: 5 minutes

### Long-Term (Defense-in-Depth)

8. **[LOW] Enable R8 for release builds** (Finding 5.2) — Estimated effort: 2-4 hours
9. **[LOW] Use EncryptedSharedPreferences** (Finding 4.1) — Estimated effort: 1-2 hours
10. **[LOW] Restrict cleartext traffic** (Finding 2.2) — Estimated effort: 30 minutes

---

## Positive Security Practices Observed

1. **Local-first architecture** — No cloud dependency, no user accounts, no PII collection
2. **SecureRandom for all secret generation** — Tokens (256-bit), tvSecret (256-bit), PIN (6-digit)
3. **Timing-safe comparison on relay** — Proper `crypto.subtle.timingSafeEqual`
4. **Path allowlist on relay** — Prevents generic proxy abuse
5. **Rate limiting at multiple layers** — PIN lockout (TV), per-tvId (relay), per-token (refresh)
6. **Session invalidation on PIN reset** — Plus tvSecret rotation
7. **Exponential backoff** — On both PIN lockout and WebSocket reconnection
8. **Room parameterized queries** — No SQL injection surface
9. **XSS-safe HTML rendering** — Consistent `escapeHtml()` / `textContent`
10. **Minimal permissions** — Only INTERNET, WIFI_STATE, NETWORK_STATE
11. **YouTube domain allowlist** — Rejects non-YouTube URLs
12. **Private IP blocking** — Rejects localhost and RFC 1918 addresses
13. **Max session cap** — 20 concurrent sessions
14. **Payload size limits** — 10KB request, 100KB response on relay
15. **Header forwarding subset** — Relay only forwards Authorization, Content-Type, Accept

---

## Appendix: Threat Model Summary

### In-Scope Attackers

| Attacker | Access | Goal | Primary Mitigations |
|----------|--------|------|---------------------|
| Child (intended user) | Physical access to TV + D-pad | Bypass parental controls | PIN auth, time limits, lock screen |
| LAN neighbor | Same WiFi network | Snoop on viewing, control TV | PIN auth, rate limiting |
| Internet attacker | Knows tvId (UUID) | Remote control, data exfiltration | tvSecret auth, relay allowlist, rate limiting |
| Malicious app on TV | Installed on same device | Extract secrets, control playback | Android app sandbox, `MODE_PRIVATE` SharedPrefs |

### Out-of-Scope

- Root/ADB access (requires physical access + developer knowledge)
- Cloudflare account compromise (infrastructure-level)
- Device theft (physical security)
- YouTube-side attacks (content moderation is out of scope)

---

*Review completed 2026-02-20.*
