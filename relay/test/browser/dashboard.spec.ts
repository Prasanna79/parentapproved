/**
 * Browser-based dashboard tests using Playwright.
 *
 * Serves the unified dashboard files with a local static server,
 * mocks API responses, and verifies everything renders correctly
 * in a real browser — both local mode and relay mode.
 *
 * Run: cd relay && npx playwright test
 */
import { test, expect, Page } from "@playwright/test";
import { createServer, Server, IncomingMessage, ServerResponse } from "http";
import { readFileSync } from "fs";
import { resolve, dirname } from "path";
import { fileURLToPath } from "url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const ASSETS_DIR = resolve(__dirname, "../../../tv-app/app/src/main/assets");

// --- Mock API responses ---
const MOCK_STATUS = {
  version: "0.8.0",
  serverRunning: true,
  protocolVersion: 1,
  currentlyPlaying: null,
};

const MOCK_PLAYLISTS = [
  { id: "pl1", displayName: "Kids Music", videoCount: 12 },
  { id: "pl2", displayName: "Science Videos", videoCount: 8 },
];

const MOCK_STATS = { totalEventsToday: 5, totalWatchTimeToday: 600 };

const MOCK_RECENT = [
  { videoId: "abc123", title: "Fun Video", durationSec: 180 },
  { videoId: "def456", title: "Learning Time", durationSec: 300 },
];

const MOCK_TIME_LIMITS = {
  currentStatus: "allowed",
  lockReason: null,
  manuallyLocked: false,
  todayUsedMin: 30,
  todayLimitMin: 120,
  todayBonusMin: 0,
  hasTimeRequest: false,
  bedtime: null,
};

const MOCK_AUTH = { token: "test-session-token-123" };

// --- Static server with API mocking ---
function createTestServer(urlPrefix: string = ""): Server {
  return createServer((req: IncomingMessage, res: ServerResponse) => {
    const url = req.url || "/";

    // Strip relay prefix if present: /tv/{tvId}/api/... → /api/...
    let apiPath = url;
    const relayMatch = url.match(/^\/tv\/[^/]+(\/.*)$/);
    if (relayMatch) apiPath = relayMatch[1];

    // Static assets
    if (url === "/" || url === "/index.html" || url.match(/^\/tv\/[^/]+\/?$/)) {
      const html = readFileSync(resolve(ASSETS_DIR, "index.html"), "utf-8");
      res.writeHead(200, { "Content-Type": "text/html" });
      res.end(html);
      return;
    }

    const assetMatch = url.match(/(?:\/assets)?\/([a-z.]+\.(js|css|svg))$/);
    if (assetMatch) {
      const file = assetMatch[1];
      const types: Record<string, string> = {
        js: "application/javascript",
        css: "text/css",
        svg: "image/svg+xml",
      };
      try {
        const content = readFileSync(resolve(ASSETS_DIR, file), "utf-8");
        res.writeHead(200, { "Content-Type": types[assetMatch[2]] || "text/plain" });
        res.end(content);
      } catch {
        res.writeHead(404);
        res.end("Not found");
      }
      return;
    }

    // API mocks
    if (apiPath === "/api/auth" || apiPath === "/auth") {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify(MOCK_AUTH));
      return;
    }
    if (apiPath === "/api/auth/refresh" || apiPath === "/auth/refresh") {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify(MOCK_AUTH));
      return;
    }
    if (apiPath === "/api/status" || apiPath === "/status") {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify(MOCK_STATUS));
      return;
    }
    if (apiPath === "/api/playlists" || apiPath === "/playlists") {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify(MOCK_PLAYLISTS));
      return;
    }
    if (apiPath === "/api/stats" || apiPath === "/stats") {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify(MOCK_STATS));
      return;
    }
    if ((apiPath === "/api/stats/recent" || apiPath === "/stats/recent")) {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify(MOCK_RECENT));
      return;
    }
    if (apiPath === "/api/time-limits" || apiPath === "/time-limits") {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify(MOCK_TIME_LIMITS));
      return;
    }
    if (apiPath === "/api/time-limits/lock" || apiPath === "/time-limits/lock") {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ ok: true }));
      return;
    }
    if (apiPath === "/api/time-limits/bonus" || apiPath === "/time-limits/bonus") {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ ok: true }));
      return;
    }

    res.writeHead(404);
    res.end("Not found: " + url);
  });
}

