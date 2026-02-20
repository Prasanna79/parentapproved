#!/bin/bash
# ParentApproved V0.7 — Automated UI Tests
# Uses: uiautomator dump, adb input keyevent, logcat verification, debug intents
# Requires: app installed and running on connected emulator/device
set -euo pipefail

export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-/Users/prasanna/.android}"
ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"
PKG="tv.parentapproved.app"
TAG="ParentApproved-Intent"
PASS=0
FAIL=0
TMPDIR=$(mktemp -d)

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

cleanup() {
    rm -rf "$TMPDIR"
}
trap cleanup EXIT

# --- Helpers ---

dump_ui() {
    $ADB shell uiautomator dump /sdcard/ui.xml 2>/dev/null || true
    $ADB pull /sdcard/ui.xml "$TMPDIR/ui.xml" 2>/dev/null || true
}

ui_contains() {
    local text="$1"
    if grep -qi "$text" "$TMPDIR/ui.xml" 2>/dev/null; then
        return 0
    fi
    return 1
}

assert_ui_text() {
    local description="$1"
    local text="$2"
    dump_ui
    if ui_contains "$text"; then
        echo -e "${GREEN}PASS${NC}: $description (found '$text')"
        PASS=$((PASS + 1))
    else
        echo -e "${RED}FAIL${NC}: $description (expected '$text')"
        FAIL=$((FAIL + 1))
    fi
}

assert_ui_not_text() {
    local description="$1"
    local text="$2"
    dump_ui
    if ! ui_contains "$text"; then
        echo -e "${GREEN}PASS${NC}: $description ('$text' not present)"
        PASS=$((PASS + 1))
    else
        echo -e "${RED}FAIL${NC}: $description ('$text' should not be present)"
        FAIL=$((FAIL + 1))
    fi
}

assert_logcat() {
    local description="$1"
    local pattern="$2"
    local timeout="${3:-5}"
    local found=false
    for i in $(seq 1 "$timeout"); do
        if $ADB logcat -d -s "$TAG" 2>/dev/null | grep -q "$pattern"; then
            found=true
            break
        fi
        sleep 1
    done
    if $found; then
        echo -e "${GREEN}PASS${NC}: $description (logcat: '$pattern')"
        PASS=$((PASS + 1))
    else
        echo -e "${RED}FAIL${NC}: $description (logcat pattern '$pattern' not found)"
        FAIL=$((FAIL + 1))
    fi
}

send_intent() {
    local action="$1"
    shift
    $ADB shell am broadcast -a "$PKG.$action" -n "$PKG/.debug.DebugReceiver" "$@" > /dev/null 2>&1
    sleep 1
}

press_key() {
    $ADB shell input keyevent "$1"
    sleep 0.5
}

tap_text() {
    local text="$1"
    dump_ui
    local BOUNDS
    BOUNDS=$(grep -o "text=\"$text\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" "$TMPDIR/ui.xml" 2>/dev/null | grep -o 'bounds="[^"]*"' | head -1 || echo "")
    if [ -z "$BOUNDS" ]; then
        # Try content-desc
        BOUNDS=$(grep -o "content-desc=\"$text\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" "$TMPDIR/ui.xml" 2>/dev/null | grep -o 'bounds="[^"]*"' | head -1 || echo "")
    fi
    if [ -n "$BOUNDS" ]; then
        local X1 Y1 X2 Y2 CX CY
        X1=$(echo "$BOUNDS" | grep -o '\[[0-9]*,' | head -1 | tr -d '[,')
        Y1=$(echo "$BOUNDS" | grep -o ',[0-9]*\]' | head -1 | tr -d ',]')
        X2=$(echo "$BOUNDS" | grep -o '\[[0-9]*,' | tail -1 | tr -d '[,')
        Y2=$(echo "$BOUNDS" | grep -o ',[0-9]*\]' | tail -1 | tr -d ',]')
        CX=$(( (X1 + X2) / 2 ))
        CY=$(( (Y1 + Y2) / 2 ))
        $ADB shell input tap $CX $CY
        sleep 2
        return 0
    fi
    return 1
}

# Key codes
DPAD_CENTER=23
BACK=4
KEY_HOME=3

# --- Setup ---

echo "=== ParentApproved Automated UI Tests (v0.7) ==="
echo ""

