#!/bin/bash
# KidsWatch Relay — E2E Smoke Test
# Run against a deployed staging relay to verify basic functionality.
#
# Usage: bash relay/test/e2e-smoke.sh [RELAY_URL]
# Default: https://kidswatch-relay-staging.workers.dev

set -euo pipefail

RELAY_URL="${1:-https://kidswatch-relay-staging.workers.dev}"
TV_ID="e2e-test-$(date +%s)"
PASS=0
FAIL=0

echo "=== KidsWatch Relay E2E Smoke Test ==="
echo "Relay: $RELAY_URL"
echo "TV ID: $TV_ID"
echo ""

check() {
    local name="$1"
    local expected_status="$2"
    local actual_status="$3"

    if [ "$actual_status" = "$expected_status" ]; then
        echo "PASS: $name (got $actual_status)"
        PASS=$((PASS + 1))
    else
        echo "FAIL: $name (expected $expected_status, got $actual_status)"
        FAIL=$((FAIL + 1))
    fi
}

# Test 1: Dashboard loads (200, HTML)
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$RELAY_URL/tv/$TV_ID/")
check "Dashboard loads" "200" "$STATUS"

# Test 2: No TV connected → 503
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer fake-token" \
    "$RELAY_URL/tv/$TV_ID/api/status")
check "No TV → 503" "503" "$STATUS"

# Test 3: Non-allowlisted path → 404
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    "$RELAY_URL/tv/$TV_ID/api/admin/users")
check "Non-allowlisted path → 404" "404" "$STATUS"

# Test 4: Oversized payload → 413
LARGE_BODY=$(python3 -c "print('x' * 20000)")
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST \
    -H "Content-Type: application/json" \
    -H "Content-Length: 20000" \
    -d "$LARGE_BODY" \
    "$RELAY_URL/tv/$TV_ID/api/playlists")
check "Oversized payload → 413" "413" "$STATUS"

# Test 5: CONNECT method → 405
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X PUT \
    "$RELAY_URL/tv/$TV_ID/api/playlists")
check "Disallowed method → 405" "405" "$STATUS"

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
