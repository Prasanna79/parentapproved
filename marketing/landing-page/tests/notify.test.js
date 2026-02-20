import { describe, it, expect, beforeEach } from 'vitest';
import { onRequestPost, onRequestOptions } from '../functions/api/notify.js';

// In-memory KV fake
function createFakeKV() {
  const store = new Map();
  return {
    get: async (key) => store.get(key) || null,
    put: async (key, value) => store.set(key, value),
    _store: store,
  };
}

function makeContext(body, kv) {
  return {
    request: new Request('https://parentapproved.tv/api/notify', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    }),
    env: { NOTIFY_EMAILS: kv },
  };
}

describe('POST /api/notify', () => {
  let kv;

  beforeEach(() => {
    kv = createFakeKV();
  });

  it('stores a valid email and returns subscribed', async () => {
    const ctx = makeContext({ email: 'test@example.com' }, kv);
    const res = await onRequestPost(ctx);
    const data = await res.json();

    expect(res.status).toBe(200);
    expect(data.status).toBe('subscribed');

    // Check KV has the email
    const stored = JSON.parse(await kv.get('test@example.com'));
    expect(stored.email).toBe('test@example.com');
    expect(stored.source).toBe('landing-page');
    expect(stored.timestamp).toBeTruthy();
  });

  it('normalizes email to lowercase and trims whitespace', async () => {
    const ctx = makeContext({ email: '  Test@Example.COM  ' }, kv);
    const res = await onRequestPost(ctx);
    const data = await res.json();

    expect(data.status).toBe('subscribed');
    const stored = JSON.parse(await kv.get('test@example.com'));
    expect(stored.email).toBe('test@example.com');
  });

  it('returns already_subscribed for duplicate email', async () => {
    // First signup
    await onRequestPost(makeContext({ email: 'dupe@test.com' }, kv));
    // Second signup
    const res = await onRequestPost(makeContext({ email: 'dupe@test.com' }, kv));
    const data = await res.json();

    expect(res.status).toBe(200);
    expect(data.status).toBe('already_subscribed');
  });

  it('rejects empty email with 400', async () => {
    const res = await onRequestPost(makeContext({ email: '' }, kv));
    expect(res.status).toBe(400);
    const data = await res.json();
    expect(data.error).toBe('Invalid email address');
  });

  it('rejects missing email field with 400', async () => {
    const res = await onRequestPost(makeContext({}, kv));
    expect(res.status).toBe(400);
  });

  it('rejects malformed email with 400', async () => {
    const res = await onRequestPost(makeContext({ email: 'not-an-email' }, kv));
    expect(res.status).toBe(400);
    const data = await res.json();
    expect(data.error).toBe('Invalid email address');
  });

  it('rejects email over 254 chars with 400', async () => {
    const longEmail = 'a'.repeat(250) + '@b.co';
    const res = await onRequestPost(makeContext({ email: longEmail }, kv));
    expect(res.status).toBe(400);
  });

  it('appends to daily index key', async () => {
    await onRequestPost(makeContext({ email: 'day@test.com' }, kv));
    const today = new Date().toISOString().slice(0, 10);
    const dailyKey = `_daily:${today}`;
    const dailyList = JSON.parse(await kv.get(dailyKey));
    expect(dailyList).toContain('day@test.com');
  });

  it('returns 400 for non-JSON body', async () => {
    const ctx = {
      request: new Request('https://parentapproved.tv/api/notify', {
        method: 'POST',
        body: 'not json',
      }),
      env: { NOTIFY_EMAILS: kv },
    };
    const res = await onRequestPost(ctx);
    expect(res.status).toBe(400);
  });
});

describe('OPTIONS /api/notify', () => {
  it('returns CORS headers', async () => {
    const res = await onRequestOptions();
    expect(res.headers.get('Access-Control-Allow-Origin')).toBe('*');
    expect(res.headers.get('Access-Control-Allow-Methods')).toContain('POST');
  });
});
