#!/bin/bash
# ParentApproved — Full CI runner
# Usage: ./scripts/ci-run.sh [suite]
#   No args = run everything
#   verify = mandatory gate (all automated tests, no running app needed)
#   unit | instrumented | relay | landing | digest | intent | ui | smoke = run one suite
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_DIR="$(cd "$PROJECT_DIR/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-/Users/prasanna/.android}"
ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

SUITE="${1:-all}"

ensure_emulator() {
    if ! $ADB devices | grep -q "device$"; then
        echo "No emulator/device connected. Starting TV_API34..."
        "$ANDROID_HOME/emulator/emulator" -avd TV_API34 -no-audio -gpu swiftshader_indirect -no-snapshot-load &
        for i in $(seq 1 60); do
            if $ADB shell getprop sys.boot_completed 2>/dev/null | grep -q 1; then
                echo "Emulator booted after $((i * 2))s"
                break
            fi
            sleep 2
        done
    else
        echo "Emulator/device already running."
    fi
}

run_unit() {
    echo -e "${YELLOW}--- Unit Tests ---${NC}"
    cd "$PROJECT_DIR"
    ./gradlew testDebugUnitTest --no-daemon
    echo -e "${GREEN}Unit tests passed!${NC}"
}

run_instrumented() {
    echo -e "${YELLOW}--- Instrumented Tests ---${NC}"
    ensure_emulator
    cd "$PROJECT_DIR"
    ./gradlew connectedDebugAndroidTest --no-daemon
    echo -e "${GREEN}Instrumented tests passed!${NC}"
}

run_relay() {
    echo -e "${YELLOW}--- Relay Tests ---${NC}"
    cd "$REPO_DIR/relay"
    npx vitest run
    echo -e "${GREEN}Relay tests passed!${NC}"
}

run_landing() {
    echo -e "${YELLOW}--- Landing Page Tests ---${NC}"
    cd "$REPO_DIR/marketing/landing-page"
    npx vitest run
    echo -e "${GREEN}Landing page tests passed!${NC}"
}

run_digest() {
    echo -e "${YELLOW}--- Digest Worker Tests ---${NC}"
    cd "$REPO_DIR/marketing/notify-digest"
    npx vitest run
    echo -e "${GREEN}Digest worker tests passed!${NC}"
}

run_intent() {
    echo -e "${YELLOW}--- Intent + HTTP Tests ---${NC}"
    ensure_emulator
    cd "$PROJECT_DIR"
    ./gradlew installDebug --no-daemon
    $ADB shell am start -n tv.parentapproved.app/.MainActivity
    sleep 5
    "$SCRIPT_DIR/test-suite.sh"
}

run_ui() {
    echo -e "${YELLOW}--- UI Tests ---${NC}"
    ensure_emulator
    "$SCRIPT_DIR/ui-test.sh"
}

run_smoke() {
    echo -e "${YELLOW}--- Deploy Smoke Test ---${NC}"
    ensure_emulator
    "$SCRIPT_DIR/deploy-smoke.sh"
    echo -e "${GREEN}Deploy smoke passed!${NC}"
}

run_playwright_smoke() {
    echo -e "${YELLOW}--- Playwright Deploy Smoke Test ---${NC}"
    ensure_emulator
    cd "$SCRIPT_DIR"
    npm install --silent
    npx playwright install chromium --with-deps 2>/dev/null || npx playwright install chromium
    npx playwright test --config playwright-smoke.config.ts
    echo -e "${GREEN}Playwright smoke passed!${NC}"
}

echo "=== ParentApproved CI === (suite: $SUITE)"
echo ""

case "$SUITE" in
    unit)         run_unit ;;
    instrumented) run_instrumented ;;
    relay)        run_relay ;;
    landing)      run_landing ;;
    digest)       run_digest ;;
    intent)       run_intent ;;
    ui)           run_ui ;;
    smoke)        run_smoke ;;
    playwright-smoke) run_playwright_smoke ;;
    verify)
        # Mandatory gate: ALL automated tests. Must pass before any merge or release.
        # Requires emulator running. Does NOT require the app to be launched.
        run_unit
        echo ""
        run_instrumented
        echo ""
        run_relay
        echo ""
        run_landing
        echo ""
        run_digest
        echo ""
        echo -e "${GREEN}=== VERIFY PASSED — all mandatory tests green ===${NC}"
        ;;
    all)
        run_unit
        echo ""
        run_instrumented
        echo ""
        # Relaunch app after instrumented tests
        $ADB shell am start -n tv.parentapproved.app/.MainActivity
        sleep 5
        run_intent
        echo ""
        # Relaunch app
        $ADB shell am start -n tv.parentapproved.app/.MainActivity
        sleep 5
        run_ui
        echo ""
        run_smoke
        echo ""
        run_playwright_smoke
        echo ""
        run_relay
        echo ""
        run_landing
        echo ""
        run_digest
        echo ""
        echo -e "${GREEN}=== All CI steps passed! ===${NC}"
        ;;
    *)
        echo -e "${RED}Unknown suite: $SUITE${NC}"
        echo "Usage: $0 [unit|instrumented|relay|landing|digest|intent|ui|smoke|playwright-smoke|verify|all]"
        exit 1
        ;;
esac
