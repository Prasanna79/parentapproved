#!/usr/bin/env bash
#
# Export YouTube cookies from Firefox on macOS and push them to the
# Android emulator for the KidsWatch feasibility app to import.
#
# Usage:
#   1. CLOSE Firefox (it locks the cookie DB)
#   2. ./push-cookies.sh
#   3. In the app: Test 2 → "Import" → "YouTube"
#

set -euo pipefail

ADB="${ADB:-/opt/homebrew/share/android-commandlinetools/platform-tools/adb}"

# --- Find Firefox profile ---
FF_DIR="$HOME/Library/Application Support/Firefox/Profiles"
if [ ! -d "$FF_DIR" ]; then
    echo "ERROR: Firefox profiles directory not found at $FF_DIR"
    exit 1
fi

PROFILE=$(find "$FF_DIR" -maxdepth 1 -type d -name "*.default-release" | head -1)
[ -z "$PROFILE" ] && PROFILE=$(find "$FF_DIR" -maxdepth 1 -type d -name "*.default*" | head -1)

if [ -z "$PROFILE" ]; then
    echo "ERROR: No Firefox default profile found"
    ls "$FF_DIR"
    exit 1
fi

COOKIES_DB="$PROFILE/cookies.sqlite"
if [ ! -f "$COOKIES_DB" ]; then
    echo "ERROR: cookies.sqlite not found in $PROFILE"
    exit 1
fi

echo "Firefox profile: $(basename "$PROFILE")"

# --- Copy DB (Firefox locks it while running) ---
TMPDB="/tmp/ff_cookies_copy.sqlite"
cp "$COOKIES_DB" "$TMPDB"
[ -f "${COOKIES_DB}-wal" ] && cp "${COOKIES_DB}-wal" "${TMPDB}-wal"
[ -f "${COOKIES_DB}-shm" ] && cp "${COOKIES_DB}-shm" "${TMPDB}-shm"

# --- Extract YouTube/Google cookies as JSON ---
COOKIE_JSON="/tmp/yt_cookies.json"

# Use sqlite3 + python3 to produce clean JSON
sqlite3 -json "$TMPDB" "
SELECT host, name, value, path, isSecure, isHttpOnly
FROM moz_cookies
WHERE host LIKE '%youtube.com'
   OR host LIKE '%google.com'
   OR host LIKE '%googleapis.com'
ORDER BY host, name;
" | python3 -c "
import json, sys

rows = json.load(sys.stdin)
cookies = []
for r in rows:
    host = r['host']
    if 'youtube' in host:
        url = 'https://www.youtube.com'
    elif 'google' in host:
        url = 'https://www.google.com'
    else:
        url = 'https://' + host.lstrip('.')

    cookies.append({
        'url': url,
        'domain': host,
        'name': r['name'],
        'value': r['value'],
        'path': r.get('path', '/'),
        'secure': bool(r.get('isSecure', 0)),
        'httpOnly': bool(r.get('isHttpOnly', 0)),
    })

print(f'Extracted {len(cookies)} cookies')
with open('$COOKIE_JSON', 'w') as f:
    json.dump(cookies, f)
"

# --- Push to emulator ---
echo "Pushing to emulator..."
$ADB push "$COOKIE_JSON" /data/local/tmp/yt_cookies.json

echo ""
echo "Done! In the app: Test 2 → Import → YouTube"

# Cleanup
rm -f "$TMPDB" "${TMPDB}-wal" "${TMPDB}-shm"