// --- Test suites ---

test.describe("dashboard in local mode", () => {
  let server: Server;
  let baseUrl: string;

  test.beforeAll(async () => {
    server = createTestServer();
    await new Promise<void>((resolve) => server.listen(0, () => resolve()));
    const addr = server.address();
    const port = typeof addr === "object" && addr ? addr.port : 0;
    baseUrl = `http://localhost:${port}`;
  });

  test.afterAll(() => {
    server.close();
  });

  test("page loads and shows auth screen", async ({ page }) => {
    await page.goto(baseUrl);
    await expect(page.locator("#auth-screen")).toBeVisible();
    await expect(page.locator("#dashboard")).toBeHidden();
  });

  test("CSS loads correctly (Nunito Sans font applied)", async ({ page }) => {
    await page.goto(baseUrl);
    const fontFamily = await page.locator("body").evaluate(
      (el) => window.getComputedStyle(el).fontFamily
    );
    expect(fontFamily).toContain("Nunito Sans");
  });

  test("PIN auth shows dashboard with all sections", async ({ page }) => {
    await page.goto(baseUrl);
    await page.fill("#pin-input", "123456");
    await page.click('#pin-form button[type="submit"]');

    // Wait for dashboard to appear
    await expect(page.locator("#dashboard")).toBeVisible({ timeout: 5000 });
    await expect(page.locator("#auth-screen")).toBeHidden();

    // All sections visible
    await expect(page.locator("#screen-time-section")).toBeVisible();
    await expect(page.locator("#playlists-section")).toBeVisible();
    await expect(page.locator("#stats-section")).toBeVisible();
    await expect(page.locator("#recent-section")).toBeVisible();
  });

  test("playlists render from API", async ({ page }) => {
    await loginAndLoadDashboard(page, baseUrl);

    await expect(page.locator("#playlist-list li")).toHaveCount(2, { timeout: 5000 });
    await expect(page.locator("#playlist-list")).toContainText("Kids Music");
    await expect(page.locator("#playlist-list")).toContainText("Science Videos");
  });

  test("stats render from API", async ({ page }) => {
    await loginAndLoadDashboard(page, baseUrl);

    await expect(page.locator("#stat-videos")).toHaveText("5", { timeout: 5000 });
    await expect(page.locator("#stat-time")).toHaveText("10m");
  });

  test("recent activity renders", async ({ page }) => {
    await loginAndLoadDashboard(page, baseUrl);

    await expect(page.locator("#recent-list li")).toHaveCount(2, { timeout: 5000 });
    await expect(page.locator("#recent-list")).toContainText("Fun Video");
  });

  test("screen time section renders correctly", async ({ page }) => {
    await loginAndLoadDashboard(page, baseUrl);

    // Status badge
    await expect(page.locator("#st-status-badge")).toBeVisible({ timeout: 5000 });
    await expect(page.locator("#st-status-badge")).toHaveText("Allowed");

    // Usage bar and label
    await expect(page.locator("#st-usage-label")).toContainText("30m / 120m");

    // Controls
    await expect(page.locator("#st-lock-btn")).toBeVisible();
    await expect(page.locator("#st-lock-btn")).toHaveText("Lock TV");
    await expect(page.locator("#st-edit-btn")).toBeVisible();
    await expect(page.locator("#st-edit-btn")).toHaveText("Edit Limits");
  });

  test("Lock TV button has red background", async ({ page }) => {
    await loginAndLoadDashboard(page, baseUrl);
    await expect(page.locator("#st-lock-btn")).toBeVisible({ timeout: 5000 });

    const bg = await page.locator("#st-lock-btn").evaluate(
      (el) => window.getComputedStyle(el).backgroundColor
    );
    // #DC2626 = rgb(220, 38, 38)
    expect(bg).toBe("rgb(220, 38, 38)");
  });

  test("Edit Limits button has white background with green text", async ({ page }) => {
    await loginAndLoadDashboard(page, baseUrl);
    await expect(page.locator("#st-edit-btn")).toBeVisible({ timeout: 5000 });

    const bg = await page.locator("#st-edit-btn").evaluate(
      (el) => window.getComputedStyle(el).backgroundColor
    );
    const color = await page.locator("#st-edit-btn").evaluate(
      (el) => window.getComputedStyle(el).color
    );
    // White background
    expect(bg).toBe("rgb(255, 255, 255)");
    // Green text #15803D = rgb(21, 128, 61)
    expect(color).toBe("rgb(21, 128, 61)");
  });

  test("Edit Limits modal opens and closes", async ({ page }) => {
    await loginAndLoadDashboard(page, baseUrl);
    await expect(page.locator("#st-edit-btn")).toBeVisible({ timeout: 5000 });

    // Modal is hidden initially
    await expect(page.locator("#edit-limits-modal")).toBeHidden();

    // Click Edit Limits
    await page.click("#st-edit-btn");
    await expect(page.locator("#edit-limits-modal")).toBeVisible();
    await expect(page.locator(".modal-content h3")).toHaveText("Edit Time Limits");

    // Close modal
    await page.click(".modal-cancel-btn");
    await expect(page.locator("#edit-limits-modal")).toBeHidden();
  });

  test("Edit Limits modal has daily limit and bedtime controls", async ({ page }) => {
    await loginAndLoadDashboard(page, baseUrl);
    await page.click("#st-edit-btn");
    await expect(page.locator("#edit-limits-modal")).toBeVisible({ timeout: 5000 });

    // Daily limit checkbox and input
    await expect(page.locator("#edit-limit-enabled")).toBeVisible();
    await expect(page.locator("#edit-limit-minutes")).toBeVisible();

    // Bedtime checkbox and inputs
    await expect(page.locator("#edit-bedtime-enabled")).toBeVisible();
  });

  test("bonus time buttons exist and are clickable", async ({ page }) => {
    await loginAndLoadDashboard(page, baseUrl);
    await expect(page.locator("#st-bonus-15")).toBeVisible({ timeout: 5000 });
    await expect(page.locator("#st-bonus-30")).toBeVisible();

    // Click +15 min — should make an API call (won't error since our mock returns ok)
    await page.click("#st-bonus-15");
    // Button still visible (no JS error)
    await expect(page.locator("#st-bonus-15")).toBeVisible();
  });

  test("local-notice footer is visible in local mode", async ({ page }) => {
    await loginAndLoadDashboard(page, baseUrl);
    await expect(page.locator("#local-notice")).toBeVisible();
    await expect(page.locator("#local-notice")).toContainText("local-only");
  });

  test("offline banner is hidden in local mode", async ({ page }) => {
    await loginAndLoadDashboard(page, baseUrl);
    await expect(page.locator("#offline-banner")).toBeHidden();
  });

  test("now-playing is hidden when nothing playing", async ({ page }) => {
    await loginAndLoadDashboard(page, baseUrl);
    await expect(page.locator("#now-playing")).toBeHidden();
  });

  test("no console errors on page load", async ({ page }) => {
    const errors: string[] = [];
    page.on("console", (msg) => {
      if (msg.type() === "error") errors.push(msg.text());
    });

    await page.goto(baseUrl);
    await page.fill("#pin-input", "123456");
    await page.click('#pin-form button[type="submit"]');
    await expect(page.locator("#dashboard")).toBeVisible({ timeout: 5000 });

    // Wait for API calls to complete
    await page.waitForTimeout(1000);

    expect(errors).toEqual([]);
  });
});

