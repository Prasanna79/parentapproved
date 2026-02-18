/**
 * Token bucket rate limiter.
 * Per-tvId limits for phone requests, TV requests, and token refresh.
 */

export interface RateLimitConfig {
  maxTokens: number;
  refillRate: number; // tokens per millisecond
  windowMs: number;
}

export const PHONE_LIMIT: RateLimitConfig = {
  maxTokens: 60,
  refillRate: 60 / 60_000, // 60 per minute
  windowMs: 60_000,
};

export const TV_LIMIT: RateLimitConfig = {
  maxTokens: 10,
  refillRate: 10 / 60_000, // 10 per minute
  windowMs: 60_000,
};

export const REFRESH_LIMIT: RateLimitConfig = {
  maxTokens: 5,
  refillRate: 5 / 3_600_000, // 5 per hour
  windowMs: 3_600_000,
};

interface Bucket {
  tokens: number;
  lastRefill: number;
}

export class RateLimiter {
  private buckets = new Map<string, Bucket>();
  private clock: () => number;

  constructor(clock: () => number = Date.now) {
    this.clock = clock;
  }

  /**
   * Try to consume a token. Returns true if allowed, false if rate limited.
   */
  consume(key: string, config: RateLimitConfig): boolean {
    const now = this.clock();
    let bucket = this.buckets.get(key);

    if (!bucket) {
      bucket = { tokens: config.maxTokens, lastRefill: now };
      this.buckets.set(key, bucket);
    }

    // Refill tokens based on elapsed time
    const elapsed = now - bucket.lastRefill;
    const refilled = elapsed * config.refillRate;
    bucket.tokens = Math.min(config.maxTokens, bucket.tokens + refilled);
    bucket.lastRefill = now;

    if (bucket.tokens >= 1) {
      bucket.tokens -= 1;
      return true;
    }

    return false;
  }

  /**
   * Reset a specific bucket (for testing).
   */
  reset(key: string): void {
    this.buckets.delete(key);
  }

  /**
   * Clear all buckets.
   */
  clear(): void {
    this.buckets.clear();
  }
}
