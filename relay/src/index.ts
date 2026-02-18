/**
 * ParentApproved Relay — Cloudflare Worker entry point.
 * Routes requests to static assets, API forwarding, or WebSocket upgrade.
 *
 * URL pattern: /tv/{tvId}/...
 *   /tv/{tvId}/         → dashboard HTML
 *   /tv/{tvId}/app.js   → dashboard JS
 *   /tv/{tvId}/style.css → dashboard CSS
 *   /tv/{tvId}/ws       → WebSocket upgrade → Durable Object
 *   /tv/{tvId}/api/*    → API forwarding → Durable Object
 */

export { RelayDurableObject } from "./relay";

import { isAllowed } from "./allowlist";
import { RateLimiter, PHONE_LIMIT, REFRESH_LIMIT } from "./ratelimit";
// @ts-expect-error — Workers Sites manifest is a virtual module
import manifestJSON from "__STATIC_CONTENT_MANIFEST";

export interface Env {
  RELAY: DurableObjectNamespace;
  __STATIC_CONTENT: KVNamespace;
}

/** Max request body size: 10KB */
const MAX_REQUEST_BODY = 10 * 1024;

/** Max response body size: 100KB */
const MAX_RESPONSE_BODY = 100 * 1024;

/** Max TV connections per source IP */
const MAX_TVS_PER_IP = 5;

/** Allowed response content types */
const ALLOWED_RESPONSE_TYPES = new Set(["application/json", "text/html"]);

/** Rate limiters (per-isolate, fine for our scale) */
const phoneLimiter = new RateLimiter();
const refreshLimiter = new RateLimiter();

/** IP → Set of tvIds tracking (per-isolate) */
const ipTvMap = new Map<string, Set<string>>();

/** Content-Type map for static assets */
const CONTENT_TYPES: Record<string, string> = {
  "index.html": "text/html; charset=utf-8",
  "app.js": "application/javascript; charset=utf-8",
  "style.css": "text/css; charset=utf-8",
  "favicon.svg": "image/svg+xml",
};

/**
 * Extract tvId and remainder from URL path.
 * Pattern: /tv/{tvId}/...
 * Returns null if path doesn't match.
 */
function parsePath(pathname: string): { tvId: string; rest: string } | null {
  const match = pathname.match(/^\/tv\/([a-zA-Z0-9_-]+)(\/.*)?$/);
  if (!match) return null;
  return {
    tvId: match[1],
    rest: match[2] || "/",
  };
}

/**
 * Get client IP from request.
 */
function getClientIp(request: Request): string {
  return request.headers.get("CF-Connecting-IP") || request.headers.get("X-Forwarded-For") || "unknown";
}

/**
 * Track IP → tvId for WebSocket connections.
 * Returns false if the IP has exceeded MAX_TVS_PER_IP.
 */
function trackIpConnection(ip: string, tvId: string): boolean {
  let tvIds = ipTvMap.get(ip);
  if (!tvIds) {
    tvIds = new Set();
    ipTvMap.set(ip, tvIds);
  }

  // Already tracking this tvId for this IP — that's fine
  if (tvIds.has(tvId)) return true;

  if (tvIds.size >= MAX_TVS_PER_IP) return false;

  tvIds.add(tvId);
  return true;
}

/**
 * Resolve asset name to content-hashed KV key using Workers Sites manifest.
 */
function resolveAssetKey(assetName: string): string | null {
  try {
    const manifest = JSON.parse(manifestJSON);
    return manifest[assetName] || null;
  } catch {
    return null;
  }
}

/**
 * Serve a static asset from KV or return a placeholder.
 */
async function serveStaticAsset(
  env: Env,
  assetName: string
): Promise<Response> {
  const contentType = CONTENT_TYPES[assetName];
  if (!contentType) {
    return new Response("Not found", { status: 404 });
  }

  // Resolve content-hashed key from Workers Sites manifest
  try {
    const kvKey = resolveAssetKey(assetName) || assetName;
    const content = await env.__STATIC_CONTENT.get(kvKey, "text");
    if (content) {
      return new Response(content, {
        headers: {
          "Content-Type": contentType,
          "Cache-Control": "public, max-age=3600",
        },
      });
    }
  } catch {
    // KV not available — fall through to placeholder
  }

  // Placeholder responses when KV is not available (tests, local dev)
  if (assetName === "index.html") {
    return new Response(
      "<!DOCTYPE html><html><head><title>ParentApproved</title></head><body><p>ParentApproved Relay — dashboard coming soon.</p></body></html>",
      { headers: { "Content-Type": contentType } }
    );
  }

  return new Response("/* placeholder */", {
    headers: { "Content-Type": contentType },
  });
}

/**
 * Validate a response from the Durable Object before returning to the phone.
 * Checks content type and body size.
 */