# Force-stop and restart the app for a clean state
echo -e "${YELLOW}Setting up...${NC}"
$ADB shell am force-stop "$PKG"
sleep 1
$ADB shell pm clear "$PKG" 2>/dev/null || true
sleep 1

# Start the app
$ADB shell am start -n "$PKG/.MainActivity"
sleep 5

# Clear logcat
$ADB logcat -c

# === Test 1: Home screen (empty state) ===
echo ""
echo "--- Test 1: Home Screen (empty state) ---"
assert_ui_text "Home screen shows app title" "ParentApproved"
assert_ui_text "Empty state shows 'No videos' message" "No videos"
assert_ui_text "Connect Phone button visible" "Connect Phone"
assert_ui_text "Settings icon has content-desc" "Settings"
assert_ui_text "Refresh icon has content-desc" "Refresh Videos"

# === Test 2: Connect Screen ===
echo ""
echo "--- Test 2: Connect Screen ---"
tap_text "Connect Phone"
sleep 2

assert_ui_text "Connect screen shows branding" "ParentApproved"
assert_ui_text "Connect screen shows 'One-time setup'" "One-time setup"
assert_ui_text "Connect screen shows 'Scan'" "Scan"
assert_ui_text "Connect screen shows 'Or enter:'" "Or enter"
assert_ui_text "Connect screen has Back button" "Back"

# Get the PIN from the connect screen
dump_ui
PIN=$(grep -o 'text="[0-9]\{6\}"' "$TMPDIR/ui.xml" 2>/dev/null | head -1 | sed 's/text="//;s/"//' || echo "")
if [ -n "$PIN" ]; then
    echo -e "${GREEN}PASS${NC}: PIN visible on connect screen: $PIN"
    PASS=$((PASS + 1))
else
    echo -e "${YELLOW}INFO${NC}: Could not extract PIN from UI dump, will get via intent"
    send_intent "DEBUG_GET_PIN"
    PIN=$($ADB logcat -d -s "$TAG" | grep -o '"pin":"[0-9]*"' | tail -1 | sed 's/"pin":"//;s/"//' || echo "123456")
    echo "  PIN from intent: $PIN"
fi

# Test Back navigates to home
tap_text "Back"
sleep 2
assert_ui_text "Back button returns to home screen" "ParentApproved"
assert_ui_not_text "Back button left connect screen" "One-time setup"

# === Test 3: Add playlist via intent ===
echo ""
echo "--- Test 3: Add playlist via intent ---"
$ADB logcat -c
send_intent "DEBUG_ADD_PLAYLIST" --es url "https://www.youtube.com/playlist?list=PLRqwX-V7Uu6ZiZxtDDRCi6uhfTH4FilpH"
sleep 4  # Give DB write time to complete

assert_logcat "Playlist added via intent" '"sourceId"'

# Verify playlist was persisted to DB (intent catalog test also verifies this)
# Note: Home screen won't show unresolved playlists with 0 videos in the video grid
$ADB logcat -c
send_intent "DEBUG_GET_PLAYLISTS"
assert_logcat "Playlist persisted in DB" "PLRqwX"

# === Test 4: Settings screen via tap ===
echo ""
echo "--- Test 4: Settings Screen ---"
# Settings is an icon-only button with content-desc="Settings" — find and tap it
dump_ui
SETTINGS_BOUNDS=$(grep -o 'content-desc="Settings"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' "$TMPDIR/ui.xml" 2>/dev/null | grep -o 'bounds="[^"]*"' | head -1 || echo "")
if [ -n "$SETTINGS_BOUNDS" ]; then
    X1=$(echo "$SETTINGS_BOUNDS" | grep -o '\[[0-9]*,' | head -1 | tr -d '[,')
    Y1=$(echo "$SETTINGS_BOUNDS" | grep -o ',[0-9]*\]' | head -1 | tr -d ',]')
    X2=$(echo "$SETTINGS_BOUNDS" | grep -o '\[[0-9]*,' | tail -1 | tr -d '[,')
    Y2=$(echo "$SETTINGS_BOUNDS" | grep -o ',[0-9]*\]' | tail -1 | tr -d ',]')
    CX=$(( (X1 + X2) / 2 ))
    CY=$(( (Y1 + Y2) / 2 ))
    $ADB shell input tap $CX $CY
    sleep 3
    assert_ui_text "Settings screen shows Connection section" "Connection"
    assert_ui_text "Settings screen shows PIN" "PIN"
    assert_ui_text "Settings screen shows Debug section" "Debug"
    assert_ui_text "Settings screen has Reset PIN button" "Reset PIN"
    assert_ui_text "Settings screen has Clear Sessions button" "Clear Sessions"
    press_key $BACK
    sleep 2
