import { describe, it, expect, beforeEach, vi } from 'vitest';
import worker, { sendDigest } from '../src/index.js';

// In-memory KV fake
function createFakeKV() {
  const store = new Map();
  return {
    get: async (key) => store.get(key) || null,
    put: async (key, value) => store.set(key, value),
    _store: store,
  };
}

function makeEnv(kv) {
  return {
    NOTIFY_EMAILS: kv,
    RESEND_API_KEY: 're_test_fake_key',
    TRIGGER_KEY: 'test_trigger',
  };
}

// Mock the resend module
vi.mock('resend', () => {
  const sent = [];
  return {
    _sent: sent,
    _resetSent: () => { sent.length = 0; },
    _failNext: false,
    Resend: class {
      constructor(apiKey) { this.apiKey = apiKey; }
      emails = {
        send: async (params) => {
          const mod = await import('resend');
          if (mod._failNext) {
            mod._failNext = false;
            throw new Error('send failed');
          }
          sent.push(params);
          return { id: 'fake-id' };
        },
      };
    },
  };
});

describe('notify-digest Worker', () => {
  let kv;
  let resendMock;

  beforeEach(async () => {
    kv = createFakeKV();
    resendMock = await import('resend');
    resendMock._resetSent();
  });

  describe('sendDigest', () => {
    it('does nothing when there are no signups', async () => {
      const env = makeEnv(kv);
      await sendDigest(env);
      expect(resendMock._sent).toHaveLength(0);
    });

    it('sends digest email for new signups', async () => {
      const now = new Date();
      const today = now.toISOString().slice(0, 10);

      await kv.put('alice@test.com', JSON.stringify({
        email: 'alice@test.com',
        timestamp: now.toISOString(),
        source: 'landing-page',
      }));
      await kv.put(`_daily:${today}`, JSON.stringify(['alice@test.com']));

      const env = makeEnv(kv);
      await sendDigest(env);

      expect(resendMock._sent).toHaveLength(1);
      const msg = resendMock._sent[0];
      expect(msg.from).toBe('ParentApproved <hello@updates.parentapproved.tv>');
      expect(msg.to).toBe('prasanna79+parentapproved@gmail.com');
      expect(msg.subject).toContain('1 new signup');
      expect(msg.text).toContain('alice@test.com');

      // Check last digest timestamp was updated
      const lastTs = await kv.get('_last_digest_ts');
      expect(lastTs).toBeTruthy();
    });

    it('skips already-digested signups', async () => {
      const today = new Date().toISOString().slice(0, 10);

      const oldTime = new Date(Date.now() - 7200000); // 2 hours ago
      await kv.put('old@test.com', JSON.stringify({
        email: 'old@test.com',
        timestamp: oldTime.toISOString(),
        source: 'landing-page',
      }));
      await kv.put(`_daily:${today}`, JSON.stringify(['old@test.com']));
      await kv.put('_last_digest_ts', new Date(Date.now() - 3600000).toISOString());

      const env = makeEnv(kv);
      await sendDigest(env);

      expect(resendMock._sent).toHaveLength(0);
    });

    it('handles multiple signups', async () => {
      const now = new Date();
      const today = now.toISOString().slice(0, 10);

      await kv.put('a@test.com', JSON.stringify({ email: 'a@test.com', timestamp: now.toISOString(), source: 'landing-page' }));
      await kv.put('b@test.com', JSON.stringify({ email: 'b@test.com', timestamp: now.toISOString(), source: 'landing-page' }));
      await kv.put(`_daily:${today}`, JSON.stringify(['a@test.com', 'b@test.com']));

      const env = makeEnv(kv);
      await sendDigest(env);

      expect(resendMock._sent).toHaveLength(1);
      const msg = resendMock._sent[0];
      expect(msg.subject).toContain('2 new signups');
    });

    it('does not update timestamp if send fails', async () => {
      const now = new Date();
      const today = now.toISOString().slice(0, 10);

      await kv.put('fail@test.com', JSON.stringify({ email: 'fail@test.com', timestamp: now.toISOString(), source: 'landing-page' }));
      await kv.put(`_daily:${today}`, JSON.stringify(['fail@test.com']));

      resendMock._failNext = true;
      const env = makeEnv(kv);
      await sendDigest(env);

      const lastTs = await kv.get('_last_digest_ts');
      expect(lastTs).toBeNull();
    });
  });

  describe('scheduled handler', () => {
    it('calls sendDigest via waitUntil', async () => {
      let promise;
      const ctx = { waitUntil: (p) => { promise = p; } };
      const env = makeEnv(kv);
      worker.scheduled({}, env, ctx);
      await promise;
      expect(resendMock._sent).toHaveLength(0);
    });
  });

  describe('HTTP trigger', () => {
    it('returns 404 for unknown paths', async () => {
      const req = new Request('https://example.com/');
      const res = await worker.fetch(req, makeEnv(kv));
      expect(res.status).toBe(404);
    });

    it('returns 404 for wrong trigger key', async () => {
      const req = new Request('https://example.com/trigger?key=wrong');
      const res = await worker.fetch(req, makeEnv(kv));
      expect(res.status).toBe(404);
    });

    it('triggers digest with correct key', async () => {
      const req = new Request('https://example.com/trigger?key=test_trigger');
      const res = await worker.fetch(req, makeEnv(kv));
      expect(res.status).toBe(200);
      const text = await res.text();
      expect(text).toContain('Digest sent');
    });
  });
});
