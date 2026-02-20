/**
 * Dashboard parity tests.
 *
 * The local dashboard (tv-app/app/src/main/assets/) and relay dashboard
 * (relay/assets/) must be identical. relay/assets/ should be symlinks
 * to the local files. These tests verify that.
 *
 * If a symlink breaks or someone copies instead of linking, these tests
 * catch the drift immediately.
 */
import { describe, it, expect } from "vitest";
import { readFileSync, lstatSync } from "fs";
import { resolve } from "path";

const LOCAL_DIR = resolve(__dirname, "../../tv-app/app/src/main/assets");
const RELAY_DIR = resolve(__dirname, "../assets");

const DASHBOARD_FILES = ["index.html", "style.css", "app.js", "favicon.svg"];

describe("dashboard parity: files are identical", () => {
  for (const file of DASHBOARD_FILES) {
    it(`${file} content is identical in local and relay`, () => {
      const local = readFileSync(resolve(LOCAL_DIR, file), "utf-8");
      const relay = readFileSync(resolve(RELAY_DIR, file), "utf-8");
      expect(relay).toBe(local);
    });
  }
});

describe("dashboard parity: relay assets are symlinks", () => {
  for (const file of DASHBOARD_FILES) {
    it(`relay/${file} is a symlink`, () => {
      const stat = lstatSync(resolve(RELAY_DIR, file));
      expect(stat.isSymbolicLink()).toBe(true);
    });
  }
});

describe("dashboard completeness: required features present", () => {
  const html = readFileSync(resolve(LOCAL_DIR, "index.html"), "utf-8");
  const js = readFileSync(resolve(LOCAL_DIR, "app.js"), "utf-8");
  const css = readFileSync(resolve(LOCAL_DIR, "style.css"), "utf-8");

  const requiredSections = [
    "screen-time-section",
    "playlists-section",
    "stats-section",
    "recent-section",
    "now-playing",
    "edit-limits-modal",
  ];

  for (const id of requiredSections) {
    it(`HTML has section: ${id}`, () => {
      expect(html).toContain(`id="${id}"`);
    });
  }

  const requiredFunctions = [
    "loadTimeLimits",
    "loadPlaylists",
    "loadStats",
    "loadRecent",
    "loadStatus",
    "loadDashboard",
    "refreshToken",
    "window.toggleLock",
    "window.grantBonusTime",
    "window.openEditLimits",
    "window.saveLimits",
  ];

  for (const fn of requiredFunctions) {
    it(`JS has function: ${fn}`, () => {
      expect(js).toContain(fn);
    });
  }

  const requiredCssClasses = [
    ".screen-time-card",
    ".st-controls",
    ".modal-overlay",
    "#st-lock-btn",
  ];

  for (const cls of requiredCssClasses) {
    it(`CSS has class: ${cls}`, () => {
      expect(css).toContain(cls);
    });
  }
});