else
    echo -e "${RED}FAIL${NC}: Settings button not found in UI dump"
    FAIL=$((FAIL + 5))
fi

# === Test 5: HTTP Server ===
echo ""
echo "--- Test 5: HTTP Server ---"
# Re-establish port forward (may have been lost during force-stop)
$ADB forward --remove tcp:8080 2>/dev/null || true
$ADB forward tcp:8080 tcp:8080 2>/dev/null || true
sleep 2

# Re-fetch PIN
$ADB logcat -c
send_intent "DEBUG_GET_PIN"
PIN=$($ADB logcat -d -s "$TAG" | grep -o '"pin":"[0-9]*"' | tail -1 | sed 's/"pin":"//;s/"//' || echo "$PIN")
echo "  Current PIN: $PIN"

STATUS=$(curl -sf http://localhost:8080/status 2>/dev/null || echo "")
if echo "$STATUS" | python3 -c "import sys,json; d=json.loads(sys.stdin.read()); assert d.get('serverRunning')==True" 2>/dev/null; then
    echo -e "${GREEN}PASS${NC}: HTTP /status returns serverRunning=true"
    PASS=$((PASS + 1))
else
    echo -e "${RED}FAIL${NC}: HTTP /status failed (got: $STATUS)"
    FAIL=$((FAIL + 1))
fi

AUTH_RESP=$(curl -sf -X POST http://localhost:8080/auth \
    -H "Content-Type: application/json" \
    -d "{\"pin\":\"$PIN\"}" 2>/dev/null || echo "")
TOKEN=$(echo "$AUTH_RESP" | python3 -c "import sys,json; print(json.loads(sys.stdin.read()).get('token',''))" 2>/dev/null || echo "")

if [ -n "$TOKEN" ] && [ "$TOKEN" != "" ] && [ "$TOKEN" != "None" ]; then
    echo -e "${GREEN}PASS${NC}: HTTP auth succeeded, got token"
    PASS=$((PASS + 1))

    PL_RESP=$(curl -sf http://localhost:8080/playlists \
        -H "Authorization: Bearer $TOKEN" 2>/dev/null || echo "[]")
    PL_COUNT=$(echo "$PL_RESP" | python3 -c "import sys,json; print(len(json.loads(sys.stdin.read())))" 2>/dev/null || echo "0")
    if [ "$PL_COUNT" -ge 1 ]; then
        echo -e "${GREEN}PASS${NC}: HTTP /playlists returns $PL_COUNT playlist(s)"
        PASS=$((PASS + 1))
    else
        echo -e "${RED}FAIL${NC}: HTTP /playlists expected >= 1, got $PL_COUNT"
        FAIL=$((FAIL + 1))
    fi

    STATS_RESP=$(curl -sf http://localhost:8080/stats \
        -H "Authorization: Bearer $TOKEN" 2>/dev/null || echo "")
    if echo "$STATS_RESP" | python3 -c "import sys,json; d=json.loads(sys.stdin.read()); assert 'totalEventsAllTime' in d" 2>/dev/null; then
        echo -e "${GREEN}PASS${NC}: HTTP /stats returns valid stats"
        PASS=$((PASS + 1))
    else
        echo -e "${RED}FAIL${NC}: HTTP /stats failed"
        FAIL=$((FAIL + 1))
    fi

    DASH=$(curl -sf http://localhost:8080/ 2>/dev/null || echo "")
    if echo "$DASH" | grep -q "ParentApproved.tv"; then
        echo -e "${GREEN}PASS${NC}: HTTP / serves dashboard HTML"
        PASS=$((PASS + 1))
    else
        echo -e "${RED}FAIL${NC}: HTTP / did not serve dashboard"
        FAIL=$((FAIL + 1))
    fi
else
    echo -e "${RED}FAIL${NC}: HTTP auth failed (response: $AUTH_RESP)"
    FAIL=$((FAIL + 4))
fi

# === Test 6: Intent catalog smoke test ===
echo ""
echo "--- Test 6: Intent Catalog Smoke Test ---"

$ADB logcat -c
send_intent "DEBUG_GET_PIN"
assert_logcat "GET_PIN returns pin" '"pin"'

send_intent "DEBUG_GET_AUTH_STATE"
assert_logcat "GET_AUTH_STATE returns sessions" '"sessions"'

send_intent "DEBUG_GET_PLAYLISTS"
assert_logcat "GET_PLAYLISTS returns array" "PLRqwX"

send_intent "DEBUG_GET_NOW_PLAYING"
assert_logcat "GET_NOW_PLAYING returns playing" '"playing"'

$ADB logcat -c
send_intent "DEBUG_GET_SERVER_STATUS"
assert_logcat "GET_SERVER_STATUS returns running" '"running"'

$ADB logcat -c
send_intent "DEBUG_SIMULATE_OFFLINE"
assert_logcat "SIMULATE_OFFLINE toggles" '"offline"'
send_intent "DEBUG_SIMULATE_OFFLINE"

$ADB logcat -c
send_intent "DEBUG_GET_STATE_DUMP"
assert_logcat "GET_STATE_DUMP returns complete state" '"sources"'

# === Test 7: Time Limit Intents ===
echo ""
echo "--- Test 7: Time Limit Intent Smoke Test ---"

$ADB logcat -c
send_intent "DEBUG_TIME_STATUS"
assert_logcat "TIME_STATUS returns status" '"status"'

$ADB logcat -c
send_intent "DEBUG_SET_DAILY_LIMIT" --ei minutes 60
assert_logcat "SET_DAILY_LIMIT sets limit" '"success":true'

$ADB logcat -c
send_intent "DEBUG_TIME_STATUS"
assert_logcat "TIME_STATUS shows limit applied" '"status"'

$ADB logcat -c
send_intent "DEBUG_CLEAR_DAILY_LIMIT"
assert_logcat "CLEAR_DAILY_LIMIT succeeds" '"success":true'

$ADB logcat -c
send_intent "DEBUG_SET_BEDTIME" --es start "22:00" --es end "07:00"
assert_logcat "SET_BEDTIME succeeds" '"success":true'

$ADB logcat -c
send_intent "DEBUG_CLEAR_BEDTIME"
assert_logcat "CLEAR_BEDTIME succeeds" '"success":true'

$ADB logcat -c
send_intent "DEBUG_GRANT_BONUS" --ei minutes 15
assert_logcat "GRANT_BONUS succeeds" '"success":true'

# === Test 8: Manual Lock → Lock Screen ===
echo ""
echo "--- Test 8: Lock Screen ---"

# Set manual lock via intent
$ADB logcat -c
sleep 1
send_intent "DEBUG_MANUAL_LOCK"
sleep 1
assert_logcat "MANUAL_LOCK succeeds" '"locked":true' 5

# Restart app — HomeScreen's initial canPlay() check triggers lock screen
$ADB shell am force-stop "$PKG"
sleep 2
$ADB shell am start -n "$PKG/.MainActivity"
sleep 10

assert_ui_text "Lock screen shows 'Taking a break'" "Taking a break"
assert_ui_text "Lock screen shows 'ask your parent'" "Ask your parent"
assert_ui_text "Lock screen shows 'Request More Time'" "Request More Time"

# Test Back button does nothing on lock screen
press_key $BACK
sleep 1
assert_ui_text "Back key doesn't exit lock screen" "Taking a break"

# Test Home button returns to lock screen
press_key $KEY_HOME
sleep 2
$ADB shell am start -n "$PKG/.MainActivity"
sleep 10
assert_ui_text "Home button re-check lands on lock" "Taking a break"

# Unlock via intent
$ADB logcat -c
sleep 1
send_intent "DEBUG_MANUAL_UNLOCK"
assert_logcat "MANUAL_UNLOCK succeeds" '"locked":false' 5

# Lock screen polls canPlay() every 5s — wait for navigation back to home
sleep 8
assert_ui_text "Unlock returns to home" "ParentApproved"
assert_ui_not_text "Lock screen gone after unlock" "Taking a break"

# === Test 9: Time Limit HTTP API ===
echo ""
echo "--- Test 9: Time Limit HTTP API ---"

if [ -n "$TOKEN" ] && [ "$TOKEN" != "None" ]; then
    # Get time limits (default)
    TL_RESP=$(curl -sf http://localhost:8080/time-limits \
        -H "Authorization: Bearer $TOKEN" 2>/dev/null || echo "")
    if echo "$TL_RESP" | python3 -c "import sys,json; d=json.loads(sys.stdin.read()); assert d.get('currentStatus')=='allowed'" 2>/dev/null; then
        echo -e "${GREEN}PASS${NC}: GET /time-limits returns allowed"
        PASS=$((PASS + 1))
    else
        echo -e "${RED}FAIL${NC}: GET /time-limits failed (got: $TL_RESP)"
        FAIL=$((FAIL + 1))
    fi

    # Set daily limit via HTTP
    PUT_RESP=$(curl -sf -X PUT http://localhost:8080/time-limits \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        -d '{"dailyLimits":{"monday":60,"tuesday":60,"wednesday":60,"thursday":60,"friday":60,"saturday":120,"sunday":120}}' 2>/dev/null || echo "")
    if echo "$PUT_RESP" | python3 -c "import sys,json; d=json.loads(sys.stdin.read()); assert d.get('success')=='true'" 2>/dev/null; then
        echo -e "${GREEN}PASS${NC}: PUT /time-limits succeeds"
        PASS=$((PASS + 1))
    else
        echo -e "${RED}FAIL${NC}: PUT /time-limits failed (got: $PUT_RESP)"
        FAIL=$((FAIL + 1))
    fi

    # Lock via HTTP
    LOCK_RESP=$(curl -sf -X POST http://localhost:8080/time-limits/lock \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        -d '{"locked":true}' 2>/dev/null || echo "")
    if echo "$LOCK_RESP" | python3 -c "import sys,json; d=json.loads(sys.stdin.read()); assert d.get('success')=='true'" 2>/dev/null; then
        echo -e "${GREEN}PASS${NC}: POST /time-limits/lock succeeds"
        PASS=$((PASS + 1))
    else
        echo -e "${RED}FAIL${NC}: POST /time-limits/lock failed"
        FAIL=$((FAIL + 1))
    fi

    # Verify locked via GET
    TL_RESP2=$(curl -sf http://localhost:8080/time-limits \
        -H "Authorization: Bearer $TOKEN" 2>/dev/null || echo "")
    if echo "$TL_RESP2" | python3 -c "import sys,json; d=json.loads(sys.stdin.read()); assert d.get('manuallyLocked')==True" 2>/dev/null; then
        echo -e "${GREEN}PASS${NC}: GET confirms manuallyLocked=true"
        PASS=$((PASS + 1))
    else
        echo -e "${RED}FAIL${NC}: GET didn't show locked (got: $TL_RESP2)"
        FAIL=$((FAIL + 1))
    fi

    # Grant bonus via HTTP
    BONUS_RESP=$(curl -sf -X POST http://localhost:8080/time-limits/bonus \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        -d '{"minutes":15}' 2>/dev/null || echo "")
    if echo "$BONUS_RESP" | python3 -c "import sys,json; d=json.loads(sys.stdin.read()); assert d.get('success')==True" 2>/dev/null; then
        echo -e "${GREEN}PASS${NC}: POST /time-limits/bonus succeeds"
        PASS=$((PASS + 1))
    else
        echo -e "${RED}FAIL${NC}: POST /time-limits/bonus failed (got: $BONUS_RESP)"
        FAIL=$((FAIL + 1))
    fi

    # Unlock for cleanup
    curl -sf -X POST http://localhost:8080/time-limits/lock \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        -d '{"locked":false}' > /dev/null 2>&1 || true
else
    echo -e "${YELLOW}SKIP${NC}: No auth token, skipping HTTP time limit tests"
    FAIL=$((FAIL + 5))
fi

# === Cleanup ===
echo ""
echo "--- Cleanup ---"
$ADB logcat -c
send_intent "DEBUG_MANUAL_UNLOCK"
send_intent "DEBUG_CLEAR_DAILY_LIMIT"
send_intent "DEBUG_CLEAR_BEDTIME"
send_intent "DEBUG_FULL_RESET"
assert_logcat "FULL_RESET succeeds" '"success"'

# === Summary ===
echo ""
echo "=== UI Test Results ==="
echo -e "${GREEN}PASS: $PASS${NC}"
if [ $FAIL -gt 0 ]; then
    echo -e "${RED}FAIL: $FAIL${NC}"
fi
echo "Total: $((PASS + FAIL))"

if [ $FAIL -gt 0 ]; then
    exit 1
fi
