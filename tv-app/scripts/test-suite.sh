#!/bin/bash
# ParentApproved V0.2.1 — ADB Intent + HTTP API Test Suite
# Requires: adb connected device/emulator with app installed and running
set -euo pipefail

export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-/Users/prasanna/.android}"
ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"
PKG="tv.parentapproved.app"
TAG="ParentApproved-Intent"
PASS=0
FAIL=0

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

send_intent() {
    local action="$1"
    shift
    $ADB shell am broadcast -a "$PKG.$action" -n "$PKG/.debug.DebugReceiver" "$@" > /dev/null 2>&1
    sleep 1
    $ADB logcat -d -s "$TAG" | tail -1
}

assert_json_field() {
    local json="$1"
    local field="$2"
    local expected="$3"
    local actual
    actual=$(echo "$json" | python3 -c "import sys,json; print(json.loads(sys.stdin.read().split('$TAG: ')[-1])['$field'])" 2>/dev/null || echo "PARSE_ERROR")
    if [ "$actual" = "$expected" ]; then
        echo -e "${GREEN}PASS${NC}: $field == $expected"
        PASS=$((PASS + 1))
    else
        echo -e "${RED}FAIL${NC}: $field expected '$expected', got '$actual'"
        FAIL=$((FAIL + 1))
    fi
}

assert_json_exists() {
    local json="$1"
    local field="$2"
    local actual
    actual=$(echo "$json" | python3 -c "import sys,json; d=json.loads(sys.stdin.read().split('$TAG: ')[-1]); print('exists' if '$field' in d else 'missing')" 2>/dev/null || echo "PARSE_ERROR")
    if [ "$actual" = "exists" ]; then
        echo -e "${GREEN}PASS${NC}: $field exists"
        PASS=$((PASS + 1))
    else
        echo -e "${RED}FAIL${NC}: $field missing"
        FAIL=$((FAIL + 1))
    fi
}

echo "=== ParentApproved ADB Intent Test Suite ==="
echo ""

# Clear logcat
$ADB logcat -c

# 1. PIN tests
echo "--- PIN/Auth ---"
result=$(send_intent "DEBUG_GET_PIN")
assert_json_exists "$result" "pin"

result=$(send_intent "DEBUG_RESET_PIN")
assert_json_exists "$result" "pin"

PIN=$($ADB logcat -d -s "$TAG" | tail -1 | python3 -c "import sys,json; print(json.loads(sys.stdin.read().split('$TAG: ')[-1])['pin'])" 2>/dev/null)
echo "Current PIN: $PIN"

result=$(send_intent "DEBUG_SIMULATE_AUTH" --es pin "$PIN")
assert_json_field "$result" "valid" "True"

result=$(send_intent "DEBUG_SIMULATE_AUTH" --es pin "000000")
assert_json_field "$result" "valid" "False"

result=$(send_intent "DEBUG_GET_AUTH_STATE")
assert_json_exists "$result" "sessions"

# 2. Playlist tests
echo ""
echo "--- Playlists ---"
result=$(send_intent "DEBUG_GET_PLAYLISTS")
echo "Playlists: $(echo "$result" | grep -o "$TAG:.*" | cut -d' ' -f2-)"

result=$(send_intent "DEBUG_ADD_PLAYLIST" --es url "https://www.youtube.com/playlist?list=PLtest123")
assert_json_exists "$result" "id"

result=$(send_intent "DEBUG_GET_PLAYLISTS")
echo "After add: $(echo "$result" | grep -o "$TAG:.*" | cut -d' ' -f2-)"

result=$(send_intent "DEBUG_REFRESH_PLAYLISTS")
assert_json_exists "$result" "playlist_count"

# 3. Playback
echo ""
echo "--- Playback ---"
result=$(send_intent "DEBUG_GET_NOW_PLAYING")
assert_json_exists "$result" "playing"

result=$(send_intent "DEBUG_STOP_PLAYBACK")
assert_json_field "$result" "success" "True"

# 4. Lifecycle
echo ""
echo "--- Lifecycle ---"
result=$(send_intent "DEBUG_SIMULATE_OFFLINE")
assert_json_exists "$result" "offline"

# Toggle back
send_intent "DEBUG_SIMULATE_OFFLINE" > /dev/null

result=$(send_intent "DEBUG_GET_STATE_DUMP")
assert_json_exists "$result" "playlists"
assert_json_exists "$result" "pin"

result=$(send_intent "DEBUG_CLEAR_PLAY_EVENTS")
assert_json_exists "$result" "deleted"

# 5. HTTP API tests (if port forwarded)
echo ""
echo "--- HTTP API ---"
$ADB forward tcp:8080 tcp:8080 2>/dev/null || true

STATUS=$(curl -s http://localhost:8080/status 2>/dev/null || echo '{"error":"connection failed"}')
if echo "$STATUS" | python3 -c "import sys,json; d=json.loads(sys.stdin.read()); assert d.get('serverRunning')==True" 2>/dev/null; then
    echo -e "${GREEN}PASS${NC}: HTTP /status returns serverRunning=true"
    PASS=$((PASS + 1))
else
    echo -e "${RED}FAIL${NC}: HTTP /status failed"
    FAIL=$((FAIL + 1))
fi

# Full reset
echo ""
echo "--- Cleanup ---"
result=$(send_intent "DEBUG_FULL_RESET")
assert_json_field "$result" "success" "True"

# Summary
echo ""
echo "=== Results ==="
echo -e "${GREEN}PASS: $PASS${NC}"
echo -e "${RED}FAIL: $FAIL${NC}"
echo "Total: $((PASS + FAIL))"

if [ $FAIL -gt 0 ]; then
    exit 1
fi
