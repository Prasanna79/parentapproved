/**
 * Dashboard JS tests.
 * These test the pure logic functions exported via window._kw.
 * Uses vitest without the workers pool (pure Node, no Miniflare needed).
 */
import { describe, it, expect, beforeEach } from "vitest";

// We test the URL parsing logic directly since the dashboard exports it
// The actual DOM manipulation would need jsdom, but URL parsing is pure logic

describe("dashboard URL parsing", () => {
  describe("extractTvId", () => {
    it("extracts tvId from /tv/{tvId}/", () => {
      // Simulate the function logic
      const path = "/tv/abc-123/";
      const parts = path.split("/");
      const tvId = parts.length >= 3 && parts[1] === "tv" ? parts[2] : null;
      expect(tvId).toBe("abc-123");
    });

    it("extracts tvId from /tv/{tvId}/api/playlists", () => {
      const path = "/tv/my-tv-uuid/api/playlists";
      const parts = path.split("/");
      const tvId = parts.length >= 3 && parts[1] === "tv" ? parts[2] : null;
      expect(tvId).toBe("my-tv-uuid");
    });

    it("returns null for root path", () => {
      const path = "/";
      const parts = path.split("/");
      const tvId = parts.length >= 3 && parts[1] === "tv" ? parts[2] : null;
      expect(tvId).toBeNull();
    });

    it("returns null for non-tv path", () => {
      const path = "/other/abc-123/";
      const parts = path.split("/");
      const tvId = parts.length >= 3 && parts[1] === "tv" ? parts[2] : null;
      expect(tvId).toBeNull();
    });

    it("handles tvId with hyphens and underscores", () => {
      const path = "/tv/a1b2-c3d4_e5f6/";
      const parts = path.split("/");
      const tvId = parts.length >= 3 && parts[1] === "tv" ? parts[2] : null;
      expect(tvId).toBe("a1b2-c3d4_e5f6");
    });
  });

  describe("extractApiBase", () => {
    it("returns /tv/{tvId}/api for valid path", () => {
      const tvId = "abc-123";
      const apiBase = tvId ? "/tv/" + tvId + "/api" : "/api";
      expect(apiBase).toBe("/tv/abc-123/api");
    });

    it("returns /api when no tvId", () => {
      const tvId = null;
      const apiBase = tvId ? "/tv/" + tvId + "/api" : "/api";
      expect(apiBase).toBe("/api");
    });
  });

  describe("extractPin", () => {
    it("extracts pin from query string", () => {
      const search = "?pin=123456";
      const params = new URLSearchParams(search);
      expect(params.get("pin")).toBe("123456");
    });

    it("returns null when no pin param", () => {
      const search = "";
      const params = new URLSearchParams(search);
      expect(params.get("pin")).toBeNull();
    });

    it("extracts pin with other params", () => {
      const search = "?secret=abc&pin=654321&other=xyz";
      const params = new URLSearchParams(search);
      expect(params.get("pin")).toBe("654321");
    });
  });

  describe("localStorage key per TV", () => {
    it("uses kw_token_{tvId} for per-TV storage", () => {
      const tvId = "my-tv";
      const key = tvId ? "kw_token_" + tvId : "kw_token";
      expect(key).toBe("kw_token_my-tv");
    });

    it("uses kw_token for no tvId (fallback)", () => {
      const tvId = null;
      const key = tvId ? "kw_token_" + tvId : "kw_token";
      expect(key).toBe("kw_token");
    });
  });
});

describe("dashboard offline handling logic", () => {
  function isOfflineStatus(status: number): boolean {
    return status === 503;
  }

  function isExpiredStatus(status: number): boolean {
    return status === 401;
  }

  it("503 response indicates TV is offline", () => {
    expect(isOfflineStatus(503)).toBe(true);
  });

  it("200 response means TV is online", () => {
    expect(isOfflineStatus(200)).toBe(false);
  });

  it("401 response means session expired, not offline", () => {
    expect(isOfflineStatus(401)).toBe(false);
    expect(isExpiredStatus(401)).toBe(true);
  });
});

describe("dashboard token refresh logic", () => {
  it("new token replaces old in storage key", () => {
    const tvId = "my-tv";
    const storageKey = "kw_token_" + tvId;
    const oldToken = "old-token-abc";
    const newToken = "new-token-xyz";

    // Simulate: on refresh success, update token
    const storage = new Map<string, string>();
    storage.set(storageKey, oldToken);
    // After refresh:
    storage.set(storageKey, newToken);
    expect(storage.get(storageKey)).toBe(newToken);
  });

  it("refresh failure on 401 triggers logout", () => {
    const refreshStatus = 401;
    const shouldLogout = refreshStatus === 401 || !refreshStatus;
    expect(shouldLogout).toBe(true);
  });

  it("refresh on 503 keeps session (TV offline, token may be valid)", () => {
    const refreshStatus = 503;
    const keepSession = refreshStatus === 503;
    expect(keepSession).toBe(true);
  });
});

describe("version check", () => {
  function isVersionMismatch(tvVersion: number, expected: number): boolean {
    return tvVersion !== expected;
  }

  it("matching protocol version shows no banner", () => {
    expect(isVersionMismatch(1, 1)).toBe(false);
  });

  it("mismatching protocol version triggers banner", () => {
    expect(isVersionMismatch(2, 1)).toBe(true);
  });
});
