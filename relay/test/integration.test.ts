/**
 * Integration tests for the relay.
 * Tests the full request lifecycle through allowlist + rate limiter.
 */
import { describe, it, expect } from "vitest";
import { isAllowed } from "../src/allowlist";
import { RateLimiter, PHONE_LIMIT, REFRESH_LIMIT } from "../src/ratelimit";

describe("auth flow through relay", () => {
  it("POST /api/auth is allowed", () => {
    expect(isAllowed("/api/auth", "POST").allowed).toBe(true);
  });

  it("GET /api/auth is blocked (POST only)", () => {
    expect(isAllowed("/api/auth", "GET").allowed).toBe(false);
  });

  it("POST /api/auth/refresh is allowed", () => {
    expect(isAllowed("/api/auth/refresh", "POST").allowed).toBe(true);
  });

  it("GET /api/auth/refresh is blocked", () => {
    expect(isAllowed("/api/auth/refresh", "GET").allowed).toBe(false);
  });
});

describe("playlist flow through relay", () => {
  it("GET /api/playlists is allowed", () => {
    expect(isAllowed("/api/playlists", "GET").allowed).toBe(true);
  });

  it("POST /api/playlists is allowed", () => {
    expect(isAllowed("/api/playlists", "POST").allowed).toBe(true);
  });

  it("DELETE /api/playlists/some-id is allowed", () => {
    expect(isAllowed("/api/playlists/some-id", "DELETE").allowed).toBe(true);
  });

  it("PUT /api/playlists is blocked", () => {
    expect(isAllowed("/api/playlists", "PUT").allowed).toBe(false);
  });
});

describe("status and stats through relay", () => {
  it("GET /api/status is allowed", () => {
    expect(isAllowed("/api/status", "GET").allowed).toBe(true);
  });

  it("GET /api/stats is allowed", () => {
    expect(isAllowed("/api/stats", "GET").allowed).toBe(true);
  });

  it("GET /api/stats/recent is allowed", () => {
    expect(isAllowed("/api/stats/recent", "GET").allowed).toBe(true);
  });
});

describe("playback control through relay", () => {
  it("POST /api/playback/pause is allowed", () => {
    expect(isAllowed("/api/playback/pause", "POST").allowed).toBe(true);
  });

  it("POST /api/playback/next is allowed", () => {
    expect(isAllowed("/api/playback/next", "POST").allowed).toBe(true);
  });

  it("POST /api/playback/prev is allowed", () => {
    expect(isAllowed("/api/playback/prev", "POST").allowed).toBe(true);
  });

  it("POST /api/playback/stop is allowed", () => {
    expect(isAllowed("/api/playback/stop", "POST").allowed).toBe(true);
  });

  it("GET /api/playback/pause is blocked (POST only)", () => {
    expect(isAllowed("/api/playback/pause", "GET").allowed).toBe(false);
  });
});

describe("rate limiter integration", () => {
  it("phone rate limit allows 60 requests per minute", () => {
    const limiter = new RateLimiter();
    let now = 1000000;
    const clock = () => now;
    const limiterWithClock = new RateLimiter(clock);

    for (let i = 0; i < 60; i++) {
      expect(limiterWithClock.consume("tv-1", PHONE_LIMIT)).toBe(true);
    }
    // 61st should fail
    expect(limiterWithClock.consume("tv-1", PHONE_LIMIT)).toBe(false);
  });

  it("refresh rate limit allows 5 per hour", () => {
    let now = 1000000;
    const limiter = new RateLimiter(() => now);

    for (let i = 0; i < 5; i++) {
      expect(limiter.consume("token-1", REFRESH_LIMIT)).toBe(true);
    }
    // 6th should fail
    expect(limiter.consume("token-1", REFRESH_LIMIT)).toBe(false);

    // Advance 1 hour — should refill
    now += 3600 * 1000;
    expect(limiter.consume("token-1", REFRESH_LIMIT)).toBe(true);
  });

  it("different tvIds have separate rate limits", () => {
    const limiter = new RateLimiter();

    for (let i = 0; i < 60; i++) {
      limiter.consume("tv-1", PHONE_LIMIT);
    }
    // tv-1 is exhausted
    expect(limiter.consume("tv-1", PHONE_LIMIT)).toBe(false);
    // tv-2 should still work
    expect(limiter.consume("tv-2", PHONE_LIMIT)).toBe(true);
  });
});

describe("blocked paths never reach TV", () => {
  const blockedPaths = [
    "/api/admin",
    "/api/debug",
    "/debug/sessions",
    "/api/../etc/passwd",
    "/api//playlists",
    "/admin",
    "/config",
    "/env",
    "/proxy/https://evil.com",
  ];

  for (const path of blockedPaths) {
    it(`${path} is blocked`, () => {
      expect(isAllowed(path, "GET").allowed).toBe(false);
    });
  }
});
