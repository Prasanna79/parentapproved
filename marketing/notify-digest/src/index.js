// Daily digest Worker — sends new signup summary to prasanna79@gmail.com
// Triggered by cron (0 14 * * * = 2pm UTC daily)
// Uses Resend API for email delivery

import { Resend } from 'resend';

const FROM_ADDR = 'ParentApproved <hello@updates.parentapproved.tv>';
const TO_ADDR = 'prasanna79+parentapproved@gmail.com';

export default {
  async scheduled(event, env, ctx) {
    ctx.waitUntil(sendDigest(env));
  },

  // Also callable via HTTP for manual trigger (protected by secret)
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === '/trigger' && url.searchParams.get('key') === env.TRIGGER_KEY) {
      await sendDigest(env);
      return new Response('Digest sent (if there were new signups)');
    }
    return new Response('Not found', { status: 404 });
  },
};

export async function sendDigest(env) {
  const yesterday = new Date(Date.now() - 86400000).toISOString().slice(0, 10);
  const today = new Date().toISOString().slice(0, 10);

  // Collect emails from yesterday and today's daily keys
  const emails = [];
  for (const date of [yesterday, today]) {
    const dailyKey = `_daily:${date}`;
    const list = JSON.parse((await env.NOTIFY_EMAILS.get(dailyKey)) || '[]');
    emails.push(...list.map(e => ({ email: e, date })));
  }

  // Filter to only entries since last digest
  const lastDigestTs = await env.NOTIFY_EMAILS.get('_last_digest_ts');
  const lastDigest = lastDigestTs ? new Date(lastDigestTs) : new Date(0);

  // Get full entries to check timestamps
  const newSignups = [];
  for (const { email } of emails) {
    const raw = await env.NOTIFY_EMAILS.get(email);
    if (!raw) continue;
    const entry = JSON.parse(raw);
    if (new Date(entry.timestamp) > lastDigest) {
      newSignups.push(entry);
    }
  }

  if (newSignups.length === 0) return;

  // Build email
  const count = newSignups.length;
  const subject = `${count} new signup${count > 1 ? 's' : ''} — ParentApproved.tv`;
  const emailList = newSignups.map(s => `  - ${s.email} (${s.timestamp})`).join('\n');
  const body = `New ParentApproved.tv signups since last digest:\n\n${emailList}\n\nTotal new: ${count}`;

  const resend = new Resend(env.RESEND_API_KEY);

  try {
    const result = await resend.emails.send({
      from: FROM_ADDR,
      to: TO_ADDR,
      subject,
      text: body,
    });
    if (result.error) {
      console.error('Resend API error:', JSON.stringify(result.error));
      return;
    }
    console.log('Email sent:', result.data?.id);
  } catch (e) {
    console.error('Email send failed:', e.message);
    return;
  }

  // Update last digest timestamp
  await env.NOTIFY_EMAILS.put('_last_digest_ts', new Date().toISOString());
}
