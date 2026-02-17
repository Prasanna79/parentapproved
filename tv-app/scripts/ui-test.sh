#!/bin/bash
# KidsWatch V0.2.1 — Automated UI Tests
# Uses: uiautomator dump, adb input keyevent, logcat verification
# Requires: app installed and running on connected emulator/device
set -euo pipefail

export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-/Users/prasanna/.android}"
ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"
PKG="com.kidswatch.tv"
TAG="KidsWatch-Intent"
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

# Key codes
DPAD_UP=19
DPAD_DOWN=20
DPAD_LEFT=21
DPAD_RIGHT=22
DPAD_CENTER=23
BACK=4
KEY_HOME=3

# --- Setup ---

echo "=== KidsWatch Automated UI Tests ==="
echo ""

# Force-stop and restart the app for a clean state
echo -e "${YELLOW}Setting up...${NC}"
$ADB shell am force-stop "$PKG"
sleep 1

# Full reset via intent won't work if app is stopped, so clear data
$ADB shell pm clear "$PKG" 2>/dev/null || true
sleep 1

# Start the app
$ADB shell am start -n "$PKG/.MainActivity"
sleep 5

# Clear logcat
$ADB logcat -c

# --- Test 1: Home screen shows on launch (no playlists yet) ---
echo ""
echo "--- Test 1: Home Screen (empty state) ---"
assert_ui_text "Home screen shows app title" "KidsWatch"
assert_ui_text "Empty state shows 'No playlists' message" "No playlists"
assert_ui_text "Connect Phone button visible" "Connect Phone"
assert_ui_text "Settings button visible" "Settings"
assert_ui_text "Refresh button visible" "Refresh"

# --- Test 2: Connect screen shows QR + PIN ---
echo ""
echo "--- Test 2: Connect Screen ---"

# Use tap on "Connect Phone" button by finding its coordinates in UI dump
dump_ui
CONNECT_BOUNDS=$(grep -o 'text="Connect Phone"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' "$TMPDIR/ui.xml" 2>/dev/null | grep -o 'bounds="[^"]*"' | head -1 || echo "")
if [ -n "$CONNECT_BOUNDS" ]; then
    # Extract center of bounds [x1,y1][x2,y2]
    X1=$(echo "$CONNECT_BOUNDS" | grep -o '\[[0-9]*,' | head -1 | tr -d '[,')
    Y1=$(echo "$CONNECT_BOUNDS" | grep -o ',[0-9]*\]' | head -1 | tr -d ',]')
    X2=$(echo "$CONNECT_BOUNDS" | grep -o '\[[0-9]*,' | tail -1 | tr -d '[,')
    Y2=$(echo "$CONNECT_BOUNDS" | grep -o ',[0-9]*\]' | tail -1 | tr -d ',]')
    CX=$(( (X1 + X2) / 2 ))
    CY=$(( (Y1 + Y2) / 2 ))
    $ADB shell input tap $CX $CY
else
    # Fallback: navigate with D-pad
    press_key $DPAD_UP
    press_key $DPAD_RIGHT
    press_key $DPAD_CENTER
fi
sleep 2

assert_ui_text "Connect screen shows PIN label" "PIN"
assert_ui_text "Connect screen shows 'Connect Your Phone'" "Connect Your Phone"
assert_ui_text "Connect screen shows QR/URL info" "Scan"
assert_ui_text "Connect screen has Back button" "Back"

# Get the PIN while still on connect screen
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

# Test Back button navigates to home
BACK_BOUNDS=$(grep -o 'text="Back"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' "$TMPDIR/ui.xml" 2>/dev/null | grep -o 'bounds="[^"]*"' | head -1 || echo "")
if [ -n "$BACK_BOUNDS" ]; then
    X1=$(echo "$BACK_BOUNDS" | grep -o '\[[0-9]*,' | head -1 | tr -d '[,')
    Y1=$(echo "$BACK_BOUNDS" | grep -o ',[0-9]*\]' | head -1 | tr -d ',]')
    X2=$(echo "$BACK_BOUNDS" | grep -o '\[[0-9]*,' | tail -1 | tr -d '[,')
    Y2=$(echo "$BACK_BOUNDS" | grep -o ',[0-9]*\]' | tail -1 | tr -d ',]')
    CX=$(( (X1 + X2) / 2 ))
    CY=$(( (Y1 + Y2) / 2 ))
    $ADB shell input tap $CX $CY
    sleep 3
    assert_ui_text "Back button returns to home screen" "KidsWatch"
    assert_ui_text "Back button shows home buttons" "Refresh Videos"
    assert_ui_not_text "Back button left connect screen" "Scan the QR"
else
    echo -e "${RED}FAIL${NC}: Back button not found, cannot test navigation"
    FAIL=$((FAIL + 3))
fi

# --- Test 3: Add playlist via intent, verify it shows on Home ---
echo ""
echo "--- Test 3: Add playlist + Home shows rows ---"

$ADB logcat -c
send_intent "DEBUG_ADD_PLAYLIST" --es url "https://www.youtube.com/playlist?list=PLRqwX-V7Uu6ZiZxtDDRCi6uhfTH4FilpH"
sleep 2

assert_logcat "Playlist added via intent" "youtubePlaylistId"

# Force-restart app so home screen reloads with the new playlist
$ADB shell am force-stop "$PKG"
sleep 1
$ADB shell am start -n "$PKG/.MainActivity"
sleep 5

# After adding a playlist, the home screen should show it
assert_ui_text "Home screen shows playlist row" "PLRqwX"

