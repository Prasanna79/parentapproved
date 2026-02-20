# ParentApproved.tv v0.7.1 "The Signal" — Release Notes

**Date:** February 20, 2026
**Milestone:** The landing page listens. Parents can sign up, and you'll know about it.

```
A form awaits —
one email, gently gathered,
morning brings the news.
```

---

## What's New

### "Notify Me" Actually Works
- The landing page form at parentapproved.tv/#download now collects email signups
- Emails stored in Cloudflare KV (no external database, no cloud dependency)
- Validates email format, normalizes to lowercase, deduplicates
- Frontend shows success/duplicate/error messages inline
- CORS headers for preflight support

### Daily Digest Email
- Scheduled Cloudflare Worker runs daily at 2pm UTC
- Sends a summary of new signups since the last digest
- Powered by Resend API via `updates.parentapproved.tv` verified domain
- Also triggerable via HTTP with a secret key for manual checks
- Skips sending if there are no new signups

---

## Architecture

### Landing Page (Cloudflare Pages + Functions)
- `functions/api/notify.js` — Pages Function, handles `POST /api/notify`
- `wrangler.toml` — binds `NOTIFY_EMAILS` KV namespace
- KV schema: email as key → `{email, timestamp, source}`, plus `_daily:YYYY-MM-DD` index

### Digest Worker (`marketing/notify-digest/`)
- Standalone Cloudflare Worker with cron trigger
- Reads KV daily index, filters by `_last_digest_ts`, sends via Resend SDK
- From: `hello@updates.parentapproved.tv` → routes to Gmail

---

## Files Changed

| Category | Files |
|----------|-------|
| **New — Pages Function** | `marketing/landing-page/functions/api/notify.js` |
| **New — config** | `marketing/landing-page/wrangler.toml`, `marketing/landing-page/package.json` |
| **New — digest worker** | `marketing/notify-digest/src/index.js`, `marketing/notify-digest/wrangler.toml`, `marketing/notify-digest/package.json`, `marketing/notify-digest/vitest.config.js` |
| **New — tests** | `marketing/landing-page/tests/notify.test.js` (10), `marketing/notify-digest/tests/digest.test.js` (9) |
| **New — test mocks** | `marketing/notify-digest/tests/mocks/cloudflare-email.js` |
| **Modified** | `marketing/landing-page/index.html` (form wiring), `marketing/landing-page/main.js` (fetch submit), `marketing/landing-page/style.css` (message styles) |
| **New — gitignore** | `marketing/landing-page/.gitignore`, `marketing/notify-digest/.gitignore` |

---

## Test Coverage

| Suite | Count | Runner |
|-------|-------|--------|
| Notify function | 10 | `cd marketing/landing-page && npx vitest run` |
| Digest worker | 9 | `cd marketing/notify-digest && npx vitest run` |
| **New total** | **19** | |

E2E verified against production: valid/duplicate/invalid/empty emails, CORS, KV storage, digest email delivery.

---

## Infrastructure

- **KV namespace:** `NOTIFY_EMAILS` (`a7e68c5bacb24480b76c8df5ccd6b6f7`)
- **Pages deploy:** `cd marketing/landing-page && npx wrangler pages deploy`
- **Digest deploy:** `cd marketing/notify-digest && npx wrangler deploy`
- **Secrets:** `RESEND_API_KEY`, `TRIGGER_KEY` (set via `wrangler secret put`)
- **DNS:** DKIM + SPF records for `updates.parentapproved.tv` (Resend domain verification)
