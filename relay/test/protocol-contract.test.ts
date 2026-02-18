/**
 * Protocol contract tests — relay side.
 * Ensures the TypeScript protocol types serialize/parse identically to the Kotlin side.
 * Both sides use the same JSON fixtures. If either side drifts, these tests fail.
 */
import { describe, it, expect } from "vitest";
import {
  parseTvMessage,
  serializeRequest,
  PROTOCOL_VERSION,
  type RelayRequest,
  type ConnectMessage,
  type HeartbeatMessage,
  type RelayResponse,
} from "../src/protocol";

// Shared JSON fixtures — these MUST be identical to the Kotlin ProtocolContractTest
const CONNECT_JSON = '{"type":"connect","tvId":"contract-tv-001","tvSecret":"aabbccdd","protocolVersion":1,"appVersion":"0.4.0"}';
const HEARTBEAT_JSON = '{"type":"heartbeat","tvId":"contract-tv-001","uptime":7200}';
const REQUEST_JSON = '{"id":"contract-req-001","method":"POST","path":"/api/playlists","headers":{"Authorization":"Bearer token123","Content-Type":"application/json"},"body":"{\\"url\\":\\"https://youtube.com/playlist?list=PLabc\\"}"}';
const RESPONSE_JSON = '{"id":"contract-req-001","status":201,"headers":{"Content-Type":"application/json"},"body":"{\\"id\\":42,\\"displayName\\":\\"My Playlist\\"}"}';
const RESPONSE_NO_BODY_JSON = '{"id":"contract-req-002","status":204,"headers":{},"body":null}';

describe("protocol contract — relay side", () => {
  it("parses ConnectMessage from shared fixture", () => {
    const msg = parseTvMessage(CONNECT_JSON);
    expect(msg).not.toBeNull();
    const connect = msg as ConnectMessage;
    expect(connect.type).toBe("connect");
    expect(connect.tvId).toBe("contract-tv-001");
    expect(connect.tvSecret).toBe("aabbccdd");
    expect(connect.protocolVersion).toBe(1);
    expect(connect.appVersion).toBe("0.4.0");
  });

  it("parses HeartbeatMessage from shared fixture", () => {
    const msg = parseTvMessage(HEARTBEAT_JSON);
    expect(msg).not.toBeNull();
    const heartbeat = msg as HeartbeatMessage;
    expect(heartbeat.type).toBe("heartbeat");
    expect(heartbeat.tvId).toBe("contract-tv-001");
    expect(heartbeat.uptime).toBe(7200);
  });

  it("parses RelayResponse from shared fixture", () => {
    const msg = parseTvMessage(RESPONSE_JSON);
    expect(msg).not.toBeNull();
    const response = msg as RelayResponse;
    expect(response.id).toBe("contract-req-001");
    expect(response.status).toBe(201);
    expect(response.headers["Content-Type"]).toBe("application/json");
    expect(response.body).toContain("My Playlist");
  });

  it("parses RelayResponse with null body from shared fixture", () => {
    const msg = parseTvMessage(RESPONSE_NO_BODY_JSON);
    expect(msg).not.toBeNull();
    const response = msg as RelayResponse;
    expect(response.id).toBe("contract-req-002");
    expect(response.status).toBe(204);
    expect(response.body).toBeNull();
  });

  it("serializes RelayRequest matching shared fixture", () => {
    const req: RelayRequest = {
      id: "contract-req-001",
      method: "POST",
      path: "/api/playlists",
      headers: {
        "Authorization": "Bearer token123",
        "Content-Type": "application/json",
      },
      body: '{"url":"https://youtube.com/playlist?list=PLabc"}',
    };
    const json = serializeRequest(req);
    const parsed = JSON.parse(json);
    const expected = JSON.parse(REQUEST_JSON);
    expect(parsed.id).toBe(expected.id);
    expect(parsed.method).toBe(expected.method);
    expect(parsed.path).toBe(expected.path);
    expect(parsed.headers.Authorization).toBe(expected.headers.Authorization);
    expect(parsed.body).toBe(expected.body);
  });

  it("protocol version matches", () => {
    expect(PROTOCOL_VERSION).toBe(1);
  });
});
