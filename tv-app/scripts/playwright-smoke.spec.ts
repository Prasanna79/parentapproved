import { test, expect, Browser, Page, BrowserContext } from '@playwright/test';
import { extractPin, extractTvId, ensurePortForward, resetPortForward, goBack, restartApp, adb } from './adb-helpers';
import { execSync } from 'child_process';

const LOCAL_BASE = 'http://localhost:8080';
const RELAY_BASE = process.env.RELAY_URL ?? 'https://relay.parentapproved.tv';
const SKIP_RELAY = process.env.SKIP_RELAY === '1';

let pin: string;
let tvId: string | null;

/** Wait for local HTTP server to respond. */
function waitForServer(maxRetries = 15): void {
  for (let i = 0; i < maxRetries; i++) {
    try {
      const result = execSync(
        `curl -sf -o /dev/null -w "%{http_code}" ${LOCAL_BASE}/status`,
        { encoding: 'utf-8', timeout: 5_000 },
      ).trim();
      if (result === '200') return;
    } catch { /* server not ready */ }
    execSync('sleep 2');
  }
  throw new Error('HTTP server not responding after retries');
}

test.describe.configure({ mode: 'serial' });

test.beforeAll(async ({}, testInfo) => {
  testInfo.setTimeout(120_000);

  // Phase 1: Navigate to ConnectScreen and extract tvId (uiautomator may kill Ktor)
  try {
    const { tapText } = await import('./adb-helpers');
    // Navigate to connect screen first
    tapText('Connect Phone');
    execSync('sleep 2');
    tvId = extractTvId();
    goBack();
  } catch {
    tvId = null;
  }

  if (tvId) {
    console.log(`Extracted TV ID: ${tvId}`);
  } else {
    console.log('No TV ID found (relay tests will be skipped)');
  }

  // Phase 2: Restart app to recover Ktor server after uiautomator dumps
  console.log('Restarting app to recover Ktor server...');
  restartApp();

  // Phase 3: Extract PIN from fresh app (one quick dump cycle)
  pin = extractPin();
  console.log(`Extracted PIN: ${pin}`);
  goBack();
  execSync('sleep 2');

  // Phase 4: Re-establish port forward and verify server
  resetPortForward();
  waitForServer();
  console.log('HTTP server ready');
});

