/**
 * Application-aware path/method allowlist.
 * Only ParentApproved API paths are forwarded — prevents generic proxy abuse.
 */

interface AllowlistEntry {
  pattern: RegExp;
  methods: string[];
}

const ALLOWED_ROUTES: AllowlistEntry[] = [
  { pattern: /^\/api\/auth$/, methods: ["POST"] },
  { pattern: /^\/api\/auth\/refresh$/, methods: ["POST"] },
  { pattern: /^\/api\/playlists$/, methods: ["GET", "POST"] },
  { pattern: /^\/api\/playlists\/[^/]+$/, methods: ["GET", "DELETE"] },
  { pattern: /^\/api\/playback\/[^/]+$/, methods: ["POST"] },
  { pattern: /^\/api\/playback$/, methods: ["GET"] },
  { pattern: /^\/api\/stats$/, methods: ["GET"] },
  { pattern: /^\/api\/stats\/recent$/, methods: ["GET"] },
  { pattern: /^\/api\/status$/, methods: ["GET"] },
  { pattern: /^\/api\/time-limits$/, methods: ["GET", "PUT"] },
  { pattern: /^\/api\/time-limits\/lock$/, methods: ["POST"] },
  { pattern: /^\/api\/time-limits\/bonus$/, methods: ["POST"] },
  { pattern: /^\/api\/time-limits\/request$/, methods: ["POST"] },
];

const BLOCKED_METHODS = new Set(["CONNECT", "TRACE", "OPTIONS"]);

/**
 * Check if a request path + method is allowed through the relay.
 * Returns { allowed: true } or { allowed: false, reason: string }.
 */
export function isAllowed(
  path: string,
  method: string
): { allowed: true } | { allowed: false; reason: string } {
  // Block dangerous methods outright
  const upperMethod = method.toUpperCase();
  if (BLOCKED_METHODS.has(upperMethod)) {
    return { allowed: false, reason: "method_not_allowed" };
  }

  // Block path traversal
  if (path.includes("..") || path.includes("//")) {
    return { allowed: false, reason: "path_traversal" };
  }

  // Only allow methods we know about
  const validMethods = new Set(["GET", "POST", "PUT", "DELETE"]);
  if (!validMethods.has(upperMethod)) {
    return { allowed: false, reason: "method_not_allowed" };
  }

  // Check against allowlist
  for (const route of ALLOWED_ROUTES) {
    if (route.pattern.test(path) && route.methods.includes(upperMethod)) {
      return { allowed: true };
    }
  }

  // Path matched but method didn't? Check if path exists at all
  for (const route of ALLOWED_ROUTES) {
    if (route.pattern.test(path)) {
      return { allowed: false, reason: "method_not_allowed" };
    }
  }

  return { allowed: false, reason: "path_not_found" };
}