# --- Test 4: Settings screen ---
echo ""
echo "--- Test 4: Settings Screen ---"

# Tap Settings button by finding its coordinates
dump_ui
SETTINGS_BOUNDS=$(grep -o 'text="Settings"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' "$TMPDIR/ui.xml" 2>/dev/null | grep -o 'bounds="[^"]*"' | head -1 || echo "")
if [ -n "$SETTINGS_BOUNDS" ]; then
    X1=$(echo "$SETTINGS_BOUNDS" | grep -o '\[[0-9]*,' | head -1 | tr -d '[,')
    Y1=$(echo "$SETTINGS_BOUNDS" | grep -o ',[0-9]*\]' | head -1 | tr -d ',]')
    X2=$(echo "$SETTINGS_BOUNDS" | grep -o '\[[0-9]*,' | tail -1 | tr -d '[,')
    Y2=$(echo "$SETTINGS_BOUNDS" | grep -o ',[0-9]*\]' | tail -1 | tr -d ',]')
    CX=$(( (X1 + X2) / 2 ))
    CY=$(( (Y1 + Y2) / 2 ))
    $ADB shell input tap $CX $CY
else
    press_key $DPAD_UP
    press_key $DPAD_RIGHT
    press_key $DPAD_RIGHT
    press_key $DPAD_RIGHT
    press_key $DPAD_CENTER
fi
sleep 2

assert_ui_text "Settings screen shows title" "Settings"
assert_ui_text "Settings screen shows Connection section" "Connection"
assert_ui_text "Settings screen shows PIN" "PIN"
assert_ui_text "Settings screen shows Debug section" "Debug"
assert_ui_text "Settings screen has Reset PIN button" "Reset PIN"
assert_ui_text "Settings screen has Clear Sessions button" "Clear Sessions"

# Go back
press_key $BACK
sleep 2

# --- Test 5: HTTP server is running (verify via curl) ---
echo ""
echo "--- Test 5: HTTP Server ---"

$ADB forward tcp:8080 tcp:8080 2>/dev/null || true
sleep 1

# Re-fetch PIN (may have changed after force-stop in Test 3)
$ADB logcat -c
send_intent "DEBUG_GET_PIN"
PIN=$($ADB logcat -d -s "$TAG" | grep -o '"pin":"[0-9]*"' | tail -1 | sed 's/"pin":"//;s/"//' || echo "$PIN")
echo "  Current PIN: $PIN"

# Check /status endpoint
STATUS=$(curl -sf http://localhost:8080/status 2>/dev/null || echo "")
if echo "$STATUS" | python3 -c "import sys,json; d=json.loads(sys.stdin.read()); assert d.get('serverRunning')==True" 2>/dev/null; then
    echo -e "${GREEN}PASS${NC}: HTTP /status returns serverRunning=true"
    PASS=$((PASS + 1))
else
    echo -e "${RED}FAIL${NC}: HTTP /status failed (got: $STATUS)"
    FAIL=$((FAIL + 1))
fi

# Authenticate via HTTP
AUTH_RESP=$(curl -sf -X POST http://localhost:8080/auth \
    -H "Content-Type: application/json" \
    -d "{\"pin\":\"$PIN\"}" 2>/dev/null || echo "")
TOKEN=$(echo "$AUTH_RESP" | python3 -c "import sys,json; print(json.loads(sys.stdin.read()).get('token',''))" 2>/dev/null || echo "")

if [ -n "$TOKEN" ] && [ "$TOKEN" != "" ] && [ "$TOKEN" != "None" ]; then
    echo -e "${GREEN}PASS${NC}: HTTP auth succeeded, got token"
    PASS=$((PASS + 1))

    # Get playlists via HTTP
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

    # Get stats via HTTP
    STATS_RESP=$(curl -sf http://localhost:8080/stats \
        -H "Authorization: Bearer $TOKEN" 2>/dev/null || echo "")
    if echo "$STATS_RESP" | python3 -c "import sys,json; d=json.loads(sys.stdin.read()); assert 'totalEventsAllTime' in d" 2>/dev/null; then
        echo -e "${GREEN}PASS${NC}: HTTP /stats returns valid stats"
        PASS=$((PASS + 1))
    else
        echo -e "${RED}FAIL${NC}: HTTP /stats failed"
        FAIL=$((FAIL + 1))
    fi

    # Dashboard HTML
    DASH=$(curl -sf http://localhost:8080/ 2>/dev/null || echo "")
    if echo "$DASH" | grep -q "KidsWatch Dashboard"; then
        echo -e "${GREEN}PASS${NC}: HTTP / serves dashboard HTML"
        PASS=$((PASS + 1))
    else
        echo -e "${RED}FAIL${NC}: HTTP / did not serve dashboard"
        FAIL=$((FAIL + 1))
    fi
else
    echo -e "${RED}FAIL${NC}: HTTP auth failed (response: $AUTH_RESP)"
    FAIL=$((FAIL + 1))
    # Skip dependent tests
    FAIL=$((FAIL + 3))
fi

# --- Test 6: Full intent catalog smoke test ---
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
# Toggle back
send_intent "DEBUG_SIMULATE_OFFLINE"

$ADB logcat -c
send_intent "DEBUG_GET_STATE_DUMP"
assert_logcat "GET_STATE_DUMP returns complete state" '"playlists"'

# --- Cleanup ---
echo ""
echo "--- Cleanup ---"
$ADB logcat -c
send_intent "DEBUG_FULL_RESET"
assert_logcat "FULL_RESET succeeds" '"success"'

# --- Summary ---
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