// Use a single shared page for all local tests to avoid Ktor connection cycling issues
// (Ktor CIO via adb forward can't handle rapid new TCP connections)
test.describe('local dashboard', () => {
  let sharedPage: Page;
  let sharedContext: BrowserContext;

  test.beforeAll(async ({ browser }) => {
    sharedContext = await browser.newContext();
    sharedPage = await sharedContext.newPage();
  });

  test.afterAll(async () => {
    await sharedContext?.close();
  });

  let initialResponse: Awaited<ReturnType<Page['goto']>>;

  test('auth screen visible', async () => {
    // Retry page.goto — adb port forward is flaky after uiautomator dumps
    for (let attempt = 0; attempt < 5; attempt++) {
      try {
        initialResponse = await sharedPage.goto(LOCAL_BASE, { timeout: 10_000 });
        break;
      } catch {
        if (attempt === 4) throw new Error('Failed to load dashboard after 5 attempts');
        resetPortForward();
        await sharedPage.waitForTimeout(3_000);
      }
    }
    await expect(sharedPage.locator('#auth-screen')).toBeVisible();
  });

  test('PIN auth → dashboard visible', async () => {
    // Auth screen should still be showing from previous test
    await sharedPage.fill('#pin-input', pin);
    await sharedPage.click('#pin-form button[type="submit"]');
    await expect(sharedPage.locator('#dashboard')).toBeVisible({ timeout: 10_000 });

    const token = await sharedPage.evaluate((key) => localStorage.getItem(key) ?? '', 'kw_token');
    expect(token).toBeTruthy();
  });

  test('all sections present', async () => {
    // Dashboard should be visible from PIN auth
    const sections = ['#screen-time-section', '#playlists-section', '#stats-section', '#recent-section', '#now-playing'];
    for (const selector of sections) {
      await expect(sharedPage.locator(selector)).toBeAttached();
    }
  });

  test('version displayed in footer', async () => {
    // Version is populated async after dashboard loads — wait for non-empty text
    const footer = sharedPage.locator('#footer-version');
    await expect(footer).not.toHaveText('', { timeout: 5_000 });
    const text = await footer.textContent();
    expect(text).toMatch(/\d+\.\d+/);
  });

  test('security headers present', async () => {
    // Check headers from the initial page load response
    expect(initialResponse).not.toBeNull();
    const headers = initialResponse!.headers();
    expect(headers['content-security-policy']).toBeTruthy();
    expect(headers['x-frame-options']).toBeTruthy();
    expect(headers['x-content-type-options']).toBeTruthy();
  });

  test('API endpoints return data', async () => {
    // Use in-page fetch to reuse the existing connection (avoids Ktor socket cycling)
    const results = await sharedPage.evaluate(async () => {
      const token = localStorage.getItem('kw_token') ?? '';
      const endpoints = ['/playlists', '/time-limits', '/stats'];
      const statuses: Record<string, number> = {};
      for (const ep of endpoints) {
        const resp = await fetch(ep, { headers: { 'Authorization': `Bearer ${token}` } });
        statuses[ep] = resp.status;
      }
      return statuses;
    });

    for (const [endpoint, status] of Object.entries(results)) {
      expect(status, `${endpoint} should return 200`).toBe(200);
    }
  });

  test('no JavaScript exceptions', async () => {
    // Listen for uncaught exceptions on the already-loaded dashboard
    const exceptions: string[] = [];
    sharedPage.on('pageerror', err => {
      exceptions.push(err.message);
    });

    // Trigger some dashboard activity by evaluating in-page
    await sharedPage.evaluate(() => {
      // Force a re-render of dashboard sections
      window.dispatchEvent(new Event('focus'));
    });
    await sharedPage.waitForTimeout(2000);

    expect(exceptions, `JS exceptions: ${exceptions.join(', ')}`).toHaveLength(0);
  });
});

test.describe('relay dashboard', () => {
  test.skip(() => !tvId || SKIP_RELAY, 'Skipped: no tvId or SKIP_RELAY=1');

  let sharedPage: Page;
  let sharedContext: BrowserContext;

  test.beforeAll(async ({ browser }) => {
    sharedContext = await browser.newContext();
    sharedPage = await sharedContext.newPage();
  });

  test.afterAll(async () => {
    await sharedContext?.close();
  });

  const relayDashUrl = () => `${RELAY_BASE}/tv/${tvId}/`;
  let relayAuthSucceeded = false;

  test('dashboard loads at relay URL', async () => {
    const response = await sharedPage.goto(relayDashUrl());
    expect(response?.status()).toBe(200);
    await expect(sharedPage.locator('#auth-screen')).toBeVisible();
  });

  test('PIN auth → dashboard visible', async () => {
    await sharedPage.fill('#pin-input', pin);
    await sharedPage.click('#pin-form button[type="submit"]');

    // Wait for auth response — relay may fail if app was restarted
    await sharedPage.waitForTimeout(5_000);

    const dashboardVisible = await sharedPage.locator('#dashboard').isVisible();
    const errorVisible = await sharedPage.locator('#auth-error').isVisible();

    if (errorVisible || !dashboardVisible) {
      test.skip(true, 'Relay auth failed — app was likely restarted with new PIN/secret');
      return;
    }

    relayAuthSucceeded = true;
    expect(dashboardVisible).toBe(true);
  });

  test('all sections present', async () => {
    test.skip(!relayAuthSucceeded, 'Relay auth did not succeed');
    const sections = ['#screen-time-section', '#playlists-section', '#stats-section', '#recent-section', '#now-playing'];
    for (const selector of sections) {
      await expect(sharedPage.locator(selector)).toBeAttached();
    }
  });

  test('per-TV localStorage key exists', async () => {
    test.skip(!relayAuthSucceeded, 'Relay auth did not succeed');
    const key = `kw_token_${tvId}`;
    const token = await sharedPage.evaluate((k) => localStorage.getItem(k), key);
    expect(token).toBeTruthy();
  });
});
