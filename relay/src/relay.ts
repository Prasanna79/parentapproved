/**
 * KidsWatch Relay Durable Object.
 * Holds WebSocket connection to TV, bridges HTTPS requests from phones.
 *
 * Lifecycle:
 *   1. TV opens WebSocket to /tv/{tvId}/ws
 *   2. TV sends ConnectMessage with tvId + tvSecret
 *   3. DO validates secret, accepts connection
 *   4. Phone sends HTTPS to /tv/{tvId}/api/* → Worker forwards to DO
 *   5. DO creates RelayRequest, sends via WebSocket, waits for RelayResponse
 *   6. DO returns HTTP Response to phone
 */

import {
  parseTvMessage,
  serializeRequest,
  isConnectMessage,
  isHeartbeatMessage,
  isRelayResponse,
  type RelayRequest,
  type RelayResponse,
  type ConnectMessage,
  PROTOCOL_VERSION,
} from "./protocol";

export interface Env {
  RELAY: DurableObjectNamespace;
  __STATIC_CONTENT: KVNamespace;
}

/** Max WebSocket frame size: 100KB */
const MAX_FRAME_BYTES = 100 * 1024;

/** Request bridge timeout: 10 seconds */
const BRIDGE_TIMEOUT_MS = 10_000;

/** Heartbeat timeout: 90 seconds */
const HEARTBEAT_TIMEOUT_MS = 90_000;

/** Heartbeat check interval via alarm: 30 seconds */
const ALARM_INTERVAL_MS = 30_000;

/** WebSocket close codes */
const WS_CLOSE_INVALID_SECRET = 4001;
const WS_CLOSE_REPLACED = 4002;
const WS_CLOSE_HEARTBEAT_TIMEOUT = 4003;
const WS_CLOSE_INVALID_MESSAGE = 4004;
const WS_CLOSE_FRAME_TOO_LARGE = 4005;

/**
 * Timing-safe string comparison using crypto.subtle.timingSafeEqual.
 * Prevents timing attacks on secret validation.
 */
async function timingSafeEqual(a: string, b: string): Promise<boolean> {
  const encoder = new TextEncoder();
  const bufA = encoder.encode(a);
  const bufB = encoder.encode(b);

  // If lengths differ, compare a with itself to avoid timing leak
  // but still return false
  if (bufA.byteLength !== bufB.byteLength) {
    // Compare a with a to burn constant time, then return false
    crypto.subtle.timingSafeEqual(bufA, bufA);
    return false;
  }

  return crypto.subtle.timingSafeEqual(bufA, bufB);
}

/** Pending request waiting for a response from the TV */
interface PendingRequest {
  resolve: (response: RelayResponse) => void;
  reject: (error: Error) => void;
  timer: ReturnType<typeof setTimeout>;
}

export class RelayDurableObject implements DurableObject {
  private tvSocket: WebSocket | null = null;
  private tvId: string | null = null;
  private tvSecret: string | null = null;
  private lastHeartbeat: number = 0;
  private pendingRequests = new Map<string, PendingRequest>();
  private authenticated = false;