test.describe("dashboard in relay mode", () => {
  let server: Server;
  let baseUrl: string;
  const TEST_TV_ID = "test-tv-abc123";

  test.beforeAll(async () => {
    server = createTestServer();
    await new Promise<void>((resolve) => server.listen(0, () => resolve()));
    const addr = server.address();
    const port = typeof addr === "object" && addr ? addr.port : 0;
    baseUrl = `http://localhost:${port}`;
  });

  test.afterAll(() => {
    server.close();
  });

  test("relay URL serves dashboard", async ({ page }) => {
    await page.goto(`${baseUrl}/tv/${TEST_TV_ID}/`);
    await expect(page.locator("#auth-screen")).toBeVisible();
  });

  test("PIN auth works via relay URL", async ({ page }) => {
    await page.goto(`${baseUrl}/tv/${TEST_TV_ID}/`);
    await page.fill("#pin-input", "123456");
    await page.click('#pin-form button[type="submit"]');
    await expect(page.locator("#dashboard")).toBeVisible({ timeout: 5000 });
  });

  test("screen time section renders via relay", async ({ page }) => {
    await loginAndLoadDashboard(page, `${baseUrl}/tv/${TEST_TV_ID}/`);

    await expect(page.locator("#screen-time-section")).toBeVisible();
    await expect(page.locator("#st-status-badge")).toHaveText("Allowed", { timeout: 5000 });
    await expect(page.locator("#st-lock-btn")).toBeVisible();
    await expect(page.locator("#st-edit-btn")).toBeVisible();
  });

  test("local-notice is hidden in relay mode", async ({ page }) => {
    await loginAndLoadDashboard(page, `${baseUrl}/tv/${TEST_TV_ID}/`);
    await expect(page.locator("#local-notice")).toBeHidden();
  });

  test("Edit Limits works via relay", async ({ page }) => {
    await loginAndLoadDashboard(page, `${baseUrl}/tv/${TEST_TV_ID}/`);
    await expect(page.locator("#st-edit-btn")).toBeVisible({ timeout: 5000 });
    await page.click("#st-edit-btn");
    await expect(page.locator("#edit-limits-modal")).toBeVisible();
  });

  test("uses per-TV localStorage key", async ({ page }) => {
    await loginAndLoadDashboard(page, `${baseUrl}/tv/${TEST_TV_ID}/`);

    const key = await page.evaluate((tvId) => {
      return localStorage.getItem("kw_token_" + tvId);
    }, TEST_TV_ID);

    expect(key).toBe("test-session-token-123");
  });
});

