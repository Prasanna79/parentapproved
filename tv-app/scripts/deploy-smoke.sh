#!/bin/bash
# ParentApproved — Post-Install Deploy Smoke Test
# Verifies the *running app* on emulator serves correct content via HTTP.
#
# Prerequisites:
#   - APK installed and app launched on emulator
#   - Port forwarded: adb forward tcp:8080 tcp:8080
#
# Usage: bash tv-app/scripts/deploy-smoke.sh [DEVICE] [PORT]
# Default device: emulator-5554, default port: 8080

set -euo pipefail

DEVICE="${1:-emulator-5554}"
PORT="${2:-8080}"
BASE="http://localhost:$PORT"
PASS=0
FAIL=0
TOTAL=0

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
ADB_BIN="${ADB:-$ANDROID_HOME/platform-tools/adb}"

adb_cmd() {
    "$ADB_BIN" -s "$DEVICE" "$@"
}

echo "=== ParentApproved Deploy Smoke Test (Emulator) ==="
echo "Base URL: $BASE"
echo ""

# Ensure port forwarding
adb_cmd forward tcp:$PORT tcp:$PORT 2>/dev/null || true

check_status() {
    local name="$1"
    local expected="$2"
    local actual="$3"
    TOTAL=$((TOTAL + 1))
    if [ "$actual" = "$expected" ]; then
        echo -e "${GREEN}PASS${NC}: $name"
        PASS=$((PASS + 1))
    else
        echo -e "${RED}FAIL${NC}: $name (expected $expected, got $actual)"
        FAIL=$((FAIL + 1))
    fi
}

check_contains() {
    local name="$1"
    local body="$2"
    local needle="$3"
    TOTAL=$((TOTAL + 1))
    if echo "$body" | grep -q "$needle"; then
        echo -e "${GREEN}PASS${NC}: $name"
        PASS=$((PASS + 1))
    else
        echo -e "${RED}FAIL${NC}: $name — missing: $needle"
        FAIL=$((FAIL + 1))
    fi
}

check_header() {
    local name="$1"
    local headers="$2"
    local header_name="$3"
    TOTAL=$((TOTAL + 1))
    if echo "$headers" | grep -iq "$header_name"; then
        echo -e "${GREEN}PASS${NC}: $name"
        PASS=$((PASS + 1))
    else
        echo -e "${RED}FAIL${NC}: $name — missing header: $header_name"
        FAIL=$((FAIL + 1))
    fi
}

# --- 1. Status endpoint ---
echo "--- /status ---"
STATUS_BODY=$(curl -sf "$BASE/status" 2>/dev/null || echo '{}')
check_contains "/status has serverRunning" "$STATUS_BODY" '"serverRunning"'
check_contains "/status has version" "$STATUS_BODY" '"version"'

# --- 2. Dashboard HTML ---
echo ""
echo "--- Dashboard HTML ---"
# Use -D to capture headers from GET (Ktor doesn't handle HEAD on /)
DASH_RESPONSE=$(curl -s -D /tmp/kw-smoke-headers "$BASE/" 2>/dev/null || echo '')
DASH_HEADERS=$(cat /tmp/kw-smoke-headers 2>/dev/null || echo '')
DASH_BODY="$DASH_RESPONSE"

REQUIRED_IDS=("auth-screen" "dashboard" "screen-time-section" "playlists-section" "stats-section" "recent-section" "now-playing" "edit-limits-modal")
for id in "${REQUIRED_IDS[@]}"; do
    check_contains "HTML has #$id" "$DASH_BODY" "id=\"$id\""
done

# Security headers
check_header "CSP header" "$DASH_HEADERS" "Content-Security-Policy"
check_header "X-Content-Type-Options" "$DASH_HEADERS" "X-Content-Type-Options"
check_header "X-Frame-Options" "$DASH_HEADERS" "X-Frame-Options"

# --- 3. JavaScript ---
echo ""
echo "--- app.js ---"
JS_BODY=$(curl -sf "$BASE/app.js" 2>/dev/null || echo '')
JS_FUNCTIONS=("loadTimeLimits" "toggleLock" "grantBonusTime" "openEditLimits" "saveLimits" "loadDashboard" "refreshToken")
for fn in "${JS_FUNCTIONS[@]}"; do
    check_contains "JS has $fn" "$JS_BODY" "$fn"
done

# --- 4. CSS ---
echo ""
echo "--- style.css ---"
CSS_BODY=$(curl -sf "$BASE/style.css" 2>/dev/null || echo '')
CSS_SELECTORS=(".screen-time-card" "#st-lock-btn" ".modal-overlay" ".st-controls")
for sel in "${CSS_SELECTORS[@]}"; do
    check_contains "CSS has $sel" "$CSS_BODY" "$sel"
done

# --- 5. Favicon ---
echo ""
echo "--- favicon.svg ---"
FAV_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/favicon.svg" 2>/dev/null || echo '0')
check_status "favicon.svg returns 200" "200" "$FAV_STATUS"

# --- 6. Legacy asset paths ---
echo ""
echo "--- Legacy paths ---"
LEGACY_JS_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/assets/app.js" 2>/dev/null || echo '0')
check_status "/assets/app.js returns 200" "200" "$LEGACY_JS_STATUS"

# --- 7. Auth + API (requires PIN) ---
echo ""
echo "--- Auth + API ---"

# Get PIN via debug intent
adb_cmd logcat -c 2>/dev/null
adb_cmd shell am broadcast -a tv.parentapproved.app.DEBUG_GET_PIN -n "tv.parentapproved.app/.debug.DebugReceiver" > /dev/null 2>&1
sleep 2
PIN=$(adb_cmd logcat -d -s ParentApproved-Intent 2>/dev/null | tail -1 | python3 -c "import sys,json; print(json.loads(sys.stdin.read().split('ParentApproved-Intent: ')[-1])['pin'])" 2>/dev/null || echo '')

if [ -n "$PIN" ]; then
    TOKEN=$(curl -sf -X POST -H "Content-Type: application/json" \
        -d "{\"pin\":\"$PIN\"}" "$BASE/auth" 2>/dev/null \
        | python3 -c "import sys,json; print(json.loads(sys.stdin.read()).get('token',''))" 2>/dev/null || echo '')

    if [ -n "$TOKEN" ]; then
        check_status "POST /auth returns token" "nonempty" "nonempty"

        # Time limits
        TL_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
            -H "Authorization: Bearer $TOKEN" "$BASE/time-limits" 2>/dev/null || echo '0')
        check_status "GET /time-limits returns 200" "200" "$TL_STATUS"

        # Playlists
        PL_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
            -H "Authorization: Bearer $TOKEN" "$BASE/playlists" 2>/dev/null || echo '0')
        check_status "GET /playlists returns 200" "200" "$PL_STATUS"

        # Stats
        ST_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
            -H "Authorization: Bearer $TOKEN" "$BASE/stats" 2>/dev/null || echo '0')
        check_status "GET /stats returns 200" "200" "$ST_STATUS"
    else
        check_status "POST /auth returns token" "nonempty" ""
    fi
else
    echo -e "${RED}SKIP${NC}: Could not get PIN via debug intent — auth tests skipped"
fi

# --- Summary ---
echo ""
echo "=== Results: $PASS/$TOTAL passed, $FAIL failed ==="

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
