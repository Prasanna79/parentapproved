import { execSync } from 'child_process';
import { readFileSync, mkdtempSync, rmSync } from 'fs';
import { join } from 'path';
import { tmpdir } from 'os';

const ANDROID_HOME = process.env.ANDROID_HOME ?? '/opt/homebrew/share/android-commandlinetools';
const ADB_BIN = process.env.ADB ?? `${ANDROID_HOME}/platform-tools/adb`;
const DEVICE = process.env.DEVICE ?? 'emulator-5554';

/** Run an adb command, return stdout as string. */
export function adb(...args: string[]): string {
  const cmd = `"${ADB_BIN}" -s ${DEVICE} ${args.join(' ')}`;
  return execSync(cmd, { encoding: 'utf-8', timeout: 15_000 }).trim();
}

/** Dump UI XML from device, return parsed content. Retries up to 3 times. */
export function dumpUi(): string {
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      adb('shell', 'uiautomator', 'dump', '/sdcard/ui.xml');
      const tmpDir = mkdtempSync(join(tmpdir(), 'pw-smoke-'));
      const localPath = join(tmpDir, 'ui.xml');
      try {
        adb('pull', '/sdcard/ui.xml', localPath);
        const content = readFileSync(localPath, 'utf-8');
        if (content.length > 0) return content;
      } finally {
        rmSync(tmpDir, { recursive: true, force: true });
      }
    } catch {
      // retry after a brief pause
      execSync('sleep 1');
    }
  }
  throw new Error('Failed to dump UI after 3 attempts');
}

/** Navigate to ConnectScreen and extract the 6-digit PIN from UI dump. */
export function extractPin(): string {
  // First check if we're already on the connect screen
  let xml = dumpUi();
  if (!xml.includes('One-time setup') && !xml.includes('Scan')) {
    // Navigate to connect screen — tap "Connect Phone" if visible
    try {
      tapText('Connect Phone');
      execSync('sleep 2');
      xml = dumpUi();
    } catch {
      throw new Error('Could not navigate to Connect Screen');
    }
  }

  const match = xml.match(/text="(\d{6})"/);
  if (!match) {
    throw new Error('Could not find 6-digit PIN in UI dump');
  }
  return match[1];
}

/** Open Settings overlay on ConnectScreen and extract the TV ID. */
export function extractTvId(): string | null {
  // Make sure we're on the connect screen
  const xml = dumpUi();
  if (!xml.includes('One-time setup') && !xml.includes('Scan')) {
    return null;
  }

  // Tap Settings gear (content-desc="Settings")
  try {
    tapByContentDesc('Settings');
    execSync('sleep 2');
  } catch {
    return null;
  }

  const settingsXml = dumpUi();
  const match = settingsXml.match(/text="TV ID: ([^"]+)"/);

  // Close settings by pressing back
  goBack();
  execSync('sleep 1');

  return match ? match[1] : null;
}

/** Find an element by text in UI XML and tap its center. */
export function tapText(text: string): void {
  const xml = dumpUi();
  const bounds = findBounds(xml, `text="${text}"`);
  if (!bounds) {
    throw new Error(`tapText: element with text="${text}" not found`);
  }
  tapBounds(bounds);
}

/** Find an element by content-desc in UI XML and tap its center. */
export function tapByContentDesc(desc: string): void {
  const xml = dumpUi();
  const bounds = findBounds(xml, `content-desc="${desc}"`);
  if (!bounds) {
    throw new Error(`tapByContentDesc: element with content-desc="${desc}" not found`);
  }
  tapBounds(bounds);
}

/** Press the Back key. */
export function goBack(): void {
  adb('shell', 'input', 'keyevent', '4');
}

/** Restart the app (force-stop + start). Needed after uiautomator dumps kill Ktor. */
export function restartApp(): void {
  const pkg = 'tv.parentapproved.app';
  adb('shell', 'am', 'force-stop', pkg);
  execSync('sleep 2');
  adb('shell', 'am', 'start', '-n', `${pkg}/.MainActivity`);
  execSync('sleep 8');
}

/** Ensure adb port forwarding from localhost:8080 to device:8080. */
export function ensurePortForward(port = 8080): void {
  adb('forward', `tcp:${port}`, `tcp:${port}`);
}

/** Remove all forwards and re-establish cleanly. */
export function resetPortForward(port = 8080): void {
  try { adb('forward', '--remove-all'); } catch { /* ignore */ }
  adb('forward', `tcp:${port}`, `tcp:${port}`);
}

// --- Internal helpers ---

function findBounds(xml: string, attrMatch: string): string | null {
  // Match the attribute followed by bounds in the same node
  const pattern = new RegExp(
    `${escapeRegex(attrMatch)}[^>]*bounds="(\\[\\d+,\\d+\\]\\[\\d+,\\d+\\])"`,
  );
  const match = xml.match(pattern);
  return match ? match[1] : null;
}

function tapBounds(bounds: string): void {
  // bounds format: [x1,y1][x2,y2]
  const nums = bounds.match(/\d+/g);
  if (!nums || nums.length < 4) throw new Error(`Invalid bounds: ${bounds}`);
  const [x1, y1, x2, y2] = nums.map(Number);
  const cx = Math.floor((x1 + x2) / 2);
  const cy = Math.floor((y1 + y2) / 2);
  adb('shell', 'input', 'tap', `${cx}`, `${cy}`);
  execSync('sleep 1');
}

function escapeRegex(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
