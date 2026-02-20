# ParentApproved.tv v0.7.1 Retrospective

**Date:** February 20, 2026
**Scope:** Landing page "Notify Me" form → Cloudflare KV + daily digest email via Resend

---

## What Went Well

### Pages Functions Are Effortless
Drop a `functions/api/notify.js` in the project, `wrangler pages deploy`, done. No separate Worker, no routing config. The function just appears at `/api/notify`. KV binding via `wrangler.toml` with `pages_build_output_dir` worked once we got the format right.

### KV Is Perfect for This Use Case
Email signups are a textbook key-value problem. No schema, no migrations, no query language. The daily index pattern (`_daily:YYYY-MM-DD`) makes the digest query O(n) on today's signups rather than scanning all keys. Free tier is more than sufficient.

### Tests Caught the Resend Error Handling Bug
The Resend SDK returns `{data: null, error: {...}}` on failure instead of throwing. The original code only had a try/catch, so failed sends silently marked the digest as complete. Tests for the "send fails" case led to adding the `result.error` check.

### `wrangler tail --format json` Is Essential
Every email delivery failure was diagnosed via `wrangler tail`. The structured JSON output showed the exact Resend error messages: "destination address is not verified", then "domain is not verified". Without tail, we'd have been guessing.

---

## What Didn't Go Well

### Cloudflare Native Email Was a Dead End
Spent ~30 minutes trying to use Cloudflare's `send_email` binding with Email Routing instead of an external service. The binding requires the destination address to be verified in Email Routing, but the semantics are confusing — a "verified destination" in Email Routing is not the same as a "custom address source". After multiple deploy/test cycles, switched to Resend which worked immediately once the domain was verified.

**Lesson:** Cloudflare Email Routing is for *receiving* email. For *sending* from Workers, use Resend or a similar transactional email service. The `send_email` binding exists but is poorly documented and the error messages are misleading.

### `wrangler kv` Defaults to Local
`wrangler kv key list` showed empty results even though the Pages Function was successfully writing data. Spent time adding a debug endpoint to verify KV was working. The issue: wrangler CLI defaults to local storage, not remote. Need `--remote` flag.

**Lesson:** Always use `wrangler kv ... --remote` when checking production data.

### Domain Verification Has Multiple Layers
Getting email delivery working required three separate verification steps:
1. Email Routing enabled on `parentapproved.tv` (Cloudflare dashboard)
2. Destination email verified in Email Routing
3. Sending domain verified in Resend (`updates.parentapproved.tv` with DKIM + SPF DNS records)

Each step produced a different error, and none of them clearly pointed to the next step needed.

**Lesson:** For future projects, set up email sending infrastructure (Resend domain verification) before writing the Worker code, not after.

---

## Learnings

1. **Pages Functions + KV is the right stack for simple form backends.** Zero infrastructure to manage, deploys with the static site, free tier covers prelaunch volumes easily.

2. **Resend's free tier is generous and the SDK is clean.** One import, one method call. The `{data, error}` return pattern (instead of throwing) is unusual but the SDK docs are clear about it.

3. **`wrangler tail` should be the first debugging tool, not the last.** We should have been tailing from the first failed email attempt instead of guessing.

4. **Cloudflare's `send_email` is not for transactional email.** It's for email routing/forwarding workflows. Don't fight it — use Resend.

5. **The `pages_build_output_dir = "."` pattern works.** Pages with wrangler.toml picks up KV bindings automatically. No need for dashboard configuration.

---

## By the Numbers

- **7 new files** created
- **3 files modified** (index.html, main.js, style.css)
- **19 new tests** (10 notify + 9 digest)
- **4 deploy cycles** to get email delivery working
- **3 verification steps** for email (Email Routing, destination, Resend domain)
- **0 external dependencies** for email collection (KV only)
- **1 external dependency** for email sending (Resend)
- **1 haiku** — tradition holds