function validateResponse(response: Response): Response | null {
  const contentType = response.headers.get("Content-Type");

  // No body responses (204, 304) are always OK
  if (response.status === 204 || response.status === 304) {
    return response;
  }

  // Check content type
  if (contentType) {
    const baseType = contentType.split(";")[0].trim().toLowerCase();
    if (!ALLOWED_RESPONSE_TYPES.has(baseType)) {
      return null;
    }
  }

  // Check content length if available
  const contentLength = response.headers.get("Content-Length");
  if (contentLength && parseInt(contentLength, 10) > MAX_RESPONSE_BODY) {
    return null;
  }

  return response;
}

/**
 * Forward a request to the Durable Object for a given tvId.
 */
function getDurableObject(env: Env, tvId: string): DurableObjectStub {
  const id = env.RELAY.idFromName(tvId);
  return env.RELAY.get(id);
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const parsed = parsePath(url.pathname);

    if (!parsed) {
      return new Response("Not found", { status: 404 });
    }

    const { tvId, rest } = parsed;

    // --- Static assets ---
    if (request.method === "GET") {
      if (rest === "/" || rest === "/index.html") {
        return serveStaticAsset(env, "index.html");
      }
      if (rest === "/app.js") {
        return serveStaticAsset(env, "app.js");
      }
      if (rest === "/style.css") {
        return serveStaticAsset(env, "style.css");
      }
      if (rest === "/favicon.svg") {
        return serveStaticAsset(env, "favicon.svg");
      }
    }

    // --- WebSocket upgrade ---
    if (rest === "/ws") {
      const upgradeHeader = request.headers.get("Upgrade");
      if (upgradeHeader !== "websocket") {
        return new Response("Expected WebSocket upgrade", { status: 426 });
      }

      // IP connection limit for TV WebSockets
      const ip = getClientIp(request);
      if (!trackIpConnection(ip, tvId)) {
        return new Response(
          JSON.stringify({ error: "Too many TVs from this IP" }),
          { status: 429, headers: { "Content-Type": "application/json" } }
        );
      }

      const stub = getDurableObject(env, tvId);
      return stub.fetch(request);
    }

    // --- API forwarding ---
    if (rest.startsWith("/api/")) {
      // Extract the API path (strip /tv/{tvId} prefix)
      const apiPath = rest;

      // Check allowlist
      const allowed = isAllowed(apiPath, request.method);
      if (!allowed.allowed) {
        const status = allowed.reason === "method_not_allowed" ? 405 : 404;
        return Response.json({ error: allowed.reason }, { status });
      }

      // Rate limiting
      if (apiPath === "/api/auth/refresh") {
        // Special rate limit for token refresh — key by Authorization header
        const authHeader = request.headers.get("Authorization") || "anonymous";
        const refreshKey = `refresh:${tvId}:${authHeader}`;
        if (!refreshLimiter.consume(refreshKey, REFRESH_LIMIT)) {
          return Response.json(
            { error: "rate_limited" },
            { status: 429, headers: { "Retry-After": "3600" } }
          );
        }
      }

      // General phone rate limit per tvId
      const phoneKey = `phone:${tvId}`;
      if (!phoneLimiter.consume(phoneKey, PHONE_LIMIT)) {
        return Response.json(
          { error: "rate_limited" },
          { status: 429, headers: { "Retry-After": "60" } }
        );
      }

      // Payload size check
      const contentLength = request.headers.get("Content-Length");
      if (contentLength && parseInt(contentLength, 10) > MAX_REQUEST_BODY) {
        return Response.json(
          { error: "payload_too_large" },
          { status: 413 }
        );
      }

      // For methods with body, check actual body size
      if (request.method === "POST" || request.method === "DELETE") {
        // Clone to read body without consuming
        if (request.body) {
          const body = await request.clone().text();
          if (body.length > MAX_REQUEST_BODY) {
            return Response.json(
              { error: "payload_too_large" },
              { status: 413 }
            );
          }
        }
      }

      // Forward to Durable Object with stripped path
      const stub = getDurableObject(env, tvId);
      const doUrl = new URL(request.url);
      doUrl.pathname = apiPath;
      const doRequest = new Request(doUrl.toString(), {
        method: request.method,
        headers: request.headers,
        body: request.body,
      });

      const response = await stub.fetch(doRequest);

      // Validate response before returning to phone
      // Skip validation for error responses from the DO itself (4xx, 5xx with known errors)
      if (response.status === 503 || response.status === 504) {
        return response;
      }

      // For responses with a body, validate content type and size
      const validated = validateResponse(response);
      if (!validated) {
        return Response.json(
          { error: "invalid_upstream_response" },
          { status: 502 }
        );
      }

      // Check body size by reading the response
      const responseBody = await validated.text();
      if (responseBody.length > MAX_RESPONSE_BODY) {
        return Response.json(
          { error: "upstream_response_too_large" },
          { status: 502 }
        );
      }

      // Reconstruct response with validated body
      return new Response(responseBody, {
        status: validated.status,
        headers: validated.headers,
      });
    }

    return new Response("Not found", { status: 404 });
  },
} satisfies ExportedHandler<Env>;
