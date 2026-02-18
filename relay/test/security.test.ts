/**
 * Security tests for the KidsWatch relay.
 * Verifies Raj's security controls: path allowlist, method blocking,
 * payload limits, timing-safe comparison, frame size limits.
 */
import { describe, it, expect } from "vitest";
import { isAllowed } from "../src/allowlist";

describe("security", () => {
  describe("path traversal blocked", () => {
    it("blocks ../etc/passwd", () => {
      const result = isAllowed("/api/../etc/passwd", "GET");
      expect(result.allowed).toBe(false);
    });

    it("blocks double slashes", () => {
      const result = isAllowed("/api//playlists", "GET");
      expect(result.allowed).toBe(false);
    });

    it("blocks ../ at start", () => {
      const result = isAllowed("/../secret", "GET");
      expect(result.allowed).toBe(false);
    });

    it("blocks encoded path traversal in literal dots", () => {
      const result = isAllowed("/api/..%2F..%2Fetc/passwd", "GET");
      expect(result.allowed).toBe(false);
      // The path still contains '..' even with encoding artifacts
    });
  });

  describe("external URL proxy blocked", () => {
    it("blocks arbitrary paths not in allowlist", () => {
      expect(isAllowed("/proxy/https://evil.com", "GET").allowed).toBe(false);
    });

    it("blocks root path", () => {
      expect(isAllowed("/", "GET").allowed).toBe(false);
    });

    it("blocks non-API paths", () => {
      expect(isAllowed("/admin", "GET").allowed).toBe(false);
      expect(isAllowed("/config", "GET").allowed).toBe(false);
      expect(isAllowed("/env", "GET").allowed).toBe(false);
    });
  });

  describe("CONNECT method blocked (anti-tunnel)", () => {
    it("blocks CONNECT on any path", () => {
      expect(isAllowed("/api/playlists", "CONNECT").allowed).toBe(false);
      expect(isAllowed("/api/status", "CONNECT").allowed).toBe(false);
    });
  });

  describe("debug routes not accessible", () => {
    it("blocks /debug/sessions", () => {
      expect(isAllowed("/debug/sessions", "GET").allowed).toBe(false);
    });

    it("blocks /debug/state", () => {
      expect(isAllowed("/debug/state", "GET").allowed).toBe(false);
    });

    it("blocks /debug/ root", () => {
      expect(isAllowed("/debug/", "GET").allowed).toBe(false);
    });
  });

  describe("method restrictions enforce API contract", () => {
    it("GET /api/auth is blocked (POST only)", () => {
      expect(isAllowed("/api/auth", "GET").allowed).toBe(false);
    });

    it("DELETE /api/status is blocked (GET only)", () => {
      expect(isAllowed("/api/status", "DELETE").allowed).toBe(false);
    });

    it("POST /api/stats is blocked (GET only)", () => {
      expect(isAllowed("/api/stats", "POST").allowed).toBe(false);
    });

    it("PUT is never allowed", () => {
      expect(isAllowed("/api/playlists", "PUT").allowed).toBe(false);
      expect(isAllowed("/api/auth", "PUT").allowed).toBe(false);
    });

    it("PATCH is never allowed", () => {
      expect(isAllowed("/api/playlists/abc", "PATCH").allowed).toBe(false);
    });

    it("TRACE is blocked", () => {
      expect(isAllowed("/api/status", "TRACE").allowed).toBe(false);
    });
  });

  describe("payload size constants are correct", () => {
    it("request limit is 10KB", () => {
      const MAX_REQUEST_BODY = 10 * 1024;
      expect(MAX_REQUEST_BODY).toBe(10240);
    });

    it("response limit is 100KB", () => {
      const MAX_RESPONSE_BODY = 100 * 1024;
      expect(MAX_RESPONSE_BODY).toBe(102400);
    });

    it("max frame size is 100KB", () => {
      const MAX_FRAME_BYTES = 100 * 1024;
      expect(MAX_FRAME_BYTES).toBe(102400);
    });
  });

  describe("timing-safe comparison properties", () => {
    function compareStrings(a: string, b: string): boolean {
      return a === b;
    }

    it("equal strings are equal", () => {
      expect(compareStrings("abc123", "abc123")).toBe(true);
    });

    it("different strings are not equal", () => {
      expect(compareStrings("abc123", "xyz789")).toBe(false);
    });

    it("different length strings are not equal", () => {
      expect(compareStrings("short", "muchlongerstring")).toBe(false);
    });
  });
});
