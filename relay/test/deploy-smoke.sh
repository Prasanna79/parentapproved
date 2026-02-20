#!/bin/bash
# ParentApproved Relay — Post-Deploy Smoke Test
# Runs against a live relay URL to verify deployed artifacts serve correct content.
#
# Usage: bash relay/test/deploy-smoke.sh [RELAY_URL]
# Default: https://parentapproved-relay-staging.workers.dev

set -euo pipefail

RELAY_URL="${1:-https://parentapproved-relay-staging.workers.dev}"
TV_ID="e2e-test-$(date +%s)"
PASS=0
FAIL=0
TOTAL=0

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo "=== ParentApproved Relay Deploy Smoke Test ==="
echo "Relay: $RELAY_URL"
echo "TV ID: $TV_ID"
echo ""

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

TV_BASE="$RELAY_URL/tv/$TV_ID"

# === Original e2e-smoke checks ===
echo "--- Basic relay checks ---"

# 1. Dashboard loads (200)
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$TV_BASE/")
check_status "Dashboard loads (200)" "200" "$STATUS"

# 2. No TV connected → 503
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer fake-token" \
    "$TV_BASE/api/status")
check_status "No TV → 503 on /api/status" "503" "$STATUS"

# 3. Non-allowlisted path → 404
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    "$TV_BASE/api/admin/users")
check_status "Non-allowlisted path → 404" "404" "$STATUS"

# 4. Oversized payload → 413
LARGE_BODY=$(python3 -c "print('x' * 20000)")
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST \
    -H "Content-Type: application/json" \
    -H "Content-Length: 20000" \
    -d "$LARGE_BODY" \
    "$TV_BASE/api/playlists")
check_status "Oversized payload → 413" "413" "$STATUS"

# 5. Disallowed method → 405
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X PUT \
    "$TV_BASE/api/playlists")
check_status "PUT on POST/GET-only → 405" "405" "$STATUS"

# === Dashboard content checks ===
echo ""
echo "--- Dashboard HTML content ---"
DASH_HEADERS=$(curl -sI "$TV_BASE/" 2>/dev/null || echo '')
DASH_BODY=$(curl -sf "$TV_BASE/" 2>/dev/null || echo '')

REQUIRED_IDS=("auth-screen" "dashboard" "screen-time-section" "playlists-section" "stats-section" "recent-section" "now-playing" "edit-limits-modal")
for id in "${REQUIRED_IDS[@]}"; do
    check_contains "HTML has #$id" "$DASH_BODY" "id=\"$id\""
done

# Security headers
check_header "CSP header" "$DASH_HEADERS" "Content-Security-Policy"
check_header "X-Content-Type-Options" "$DASH_HEADERS" "X-Content-Type-Options"
check_header "X-Frame-Options" "$DASH_HEADERS" "X-Frame-Options"
check_header "Referrer-Policy" "$DASH_HEADERS" "Referrer-Policy"

# === Dashboard JS ===
echo ""
echo "--- Dashboard JS ---"
JS_BODY=$(curl -sf "$TV_BASE/app.js" 2>/dev/null || echo '')
JS_FUNCTIONS=("loadTimeLimits" "toggleLock" "grantBonusTime" "openEditLimits" "saveLimits" "loadDashboard" "refreshToken")
for fn in "${JS_FUNCTIONS[@]}"; do
    check_contains "JS has $fn" "$JS_BODY" "$fn"
done

# === Dashboard CSS ===
echo ""
echo "--- Dashboard CSS ---"
CSS_BODY=$(curl -sf "$TV_BASE/style.css" 2>/dev/null || echo '')
CSS_SELECTORS=(".screen-time-card" "#st-lock-btn" ".modal-overlay" ".st-controls")
for sel in "${CSS_SELECTORS[@]}"; do
    check_contains "CSS has $sel" "$CSS_BODY" "$sel"
done

# === Favicon ===
echo ""
echo "--- Favicon ---"
FAV_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$TV_BASE/favicon.svg")
check_status "favicon.svg returns 200" "200" "$FAV_STATUS"

# === Time-limit routes forwarded (503, not 404) ===
echo ""
echo "--- Time-limit routes in allowlist ---"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer fake" "$TV_BASE/api/time-limits")
check_status "GET /api/time-limits → 503 (not 404)" "503" "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST -H "Content-Type: application/json" -d '{"locked":true}' \
    "$TV_BASE/api/time-limits/lock")
check_status "POST /api/time-limits/lock → 503" "503" "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST -H "Content-Type: application/json" -d '{"minutes":15}' \
    "$TV_BASE/api/time-limits/bonus")
check_status "POST /api/time-limits/bonus → 503" "503" "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X PUT -H "Content-Type: application/json" -d '{}' \
    "$TV_BASE/api/time-limits")
check_status "PUT /api/time-limits → 503" "503" "$STATUS"

# === Auth route ===
echo ""
echo "--- Auth route ---"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST -H "Content-Type: application/json" -d '{"pin":"123456"}' \
    "$TV_BASE/api/auth")
check_status "POST /api/auth → 503 (no TV)" "503" "$STATUS"

# === PATCH method blocked ===
echo ""
echo "--- Method enforcement ---"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X PATCH "$TV_BASE/api/playlists")
check_status "PATCH → 405" "405" "$STATUS"

# === Summary ===
echo ""
echo "=== Results: $PASS/$TOTAL passed, $FAIL failed ==="

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
