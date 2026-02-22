import { describe, it, expect } from "vitest";
import { isAllowed } from "../src/allowlist";

/**
 * Route Alignment Test
 *
 * Ensures every Ktor API route on the TV has a corresponding relay allowlist entry,
 * and no orphan allowlist entries exist that don't map to a real Ktor route.
 *
 * This is the test that would have caught missing time-limit routes before deploy.
 *
 * To update: when adding a new Ktor route, add it here AND to allowlist.ts.
 */

// Canonical list of all Ktor API routes (from tv-app/app/src/main/java/.../server/*Routes.kt)
// Each entry: [method, path, description]
const KTOR_ROUTES: [string, string, string][] = [
  // AuthRoutes.kt
  ["POST", "/api/auth", "PIN authentication"],
  ["POST", "/api/auth/refresh", "Token refresh"],

  // PlaylistRoutes.kt
  ["GET", "/api/playlists", "List playlists"],
  ["POST", "/api/playlists", "Add playlist"],
  ["DELETE", "/api/playlists/abc123", "Delete playlist (parameterized)"],

  // PlaybackRoutes.kt
  ["POST", "/api/playback/stop", "Stop playback"],
  ["POST", "/api/playback/skip", "Skip to next"],
  ["POST", "/api/playback/pause", "Toggle pause"],

  // StatsRoutes.kt
  ["GET", "/api/stats", "Watch stats"],
  ["GET", "/api/stats/recent", "Recent play events"],

  // StatusRoutes.kt
  ["GET", "/api/status", "Server status"],

  // TimeLimitRoutes.kt
  ["GET", "/api/time-limits", "Get time limits"],
  ["PUT", "/api/time-limits", "Update time limits"],
  ["POST", "/api/time-limits/lock", "Manual lock/unlock"],
  ["POST", "/api/time-limits/bonus", "Grant bonus time"],
  ["POST", "/api/time-limits/request", "Kid requests more time"],

  // CrashLogRoutes.kt
  ["GET", "/api/crash-log", "Crash log"],
];

// All allowlist entries (method + example path) — derived from allowlist.ts ALLOWED_ROUTES
// These represent what the relay accepts.
const ALLOWLIST_ENTRIES: [string, string, string][] = [
  ["POST", "/api/auth", "auth"],
  ["POST", "/api/auth/refresh", "auth refresh"],
  ["GET", "/api/playlists", "list playlists"],
  ["POST", "/api/playlists", "add playlist"],
  ["GET", "/api/playlists/abc123", "get playlist by id"],
  ["DELETE", "/api/playlists/abc123", "delete playlist"],
  ["POST", "/api/playback/pause", "playback command"],
  ["GET", "/api/playback", "playback status"],
  ["GET", "/api/stats", "stats"],
  ["GET", "/api/stats/recent", "recent stats"],
  ["GET", "/api/status", "status"],
  ["GET", "/api/time-limits", "get time limits"],
  ["PUT", "/api/time-limits", "update time limits"],
  ["POST", "/api/time-limits/lock", "lock"],
  ["POST", "/api/time-limits/bonus", "bonus"],
  ["POST", "/api/time-limits/request", "time request"],
  ["GET", "/api/crash-log", "crash log"],
];

// Allowlist entries that exist in the relay but don't have dedicated Ktor handlers yet.
// The relay forwards them, the TV returns its own 404/response. These are intentional.
// If this list grows beyond 3, investigate — it may indicate stale allowlist entries.
const KNOWN_ALLOWLIST_EXTRAS: [string, string][] = [
  ["GET", "/api/playlists/:id"],   // allowlist permits; Ktor has no single-playlist GET yet
  ["GET", "/api/playback"],        // allowlist permits; dashboard polls playback state
];

describe("route alignment: Ktor ↔ relay allowlist", () => {
  describe("every Ktor route is allowed through the relay", () => {
    for (const [method, path, desc] of KTOR_ROUTES) {
      it(`${method} ${path} — ${desc}`, () => {
        const result = isAllowed(path, method);
        expect(result).toEqual({ allowed: true });
      });
    }
  });

  describe("every allowlist entry maps to a real Ktor route", () => {
    // Build a set of "METHOD /path" from Ktor routes for lookup.
    // For parameterized routes, we normalize: /playlists/abc123 → /playlists/:id
    function normalize(method: string, path: string): string {
      const normalized = path
        .replace(/\/playback\/[^/]+$/, "/playback/:cmd")
        .replace(/\/playlists\/[^/]+$/, "/playlists/:id");
      return `${method} ${normalized}`;
    }

    const ktorSet = new Set(
      KTOR_ROUTES.map(([m, p]) => normalize(m, p))
    );

    const extrasSet = new Set(
      KNOWN_ALLOWLIST_EXTRAS.map(([m, p]) => `${m} ${p}`)
    );

    for (const [method, path, desc] of ALLOWLIST_ENTRIES) {
      it(`${method} ${path} — has Ktor handler (${desc})`, () => {
        const key = normalize(method, path);
        if (extrasSet.has(key)) {
          // Known extra — allowlist entry without Ktor handler. Still valid.
          return;
        }
        expect(ktorSet.has(key)).toBe(true);
      });
    }
  });

  describe("no Ktor route missing from allowlist", () => {
    it("every Ktor route passes isAllowed()", () => {
      const missingFromAllowlist = KTOR_ROUTES.filter(([method, path]) => {
        const result = isAllowed(path, method);
        return !result.allowed;
      });
      expect(missingFromAllowlist).toEqual([]);
    });

    it("known extras list is small (max 3)", () => {
      // If this grows, investigate — may indicate stale allowlist entries
      expect(KNOWN_ALLOWLIST_EXTRAS.length).toBeLessThanOrEqual(3);
    });
  });
});
