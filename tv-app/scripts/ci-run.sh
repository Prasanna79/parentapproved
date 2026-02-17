#!/bin/bash
# KidsWatch V0.2.1 — Full CI runner
# Usage: ./scripts/ci-run.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-/Users/prasanna/.android}"
ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo "=== KidsWatch V0.2.1 CI ==="
echo "Project dir: $PROJECT_DIR"
echo ""

# Step 1: Unit tests (no emulator needed)
echo -e "${YELLOW}--- Step 1: Unit Tests (70 tests) ---${NC}"
cd "$PROJECT_DIR"
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew testDebugUnitTest --no-daemon
echo -e "${GREEN}Unit tests passed!${NC}"
echo ""

# Step 2: Check emulator
echo -e "${YELLOW}--- Step 2: Check Emulator ---${NC}"
if ! $ADB devices | grep -q "device$"; then
    echo "No emulator/device connected. Starting TV_API34..."
    ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
    /opt/homebrew/share/android-commandlinetools/emulator/emulator -avd TV_API34 -no-audio -gpu swiftshader_indirect -no-snapshot &
    EMULATOR_PID=$!
    echo "Waiting for emulator boot..."
    $ADB wait-for-device
    sleep 30
    $ADB shell getprop sys.boot_completed | grep -q "1" || sleep 20
    echo "Emulator ready."
else
    echo "Emulator already running."
fi

# Step 3: Install and launch
echo ""
echo -e "${YELLOW}--- Step 3: Install & Launch ---${NC}"
cd "$PROJECT_DIR"
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew installDebug --no-daemon
echo -e "${GREEN}APK installed.${NC}"

$ADB shell am start -n com.kidswatch.tv/.MainActivity
sleep 5
echo "App launched."

# Step 4: Instrumented tests
echo ""
echo -e "${YELLOW}--- Step 4: Instrumented Tests (19 tests) ---${NC}"
cd "$PROJECT_DIR"
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew connectedDebugAndroidTest --no-daemon
echo -e "${GREEN}Instrumented tests passed!${NC}"

# Relaunch app after instrumented tests
$ADB shell am start -n com.kidswatch.tv/.MainActivity
sleep 5

# Step 5: ADB intent + HTTP tests
echo ""
echo -e "${YELLOW}--- Step 5: Intent + HTTP Tests ---${NC}"
"$SCRIPT_DIR/test-suite.sh"

# Relaunch app
$ADB shell am start -n com.kidswatch.tv/.MainActivity
sleep 5

# Step 6: Automated UI tests
echo ""
echo -e "${YELLOW}--- Step 6: Automated UI Tests ---${NC}"
"$SCRIPT_DIR/ui-test.sh"

echo ""
echo -e "${GREEN}=== All CI steps passed! ===${NC}"
