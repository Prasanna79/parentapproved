/**
 * Durable Object integration tests for RelayDurableObject.
 * Tests the DO via its fetch() handler and internal state via runInDurableObject.
 * Runs in the Workers runtime using @cloudflare/vitest-pool-workers.
 */
import { env, runInDurableObject, runDurableObjectAlarm } from "cloudflare:test";
import { describe, it, expect } from "vitest";
import { PROTOCOL_VERSION } from "../src/protocol";

function getStub(name = "test-tv") {
  const id = env.RELAY.idFromName(name);
  return env.RELAY.get(id);
}

describe("RelayDurableObject", () => {
  describe("API requests when TV is offline", () => {
    it("returns 503 for API request with no TV connected", async () => {
      const stub = getStub("offline-1");
      const response = await stub.fetch("https://relay.test/api/status", {
        method: "GET",
      });
      expect(response.status).toBe(503);
      const body = await response.json() as { error: string };
      expect(body.error).toBe("TV is offline");
    });

    it("returns 503 for POST /api/auth when offline", async () => {
      const stub = getStub("offline-2");
      const response = await stub.fetch("https://relay.test/api/auth", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ pin: "123456" }),
      });
      expect(response.status).toBe(503);
    });

    it("returns 503 for GET /api/playlists when offline", async () => {
      const stub = getStub("offline-3");
      const response = await stub.fetch("https://relay.test/api/playlists", {
        method: "GET",
        headers: { Authorization: "Bearer some-token" },
      });
      expect(response.status).toBe(503);
    });

    it("returns 503 for POST /api/playback/pause when offline", async () => {
      const stub = getStub("offline-4");
      const response = await stub.fetch("https://relay.test/api/playback/pause", {
        method: "POST",
        headers: { Authorization: "Bearer some-token" },
      });
      expect(response.status).toBe(503);
    });

    it("returns 503 for GET /api/time-limits when offline", async () => {
      const stub = getStub("offline-5");
      const response = await stub.fetch("https://relay.test/api/time-limits", {
        method: "GET",
        headers: { Authorization: "Bearer some-token" },
      });
      expect(response.status).toBe(503);
    });
  });

  describe("WebSocket upgrade", () => {
    it("returns 101 with valid Upgrade header", async () => {
      const stub = getStub("ws-upgrade-1");
      const response = await stub.fetch("https://relay.test/ws", {
        headers: { Upgrade: "websocket" },
      });
      expect(response.status).toBe(101);
      expect(response.webSocket).toBeDefined();
    });

    it("returns 426 without Upgrade header", async () => {
      const stub = getStub("ws-upgrade-2");
      const response = await stub.fetch("https://relay.test/ws", {
        method: "GET",
      });
      expect(response.status).toBe(426);
    });

    it("returns 426 with wrong Upgrade header", async () => {
      const stub = getStub("ws-upgrade-3");
      const response = await stub.fetch("https://relay.test/ws", {
        headers: { Upgrade: "h2c" },
      });
      expect(response.status).toBe(426);
    });
  });

  describe("WebSocket connect flow", () => {
    it("accepts connection with valid ConnectMessage", async () => {
      const stub = getStub("connect-1");
      const response = await stub.fetch("https://relay.test/ws", {
        headers: { Upgrade: "websocket" },
      });
      expect(response.status).toBe(101);
      const ws = response.webSocket!;
      ws.accept();

      // Send ConnectMessage
      ws.send(JSON.stringify({
        type: "connect",
        tvId: "connect-1",
        tvSecret: "secret-abc-123",
        protocolVersion: PROTOCOL_VERSION,
        appVersion: "0.8.0",
      }));

      // Verify the DO accepted the connection by checking internal state
      const state = await runInDurableObject(stub, async (instance: any) => {
        return {
          authenticated: instance.authenticated,
          tvId: instance.tvId,
          tvSecret: instance.tvSecret,
        };
      });
      expect(state.authenticated).toBe(true);
      expect(state.tvId).toBe("connect-1");
      expect(state.tvSecret).toBe("secret-abc-123");

      ws.close(1000, "test done");
    });

    it("closes socket on protocol version mismatch", async () => {
      const stub = getStub("connect-version");
      const response = await stub.fetch("https://relay.test/ws", {
        headers: { Upgrade: "websocket" },
      });
      const ws = response.webSocket!;
      ws.accept();

      // Track close events
      let closeCode = 0;
      ws.addEventListener("close", (event) => {
        closeCode = event.code;
      });

      // Send ConnectMessage with wrong protocol version
      ws.send(JSON.stringify({
        type: "connect",
        tvId: "connect-version",
        tvSecret: "secret-xyz",
        protocolVersion: 999,
        appVersion: "0.8.0",
      }));

      // The DO should close the WebSocket with 4004 (invalid message)
      // Give the DO time to process
      await new Promise(r => setTimeout(r, 50));
      expect(closeCode).toBe(4004);
    });

    it("closes socket on invalid message", async () => {
      const stub = getStub("connect-invalid");
      const response = await stub.fetch("https://relay.test/ws", {
        headers: { Upgrade: "websocket" },
      });
      const ws = response.webSocket!;
      ws.accept();

      let closeCode = 0;
      ws.addEventListener("close", (event) => {
        closeCode = event.code;
      });

      // Send garbage
      ws.send("not valid json at all {{{");

      await new Promise(r => setTimeout(r, 50));
      expect(closeCode).toBe(4004);
    });

    it("closes socket on frame too large", async () => {
      const stub = getStub("connect-large");
      const response = await stub.fetch("https://relay.test/ws", {
        headers: { Upgrade: "websocket" },
      });
      const ws = response.webSocket!;
      ws.accept();

      let closeCode = 0;
      ws.addEventListener("close", (event) => {
        closeCode = event.code;
      });

      // Send oversized frame (>100KB)
      const largeMessage = "x".repeat(101 * 1024);
      ws.send(largeMessage);

      await new Promise(r => setTimeout(r, 50));
      expect(closeCode).toBe(4005);
    });

    it("replaces old connection when new TV connects with same secret", async () => {
      const stub = getStub("connect-replace");
      const sharedSecret = "shared-secret-for-replace";

      // First connection
      const resp1 = await stub.fetch("https://relay.test/ws", {
        headers: { Upgrade: "websocket" },
      });
      const ws1 = resp1.webSocket!;
      ws1.accept();

      let ws1CloseCode = 0;
      ws1.addEventListener("close", (event) => {
        ws1CloseCode = event.code;
      });

      ws1.send(JSON.stringify({
        type: "connect",
        tvId: "connect-replace",
        tvSecret: sharedSecret,
        protocolVersion: PROTOCOL_VERSION,
        appVersion: "0.8.0",
      }));

      // Wait for first connection to be established
      await new Promise(r => setTimeout(r, 50));

      // Second connection with same secret — should replace first
      const resp2 = await stub.fetch("https://relay.test/ws", {
        headers: { Upgrade: "websocket" },
      });
      const ws2 = resp2.webSocket!;
      ws2.accept();

      ws2.send(JSON.stringify({
        type: "connect",
        tvId: "connect-replace",
        tvSecret: sharedSecret,
        protocolVersion: PROTOCOL_VERSION,
        appVersion: "0.8.0",
      }));

      await new Promise(r => setTimeout(r, 50));

      // Old socket should be closed with 4002 (replaced)
      expect(ws1CloseCode).toBe(4002);

      // New connection should be authenticated
      const state = await runInDurableObject(stub, async (instance: any) => {
        return { authenticated: instance.authenticated, tvSecret: instance.tvSecret };
      });
      expect(state.authenticated).toBe(true);
      expect(state.tvSecret).toBe(sharedSecret);

      ws2.close(1000, "test done");
    });

    it("rejects connection with wrong secret when TV is already connected", async () => {
      const stub = getStub("connect-reject");

      // First connection
      const resp1 = await stub.fetch("https://relay.test/ws", {
        headers: { Upgrade: "websocket" },
      });
      const ws1 = resp1.webSocket!;
      ws1.accept();

      ws1.send(JSON.stringify({
        type: "connect",
        tvId: "connect-reject",
        tvSecret: "correct-secret",
        protocolVersion: PROTOCOL_VERSION,
        appVersion: "0.8.0",
      }));

      await new Promise(r => setTimeout(r, 50));

      // Second connection with WRONG secret — should be rejected
      const resp2 = await stub.fetch("https://relay.test/ws", {
        headers: { Upgrade: "websocket" },
      });
      const ws2 = resp2.webSocket!;
      ws2.accept();

      let ws2CloseCode = 0;
      ws2.addEventListener("close", (event) => {
        ws2CloseCode = event.code;
      });

      ws2.send(JSON.stringify({
        type: "connect",
        tvId: "connect-reject",
        tvSecret: "wrong-secret",
        protocolVersion: PROTOCOL_VERSION,
        appVersion: "0.8.0",
      }));

      await new Promise(r => setTimeout(r, 50));

      // Second socket should be closed with 4001 (invalid secret)
      expect(ws2CloseCode).toBe(4001);

      // Original connection should still be active
      const state = await runInDurableObject(stub, async (instance: any) => {
        return { authenticated: instance.authenticated, tvSecret: instance.tvSecret };
      });
      expect(state.authenticated).toBe(true);
      expect(state.tvSecret).toBe("correct-secret");

      ws1.close(1000, "test done");
    });
  });

  describe("secret rotation", () => {
    it("accepts new secret when no prior connection exists", async () => {
      const stub = getStub("secret-new");
      const response = await stub.fetch("https://relay.test/ws", {
        headers: { Upgrade: "websocket" },
      });
      const ws = response.webSocket!;
      ws.accept();

      ws.send(JSON.stringify({
        type: "connect",
        tvId: "secret-new",
        tvSecret: "brand-new-secret",
        protocolVersion: PROTOCOL_VERSION,
        appVersion: "0.8.0",
      }));

      await new Promise(r => setTimeout(r, 50));

      const state = await runInDurableObject(stub, async (instance: any) => {
        return { tvSecret: instance.tvSecret };
      });
      expect(state.tvSecret).toBe("brand-new-secret");

      ws.close(1000, "test done");
    });

    it("accepts rotated secret after disconnect (Bug 3 scenario)", async () => {
      const stub = getStub("secret-rotate");

      // First connection with old secret
      const resp1 = await stub.fetch("https://relay.test/ws", {
        headers: { Upgrade: "websocket" },
      });
      const ws1 = resp1.webSocket!;
      ws1.accept();

      ws1.send(JSON.stringify({
        type: "connect",
        tvId: "secret-rotate",
        tvSecret: "old-secret",
        protocolVersion: PROTOCOL_VERSION,
        appVersion: "0.8.0",
      }));

      await new Promise(r => setTimeout(r, 50));

      // Disconnect
      ws1.close(1000, "going away");
      await new Promise(r => setTimeout(r, 50));

      // Reconnect with rotated secret — should be accepted
      const resp2 = await stub.fetch("https://relay.test/ws", {
        headers: { Upgrade: "websocket" },
      });
      const ws2 = resp2.webSocket!;
      ws2.accept();

      let ws2CloseCode = 0;
      ws2.addEventListener("close", (event) => {
        ws2CloseCode = event.code;
      });

      ws2.send(JSON.stringify({
        type: "connect",
        tvId: "secret-rotate",
        tvSecret: "new-rotated-secret",
        protocolVersion: PROTOCOL_VERSION,
        appVersion: "0.8.0",
      }));

      await new Promise(r => setTimeout(r, 50));

      // Should NOT have been closed (secret accepted)
      expect(ws2CloseCode).toBe(0);

      const state = await runInDurableObject(stub, async (instance: any) => {
        return { authenticated: instance.authenticated, tvSecret: instance.tvSecret };
      });
      expect(state.authenticated).toBe(true);
      expect(state.tvSecret).toBe("new-rotated-secret");

      ws2.close(1000, "test done");
    });

    it("persists secret to storage", async () => {
      const stub = getStub("secret-persist");
      const response = await stub.fetch("https://relay.test/ws", {
        headers: { Upgrade: "websocket" },
      });
      const ws = response.webSocket!;
      ws.accept();

      ws.send(JSON.stringify({
        type: "connect",
        tvId: "secret-persist",
        tvSecret: "persisted-secret-value",
        protocolVersion: PROTOCOL_VERSION,
        appVersion: "0.8.0",
      }));

      await new Promise(r => setTimeout(r, 50));

      // Verify secret was persisted to DO storage
      const storedSecret = await runInDurableObject(stub, async (_instance: any, state: any) => {
        return await state.storage.get("tvSecret");
      });
      expect(storedSecret).toBe("persisted-secret-value");

      ws.close(1000, "test done");
    });
  });

  describe("heartbeat", () => {
    it("updates lastHeartbeat on heartbeat message", async () => {
      const stub = getStub("heartbeat-1");

      // Connect first
      const response = await stub.fetch("https://relay.test/ws", {
        headers: { Upgrade: "websocket" },
      });
      const ws = response.webSocket!;
      ws.accept();

      ws.send(JSON.stringify({
        type: "connect",
        tvId: "heartbeat-1",
        tvSecret: "hb-secret",
        protocolVersion: PROTOCOL_VERSION,
        appVersion: "0.8.0",
      }));

      await new Promise(r => setTimeout(r, 50));

      const beforeHeartbeat = await runInDurableObject(stub, async (instance: any) => {
        return instance.lastHeartbeat;
      });

      // Small delay to ensure timestamp changes
      await new Promise(r => setTimeout(r, 10));

      // Send heartbeat
      ws.send(JSON.stringify({
        type: "heartbeat",
        tvId: "heartbeat-1",
        uptime: 3600,
      }));

      await new Promise(r => setTimeout(r, 50));

      const afterHeartbeat = await runInDurableObject(stub, async (instance: any) => {
        return instance.lastHeartbeat;
      });

      expect(afterHeartbeat).toBeGreaterThanOrEqual(beforeHeartbeat);

      ws.close(1000, "test done");
    });
  });

  describe("alarm (heartbeat timeout)", () => {
    it("alarm with no connection does not crash", async () => {
      const stub = getStub("alarm-no-conn");

      // Just trigger alarm on a fresh DO — should be a no-op
      await runDurableObjectAlarm(stub);

      // Verify DO is still functional
      const response = await stub.fetch("https://relay.test/api/status");
      expect(response.status).toBe(503); // offline, but no crash
    });

    it("alarm does not disconnect fresh connection", async () => {
      const stub = getStub("alarm-fresh");

      // Connect
      const response = await stub.fetch("https://relay.test/ws", {
        headers: { Upgrade: "websocket" },
      });
      const ws = response.webSocket!;
      ws.accept();

      ws.send(JSON.stringify({
        type: "connect",
        tvId: "alarm-fresh",
        tvSecret: "alarm-secret",
        protocolVersion: PROTOCOL_VERSION,
        appVersion: "0.8.0",
      }));

      await new Promise(r => setTimeout(r, 50));

      // Trigger alarm immediately — heartbeat was just set, should NOT disconnect
      await runDurableObjectAlarm(stub);

      // Connection should still be alive
      const state = await runInDurableObject(stub, async (instance: any) => {
        return { authenticated: instance.authenticated, tvSocket: instance.tvSocket !== null };
      });
      expect(state.authenticated).toBe(true);
      expect(state.tvSocket).toBe(true);

      ws.close(1000, "test done");
    });
  });

  describe("request bridging", () => {
    it("bridges request to connected TV and returns response", async () => {
      const stub = getStub("bridge-1");

      // Connect TV
      const wsResp = await stub.fetch("https://relay.test/ws", {
        headers: { Upgrade: "websocket" },
      });
      const ws = wsResp.webSocket!;
      ws.accept();

      ws.send(JSON.stringify({
        type: "connect",
        tvId: "bridge-1",
        tvSecret: "bridge-secret",
        protocolVersion: PROTOCOL_VERSION,
        appVersion: "0.8.0",
      }));

      await new Promise(r => setTimeout(r, 50));

      // Listen for relay requests on the WebSocket
      let receivedRequest: any = null;
      ws.addEventListener("message", (event) => {
        const data = JSON.parse(event.data as string);
        if (data.id && data.method) {
          receivedRequest = data;
          // Respond back via the WebSocket
          ws.send(JSON.stringify({
            id: data.id,
            status: 200,
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ serverRunning: true, version: "0.8.0" }),
          }));
        }
      });

      // Make an API request that should be bridged
      const apiResp = await stub.fetch("https://relay.test/api/status", {
        method: "GET",
      });

      expect(apiResp.status).toBe(200);
      const body = await apiResp.json() as { serverRunning: boolean; version: string };
      expect(body.serverRunning).toBe(true);
      expect(body.version).toBe("0.8.0");

      // Verify the request was forwarded correctly
      expect(receivedRequest).not.toBeNull();
      expect(receivedRequest.method).toBe("GET");
      expect(receivedRequest.path).toBe("/api/status");

      ws.close(1000, "test done");
    });

    it("bridges POST with body to TV", async () => {
      const stub = getStub("bridge-post");

      // Connect TV
      const wsResp = await stub.fetch("https://relay.test/ws", {
        headers: { Upgrade: "websocket" },
      });
      const ws = wsResp.webSocket!;
      ws.accept();

      ws.send(JSON.stringify({
        type: "connect",
        tvId: "bridge-post",
        tvSecret: "bridge-secret-post",
        protocolVersion: PROTOCOL_VERSION,
        appVersion: "0.8.0",
      }));

      await new Promise(r => setTimeout(r, 50));

      ws.addEventListener("message", (event) => {
        const data = JSON.parse(event.data as string);
        if (data.id && data.method) {
          // Echo back the body we received to prove it was bridged
          ws.send(JSON.stringify({
            id: data.id,
            status: 200,
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ received: data.body }),
          }));
        }
      });

      const apiResp = await stub.fetch("https://relay.test/api/auth", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ pin: "123456" }),
      });

      expect(apiResp.status).toBe(200);
      const body = await apiResp.json() as { received: string };
      expect(body.received).toBe(JSON.stringify({ pin: "123456" }));

      ws.close(1000, "test done");
    });

    it("forwards Authorization header to TV", async () => {
      const stub = getStub("bridge-auth");

      const wsResp = await stub.fetch("https://relay.test/ws", {
        headers: { Upgrade: "websocket" },
      });
      const ws = wsResp.webSocket!;
      ws.accept();

      ws.send(JSON.stringify({
        type: "connect",
        tvId: "bridge-auth",
        tvSecret: "bridge-secret-auth",
        protocolVersion: PROTOCOL_VERSION,
        appVersion: "0.8.0",
      }));

      await new Promise(r => setTimeout(r, 50));

      let receivedHeaders: Record<string, string> = {};
      ws.addEventListener("message", (event) => {
        const data = JSON.parse(event.data as string);
        if (data.id && data.method) {
          receivedHeaders = data.headers;
          ws.send(JSON.stringify({
            id: data.id,
            status: 200,
            headers: { "Content-Type": "application/json" },
            body: "{}",
          }));
        }
      });

      await stub.fetch("https://relay.test/api/playlists", {
        method: "GET",
        headers: { Authorization: "Bearer test-token-xyz" },
      });

      expect(receivedHeaders["authorization"]).toBe("Bearer test-token-xyz");

      ws.close(1000, "test done");
    });
  });

  describe("cleanup after disconnect", () => {
    it("returns 503 after TV disconnects", async () => {
      const stub = getStub("cleanup-1");

      // Connect
      const wsResp = await stub.fetch("https://relay.test/ws", {
        headers: { Upgrade: "websocket" },
      });
      const ws = wsResp.webSocket!;
      ws.accept();

      ws.send(JSON.stringify({
        type: "connect",
        tvId: "cleanup-1",
        tvSecret: "cleanup-secret",
        protocolVersion: PROTOCOL_VERSION,
        appVersion: "0.8.0",
      }));

      await new Promise(r => setTimeout(r, 50));

      // Disconnect
      ws.close(1000, "going away");
      await new Promise(r => setTimeout(r, 50));

      // API request should now return 503
      const response = await stub.fetch("https://relay.test/api/status");
      expect(response.status).toBe(503);
    });

    it("cleans up authenticated state on disconnect", async () => {
      const stub = getStub("cleanup-2");

      // Connect and authenticate
      const wsResp = await stub.fetch("https://relay.test/ws", {
        headers: { Upgrade: "websocket" },
      });
      const ws = wsResp.webSocket!;
      ws.accept();

      ws.send(JSON.stringify({
        type: "connect",
        tvId: "cleanup-2",
        tvSecret: "cleanup-secret-2",
        protocolVersion: PROTOCOL_VERSION,
        appVersion: "0.8.0",
      }));

      await new Promise(r => setTimeout(r, 50));

      // Verify authenticated
      let state = await runInDurableObject(stub, async (instance: any) => {
        return { authenticated: instance.authenticated };
      });
      expect(state.authenticated).toBe(true);

      // Disconnect
      ws.close(1000, "going away");
      await new Promise(r => setTimeout(r, 50));

      // Verify cleaned up
      state = await runInDurableObject(stub, async (instance: any) => {
        return { authenticated: instance.authenticated };
      });
      expect(state.authenticated).toBe(false);
    });
  });

  describe("fresh DO state", () => {
    it("has no connection on fresh instance", async () => {
      const stub = getStub("fresh-1");
      const state = await runInDurableObject(stub, async (instance: any) => {
        return {
          tvSocket: instance.tvSocket,
          authenticated: instance.authenticated,
          tvId: instance.tvId,
        };
      });
      expect(state.tvSocket).toBeNull();
      expect(state.authenticated).toBe(false);
      expect(state.tvId).toBeNull();
    });
  });
});
