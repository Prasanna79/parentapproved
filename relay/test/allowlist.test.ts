import { describe, it, expect } from "vitest";
import { isAllowed } from "../src/allowlist";

describe("allowlist", () => {
  describe("allowed paths and methods", () => {
    it("allows POST /api/auth", () => {
      expect(isAllowed("/api/auth", "POST")).toEqual({ allowed: true });
    });

    it("allows POST /api/auth/refresh", () => {
      expect(isAllowed("/api/auth/refresh", "POST")).toEqual({ allowed: true });
    });

    it("allows GET /api/playlists", () => {
      expect(isAllowed("/api/playlists", "GET")).toEqual({ allowed: true });
    });

    it("allows POST /api/playlists", () => {
      expect(isAllowed("/api/playlists", "POST")).toEqual({ allowed: true });
    });

    it("allows GET /api/playlists/:id", () => {
      expect(isAllowed("/api/playlists/abc123", "GET")).toEqual({ allowed: true });
    });

    it("allows DELETE /api/playlists/:id", () => {
      expect(isAllowed("/api/playlists/abc123", "DELETE")).toEqual({ allowed: true });
    });

    it("allows POST /api/playback/:command", () => {
      expect(isAllowed("/api/playback/pause", "POST")).toEqual({ allowed: true });
      expect(isAllowed("/api/playback/stop", "POST")).toEqual({ allowed: true });
      expect(isAllowed("/api/playback/next", "POST")).toEqual({ allowed: true });
    });

    it("allows GET /api/playback", () => {
      expect(isAllowed("/api/playback", "GET")).toEqual({ allowed: true });
    });

    it("allows GET /api/stats", () => {
      expect(isAllowed("/api/stats", "GET")).toEqual({ allowed: true });
    });

    it("allows GET /api/stats/recent", () => {
      expect(isAllowed("/api/stats/recent", "GET")).toEqual({ allowed: true });
    });

    it("allows GET /api/status", () => {
      expect(isAllowed("/api/status", "GET")).toEqual({ allowed: true });
    });
  });

  describe("blocked paths", () => {
    it("blocks unknown paths", () => {
      const result = isAllowed("/api/unknown", "GET");
      expect(result).toEqual({ allowed: false, reason: "path_not_found" });
    });

    it("blocks root path", () => {
      expect(isAllowed("/", "GET")).toEqual({ allowed: false, reason: "path_not_found" });
    });

    it("blocks debug endpoints", () => {
      expect(isAllowed("/debug/sessions", "GET")).toEqual({ allowed: false, reason: "path_not_found" });
    });

    it("blocks path traversal with ..", () => {
      expect(isAllowed("/api/../etc/passwd", "GET")).toEqual({ allowed: false, reason: "path_traversal" });
    });

    it("blocks path traversal with double slashes", () => {
      expect(isAllowed("/api//playlists", "GET")).toEqual({ allowed: false, reason: "path_traversal" });
    });
  });

  describe("blocked methods", () => {
    it("blocks CONNECT method", () => {
      expect(isAllowed("/api/playlists", "CONNECT")).toEqual({ allowed: false, reason: "method_not_allowed" });
    });

    it("blocks PUT method", () => {
      expect(isAllowed("/api/playlists", "PUT")).toEqual({ allowed: false, reason: "method_not_allowed" });
    });

    it("blocks PATCH method", () => {
      expect(isAllowed("/api/playlists", "PATCH")).toEqual({ allowed: false, reason: "method_not_allowed" });
    });

    it("blocks TRACE method", () => {
      expect(isAllowed("/api/status", "TRACE")).toEqual({ allowed: false, reason: "method_not_allowed" });
    });

    it("blocks GET on POST-only endpoint", () => {
      expect(isAllowed("/api/auth", "GET")).toEqual({ allowed: false, reason: "method_not_allowed" });
    });

    it("blocks DELETE on GET-only endpoint", () => {
      expect(isAllowed("/api/status", "DELETE")).toEqual({ allowed: false, reason: "method_not_allowed" });
    });
  });

  describe("case insensitivity", () => {
    it("handles lowercase methods", () => {
      expect(isAllowed("/api/playlists", "get")).toEqual({ allowed: true });
    });

    it("handles mixed-case methods", () => {
      expect(isAllowed("/api/playlists", "Get")).toEqual({ allowed: true });
    });
  });
});