  constructor(
    private state: DurableObjectState,
    private env: Env
  ) {
    // Restore tvSecret from storage if set
    this.state.blockConcurrencyWhile(async () => {
      const secret = await this.state.storage.get<string>("tvSecret");
      if (secret) {
        this.tvSecret = secret;
      }
    });
  }

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);

    // WebSocket upgrade
    if (url.pathname.endsWith("/ws")) {
      return this.handleWebSocketUpgrade(request);
    }

    // API bridge request
    return this.handleApiRequest(request, url.pathname);
  }

  /**
   * Alarm handler: check heartbeat freshness, schedule next alarm.
   */
  async alarm(): Promise<void> {
    if (this.tvSocket && this.authenticated) {
      const now = Date.now();
      if (now - this.lastHeartbeat > HEARTBEAT_TIMEOUT_MS) {
        this.disconnectTv(WS_CLOSE_HEARTBEAT_TIMEOUT, "heartbeat timeout");
        return;
      }
      // Schedule next check
      await this.state.storage.setAlarm(Date.now() + ALARM_INTERVAL_MS);
    }
  }

  /**
   * Accept WebSocket upgrade request.
   * The TV must send a ConnectMessage as its first message.
   */
  private handleWebSocketUpgrade(request: Request): Response {
    const upgradeHeader = request.headers.get("Upgrade");
    if (upgradeHeader !== "websocket") {
      return new Response("Expected WebSocket upgrade", { status: 426 });
    }

    const pair = new WebSocketPair();
    const [client, server] = [pair[0], pair[1]];

    this.state.acceptWebSocket(server);

    return new Response(null, {
      status: 101,
      webSocket: client,
    });
  }

  /**
   * Durable Object WebSocket message handler (Hibernation API).
   */
  async webSocketMessage(ws: WebSocket, message: string | ArrayBuffer): Promise<void> {
    // Convert ArrayBuffer to string if needed
    const raw = typeof message === "string" ? message : new TextDecoder().decode(message);

    // Check frame size
    if (raw.length > MAX_FRAME_BYTES) {
      ws.close(WS_CLOSE_FRAME_TOO_LARGE, "frame too large");
      return;
    }

    const msg = parseTvMessage(raw);
    if (!msg) {
      ws.close(WS_CLOSE_INVALID_MESSAGE, "invalid message");
      return;
    }

    if (isConnectMessage(msg)) {
      await this.handleConnect(ws, msg);
    } else if (isHeartbeatMessage(msg)) {
      this.handleHeartbeat();
    } else if (isRelayResponse(msg)) {
      this.handleRelayResponse(msg);
    }
  }

  /**
   * Durable Object WebSocket close handler (Hibernation API).
   */
  async webSocketClose(ws: WebSocket, code: number, reason: string, wasClean: boolean): Promise<void> {
    if (ws === this.tvSocket) {
      this.cleanupConnection();
    }
  }

  /**
   * Durable Object WebSocket error handler (Hibernation API).
   */
  async webSocketError(ws: WebSocket, error: unknown): Promise<void> {
    if (ws === this.tvSocket) {
      this.cleanupConnection();
    }
  }

  /**
   * Handle ConnectMessage: validate secret, accept or reject.
   */
  private async handleConnect(ws: WebSocket, msg: ConnectMessage): Promise<void> {
    // If we don't have a stored secret yet, store the first one
    // (In production, the secret would be provisioned out-of-band)
    if (!this.tvSecret) {
      this.tvSecret = msg.tvSecret;
      await this.state.storage.put("tvSecret", this.tvSecret);
    }

    const valid = await timingSafeEqual(msg.tvSecret, this.tvSecret);
    if (!valid) {
      ws.close(WS_CLOSE_INVALID_SECRET, "invalid secret");
      return;
    }

    // Protocol version check
    if (msg.protocolVersion !== PROTOCOL_VERSION) {
      ws.close(WS_CLOSE_INVALID_MESSAGE, "protocol version mismatch");
      return;
    }

    // Replace old connection if exists
    if (this.tvSocket && this.tvSocket !== ws) {
      this.tvSocket.close(WS_CLOSE_REPLACED, "replaced");
      this.rejectAllPending("connection replaced");
    }

    this.tvSocket = ws;
    this.tvId = msg.tvId;
    this.authenticated = true;
    this.lastHeartbeat = Date.now();

    // Schedule heartbeat alarm
    await this.state.storage.setAlarm(Date.now() + ALARM_INTERVAL_MS);
  }

  /**
   * Handle HeartbeatMessage: update timestamp.
   */
  private handleHeartbeat(): void {
    this.lastHeartbeat = Date.now();
  }

  /**
   * Handle RelayResponse: resolve the pending request.
   */
  private handleRelayResponse(msg: RelayResponse): void {
    const pending = this.pendingRequests.get(msg.id);
    if (pending) {
      clearTimeout(pending.timer);
      this.pendingRequests.delete(msg.id);
      pending.resolve(msg);
    }
  }

  /**
   * Bridge an HTTP request to the TV via WebSocket.
   */
  private async handleApiRequest(request: Request, originalPath: string): Promise<Response> {
    if (!this.tvSocket || !this.authenticated) {
      return Response.json({ error: "TV is offline" }, { status: 503 });
    }

    // Extract the apiPath from the request — the Worker strips /tv/{tvId} before forwarding
    const apiPath = originalPath;

    return this.bridgeRequest(request, apiPath);
  }

  /**
   * Bridge a request: serialize, send via WebSocket, wait for response.
   */
  async bridgeRequest(request: Request, apiPath: string): Promise<Response> {
    if (!this.tvSocket || !this.authenticated) {
      return Response.json({ error: "TV is offline" }, { status: 503 });
    }

    const correlationId = crypto.randomUUID();

    // Extract headers (forward a safe subset)
    const headers: Record<string, string> = {};
    const forwardHeaders = ["authorization", "content-type", "accept"];
    for (const name of forwardHeaders) {
      const value = request.headers.get(name);
      if (value) {
        headers[name] = value;
      }
    }

    // Read body
    let body: string | null = null;
    if (request.method !== "GET" && request.method !== "HEAD") {
      const bodyText = await request.text();
      if (bodyText.length > 0) {
        body = bodyText;
      }
    }

    const relayRequest: RelayRequest = {
      id: correlationId,
      method: request.method,
      path: apiPath,
      headers,
      body,
    };

    // Create promise for the response
    const responsePromise = new Promise<RelayResponse>((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pendingRequests.delete(correlationId);
        reject(new Error("bridge timeout"));
      }, BRIDGE_TIMEOUT_MS);

      this.pendingRequests.set(correlationId, { resolve, reject, timer });
    });

    // Send to TV
    try {
      this.tvSocket.send(serializeRequest(relayRequest));
    } catch {
      this.pendingRequests.delete(correlationId);
      return Response.json({ error: "TV is offline" }, { status: 503 });
    }

    // Wait for response
    try {
      const relayResponse = await responsePromise;

      // Build HTTP response from relay response
      const responseHeaders = new Headers();
      if (relayResponse.headers) {
        for (const [key, value] of Object.entries(relayResponse.headers)) {
          responseHeaders.set(key, value);
        }
      }

      return new Response(relayResponse.body, {
        status: relayResponse.status,
        headers: responseHeaders,
      });
    } catch {
      return new Response(JSON.stringify({ error: "TV did not respond in time" }), {
        status: 504,
        headers: { "Content-Type": "application/json" },
      });
    }
  }

  /**
   * Disconnect the TV socket and clean up.
   */
  private disconnectTv(code: number, reason: string): void {
    if (this.tvSocket) {
      try {
        this.tvSocket.close(code, reason);
      } catch {
        // Socket may already be closed
      }
    }
    this.cleanupConnection();
  }

  /**
   * Clean up after disconnection.
   */
  private cleanupConnection(): void {
    this.tvSocket = null;
    this.authenticated = false;
    this.rejectAllPending("TV disconnected");
  }

  /**
   * Reject all pending bridge requests.
   */
  private rejectAllPending(reason: string): void {
    for (const [id, pending] of this.pendingRequests) {
      clearTimeout(pending.timer);
      pending.reject(new Error(reason));
    }
    this.pendingRequests.clear();
  }
}
