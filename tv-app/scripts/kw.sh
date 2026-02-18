#!/bin/bash
# kw.sh — ParentApproved debug helper
# Wraps ADB debug intents into simple subcommands with pretty JSON output.
#
# Usage: bash scripts/kw.sh <command>
#   pin          Get current PIN
#   reset-pin    Reset PIN + rotate secret
#   status       Server status
#   relay        Relay connection status
#   playlists    List playlists
#   playing      Now playing info
#   state        Full state dump
#   log          Live tail ParentApproved-Intent logcat
#
# Device selection: DEVICE=192.168.0.101:5555 bash scripts/kw.sh relay
# Default: no -s flag (uses whatever's connected)

ADB="/opt/homebrew/share/android-commandlinetools/platform-tools/adb"
INTENT_PREFIX="tv.parentapproved.app"
TAG="ParentApproved-Intent"

if [ -n "$DEVICE" ]; then
    ADB="$ADB -s $DEVICE"
fi

broadcast() {
    local action="$1"
    # Clear logcat, send broadcast, capture output
    $ADB logcat -c 2>/dev/null
    $ADB shell am broadcast -a "${INTENT_PREFIX}.${action}" >/dev/null 2>&1
    # Give the broadcast a moment to execute
    sleep 0.5
    # Grab the JSON output from logcat
    $ADB logcat -d -s "$TAG" | grep -o '{.*}' | head -1
}

case "${1:-help}" in
    pin)
        broadcast "DEBUG_GET_PIN"
        ;;
    reset-pin)
        broadcast "DEBUG_RESET_PIN"
        ;;
    status)
        broadcast "DEBUG_GET_STATUS"
        ;;
    relay)
        broadcast "DEBUG_GET_RELAY_STATUS"
        ;;
    playlists)
        broadcast "DEBUG_GET_PLAYLISTS"
        ;;
    playing)
        broadcast "DEBUG_GET_NOW_PLAYING"
        ;;
    state)
        broadcast "DEBUG_GET_STATE"
        ;;
    log)
        echo "Tailing $TAG... (Ctrl+C to stop)"
        $ADB logcat -s "$TAG":D
        ;;
    help|*)
        echo "Usage: bash scripts/kw.sh <command>"
        echo ""
        echo "Commands:"
        echo "  pin          Get current PIN"
        echo "  reset-pin    Reset PIN + rotate secret"
        echo "  status       Server status"
        echo "  relay        Relay connection status"
        echo "  playlists    List playlists"
        echo "  playing      Now playing info"
        echo "  state        Full state dump"
        echo "  log          Live tail ParentApproved-Intent logcat"
        echo ""
        echo "Device: DEVICE=192.168.0.101:5555 bash scripts/kw.sh relay"
        ;;
esac
