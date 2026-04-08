#!/bin/bash
# setup-kiosk.sh — Provision a factory-reset Mi Box 4 as a ParentApproved kiosk
#
# Prerequisites:
#   - Mi Box 4 factory-reset and connected to same WiFi
#   - ADB debugging enabled on Mi Box (Settings > About > tap Build 7 times, then Settings > Developer options > USB debugging)
#   - This script run from tv-app/ directory
#
# Usage:
#   bash scripts/setup-kiosk.sh [MI_BOX_IP] [APK_PATH] [EXTRA_APKS_DIR]
#
# Examples:
#   bash scripts/setup-kiosk.sh 192.168.0.101
#   bash scripts/setup-kiosk.sh 192.168.0.101 app/build/outputs/apk/debug/app-debug.apk
#   bash scripts/setup-kiosk.sh 192.168.0.101 app-release.apk /path/to/extra-apks/

set -euo pipefail

ADB="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}/platform-tools/adb"
PKG="tv.parentapproved.app"
ADMIN_COMPONENT="$PKG/.kiosk.ParentApprovedAdmin"

MI_BOX_IP="${1:-}"
APK_PATH="${2:-app/build/outputs/apk/debug/app-debug.apk}"
EXTRA_APKS_DIR="${3:-}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()   { echo -e "${GREEN}[+]${NC} $1"; }
warn()  { echo -e "${YELLOW}[!]${NC} $1"; }
error() { echo -e "${RED}[x]${NC} $1"; exit 1; }

# --- Step 0: Connect ---
if [ -n "$MI_BOX_IP" ]; then
    log "Connecting to Mi Box at $MI_BOX_IP..."
    $ADB connect "$MI_BOX_IP:5555" 2>/dev/null || true
    sleep 2
fi

log "Waiting for device..."
$ADB wait-for-device

DEVICE=$($ADB get-serialno)
log "Connected to device: $DEVICE"

# --- Step 1: Preflight checks ---
log "Running preflight checks..."

SETUP_COMPLETE=$($ADB shell settings get secure user_setup_complete 2>/dev/null || echo "null")
PROVISIONED=$($ADB shell settings get global device_provisioned 2>/dev/null || echo "null")
HAS_DEVICE_ADMIN=$($ADB shell pm list features 2>/dev/null | grep -c "android.software.device_admin" || echo "0")

log "  user_setup_complete = $SETUP_COMPLETE"
log "  device_provisioned  = $PROVISIONED"
log "  has device_admin    = $HAS_DEVICE_ADMIN"

if [ "$HAS_DEVICE_ADMIN" = "0" ]; then
    error "Device does not support device admin. Cannot proceed."
fi

# Check if already a device owner
EXISTING_OWNER=$($ADB shell dpm list-owners 2>/dev/null | grep -c "Device Owner" || echo "0")
if [ "$EXISTING_OWNER" != "0" ]; then
    warn "Device already has a device owner:"
    $ADB shell dpm list-owners
    echo ""
    warn "If this is ParentApproved, kiosk is already set up. Enable it from the dashboard."
    warn "To re-provision, factory reset the device first."
    exit 0
fi

# Warn if setup is complete (dpm set-device-owner may fail)
if [ "$SETUP_COMPLETE" = "1" ]; then
    warn "Setup wizard has already completed. dpm set-device-owner may fail."
    warn "If it does, factory reset the device and try again."
    echo ""
fi

# --- Step 2: Install ParentApproved APK ---
if [ ! -f "$APK_PATH" ]; then
    error "APK not found at: $APK_PATH"
fi

log "Installing ParentApproved from $APK_PATH..."
$ADB install -r "$APK_PATH"
log "APK installed successfully"

# Verify package is installed
$ADB shell pm list packages | grep -q "$PKG" || error "Package $PKG not found after install!"

# --- Step 3: Set device owner ---
log "Setting device owner..."
RESULT=$($ADB shell dpm set-device-owner "$ADMIN_COMPONENT" 2>&1)
echo "  $RESULT"

if echo "$RESULT" | grep -q "Success"; then
    log "Device owner set successfully!"
else
    error "Failed to set device owner. Output: $RESULT"
fi

# Verify
log "Verifying device owner..."
$ADB shell dpm list-owners

# --- Step 4: Install extra APKs (optional) ---
if [ -n "$EXTRA_APKS_DIR" ] && [ -d "$EXTRA_APKS_DIR" ]; then
    log "Installing extra APKs from $EXTRA_APKS_DIR..."
    for apk in "$EXTRA_APKS_DIR"/*.apk; do
        if [ -f "$apk" ]; then
            BASENAME=$(basename "$apk")
            log "  Installing $BASENAME..."
            $ADB install -r "$apk" || warn "  Failed to install $BASENAME"
        fi
    done
fi

# --- Step 5: Launch ParentApproved ---
log "Launching ParentApproved..."
$ADB shell am start -n "$PKG/.MainActivity"
sleep 2

# --- Step 6: Summary ---
echo ""
echo "============================================="
echo "  ParentApproved Kiosk Setup Complete!"
echo "============================================="
echo ""
echo "Next steps:"
echo "  1. On the TV, scan the QR code with your phone"
echo "  2. Enter the PIN shown on the TV screen"
echo "  3. In the dashboard, go to 'Apps & Kiosk'"
echo "  4. Enable Kiosk Mode"
echo "  5. Toggle on the apps you want kids to access"
echo "  6. Sign into streaming apps (Netflix etc) via TV remote"
echo ""
echo "To undo kiosk (requires factory reset):"
echo "  $ADB shell am broadcast -a android.intent.action.FACTORY_RESET"
echo "  or: Settings > Storage & reset > Factory data reset"
echo ""
