import { describe, it, expect } from "vitest";
import { RateLimiter, PHONE_LIMIT, TV_LIMIT, REFRESH_LIMIT } from "../src/ratelimit";

describe("RateLimiter", () => {
  it("allows requests within the limit", () => {
    const limiter = new RateLimiter(() => 0);
    for (let i = 0; i < 60; i++) {
      expect(limiter.consume("tv1", PHONE_LIMIT)).toBe(true);
    }
  });

  it("blocks the 61st request within the window", () => {
    const limiter = new RateLimiter(() => 0);
    for (let i = 0; i < 60; i++) {
      limiter.consume("tv1", PHONE_LIMIT);
    }
    expect(limiter.consume("tv1", PHONE_LIMIT)).toBe(false);
  });

  it("refills tokens after window elapses", () => {
    let now = 0;
    const limiter = new RateLimiter(() => now);

    // Exhaust all tokens
    for (let i = 0; i < 60; i++) {
      limiter.consume("tv1", PHONE_LIMIT);
    }
    expect(limiter.consume("tv1", PHONE_LIMIT)).toBe(false);

    // Advance 1 minute — full refill
    now = 60_000;
    expect(limiter.consume("tv1", PHONE_LIMIT)).toBe(true);
  });

  it("isolates rate limits between tvIds", () => {
    const limiter = new RateLimiter(() => 0);

    // Exhaust tv1
    for (let i = 0; i < 60; i++) {
      limiter.consume("tv1", PHONE_LIMIT);
    }
    expect(limiter.consume("tv1", PHONE_LIMIT)).toBe(false);

    // tv2 should be unaffected
    expect(limiter.consume("tv2", PHONE_LIMIT)).toBe(true);
  });

  it("enforces TV rate limit (10/min)", () => {
    const limiter = new RateLimiter(() => 0);
    for (let i = 0; i < 10; i++) {
      expect(limiter.consume("tv1", TV_LIMIT)).toBe(true);
    }
    expect(limiter.consume("tv1", TV_LIMIT)).toBe(false);
  });

  it("enforces refresh rate limit (5/hour)", () => {
    const limiter = new RateLimiter(() => 0);
    for (let i = 0; i < 5; i++) {
      expect(limiter.consume("token1", REFRESH_LIMIT)).toBe(true);
    }
    expect(limiter.consume("token1", REFRESH_LIMIT)).toBe(false);
  });

  it("partially refills tokens over time", () => {
    let now = 0;
    const limiter = new RateLimiter(() => now);

    // Exhaust all tokens
    for (let i = 0; i < 60; i++) {
      limiter.consume("tv1", PHONE_LIMIT);
    }

    // Advance 1 second — should refill 1 token (60/60s = 1/s)
    now = 1000;
    expect(limiter.consume("tv1", PHONE_LIMIT)).toBe(true);
    expect(limiter.consume("tv1", PHONE_LIMIT)).toBe(false);
  });

  it("caps tokens at max", () => {
    let now = 0;
    const limiter = new RateLimiter(() => now);

    // Use 1 token
    limiter.consume("tv1", PHONE_LIMIT);

    // Advance a very long time
    now = 1_000_000;

    // Should have max tokens (60), not more
    for (let i = 0; i < 60; i++) {
      expect(limiter.consume("tv1", PHONE_LIMIT)).toBe(true);
    }
    expect(limiter.consume("tv1", PHONE_LIMIT)).toBe(false);
  });
});