test.describe("dashboard CSS correctness", () => {
  let server: Server;
  let baseUrl: string;

  test.beforeAll(async () => {
    server = createTestServer();
    await new Promise<void>((resolve) => server.listen(0, () => resolve()));
    const addr = server.address();
    const port = typeof addr === "object" && addr ? addr.port : 0;
    baseUrl = `http://localhost:${port}`;
  });

  test.afterAll(() => {
    server.close();
  });

  test("usage bar container has proper dimensions", async ({ page }) => {
    await loginAndLoadDashboard(page, baseUrl);
    await expect(page.locator(".st-usage-bar-container")).toBeVisible({ timeout: 5000 });

    const height = await page.locator(".st-usage-bar-container").evaluate(
      (el) => window.getComputedStyle(el).height
    );
    expect(height).toBe("8px");
  });

  test("screen time card has white background and border", async ({ page }) => {
    await loginAndLoadDashboard(page, baseUrl);
    await expect(page.locator(".screen-time-card")).toBeVisible({ timeout: 5000 });

    const bg = await page.locator(".screen-time-card").evaluate(
      (el) => window.getComputedStyle(el).backgroundColor
    );
    expect(bg).toBe("rgb(255, 255, 255)");
  });

  test("delete button is red", async ({ page }) => {
    await loginAndLoadDashboard(page, baseUrl);
    await expect(page.locator(".delete-btn").first()).toBeVisible({ timeout: 5000 });

    const bg = await page.locator(".delete-btn").first().evaluate(
      (el) => window.getComputedStyle(el).backgroundColor
    );
    // #DC2626 = rgb(220, 38, 38)
    expect(bg).toBe("rgb(220, 38, 38)");
  });

  test("status badge has green background when allowed", async ({ page }) => {
    await loginAndLoadDashboard(page, baseUrl);
    await expect(page.locator("#st-status-badge")).toBeVisible({ timeout: 5000 });

    const bg = await page.locator("#st-status-badge").evaluate(
      (el) => window.getComputedStyle(el).backgroundColor
    );
    // #22A559 = rgb(34, 165, 89)
    expect(bg).toBe("rgb(34, 165, 89)");
  });

  test("modal overlay covers the page", async ({ page }) => {
    await loginAndLoadDashboard(page, baseUrl);
    await page.click("#st-edit-btn");
    await expect(page.locator(".modal-overlay")).toBeVisible({ timeout: 5000 });

    const position = await page.locator(".modal-overlay").evaluate(
      (el) => window.getComputedStyle(el).position
    );
    expect(position).toBe("fixed");
  });
});

// --- Helper ---
async function loginAndLoadDashboard(page: Page, url: string) {
  await page.goto(url);
  await page.fill("#pin-input", "123456");
  await page.click('#pin-form button[type="submit"]');
  await expect(page.locator("#dashboard")).toBeVisible({ timeout: 5000 });
}
