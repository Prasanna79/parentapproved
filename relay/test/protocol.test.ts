import { describe, it, expect } from "vitest";
import {
  parseTvMessage,
  serializeRequest,
  isConnectMessage,
  isHeartbeatMessage,
  isRelayResponse,
  PROTOCOL_VERSION,
  type RelayRequest,
  type ConnectMessage,
  type HeartbeatMessage,
  type RelayResponse,
} from "../src/protocol";

describe("protocol", () => {
  describe("PROTOCOL_VERSION", () => {
    it("is 1", () => {
      expect(PROTOCOL_VERSION).toBe(1);
    });
  });

  describe("parseTvMessage", () => {
    it("parses ConnectMessage", () => {
      const raw = JSON.stringify({
        type: "connect",
        tvId: "abc-123",
        tvSecret: "deadbeef",
        protocolVersion: 1,
        appVersion: "0.4.0",
      });
      const msg = parseTvMessage(raw);
      expect(msg).not.toBeNull();
      expect(isConnectMessage(msg!)).toBe(true);
      expect((msg as ConnectMessage).tvId).toBe("abc-123");
      expect((msg as ConnectMessage).tvSecret).toBe("deadbeef");
    });

    it("parses HeartbeatMessage", () => {
      const raw = JSON.stringify({
        type: "heartbeat",
        tvId: "abc-123",
        uptime: 3600,
      });
      const msg = parseTvMessage(raw);
      expect(msg).not.toBeNull();
      expect(isHeartbeatMessage(msg!)).toBe(true);
      expect((msg as HeartbeatMessage).uptime).toBe(3600);
    });

    it("parses RelayResponse", () => {
      const raw = JSON.stringify({
        id: "req-001",
        status: 200,
        headers: { "Content-Type": "application/json" },
        body: '{"playlists":[]}',
      });
      const msg = parseTvMessage(raw);
      expect(msg).not.toBeNull();
      expect(isRelayResponse(msg!)).toBe(true);
      expect((msg as RelayResponse).status).toBe(200);
    });

    it("returns null for invalid JSON", () => {
      expect(parseTvMessage("not json")).toBeNull();
    });

    it("returns null for unknown message format", () => {
      expect(parseTvMessage(JSON.stringify({ foo: "bar" }))).toBeNull();
    });

    it("returns null for non-object JSON", () => {
      expect(parseTvMessage('"just a string"')).toBeNull();
      expect(parseTvMessage("42")).toBeNull();
      expect(parseTvMessage("null")).toBeNull();
    });
  });

  describe("serializeRequest", () => {
    it("serializes a RelayRequest to JSON", () => {
      const req: RelayRequest = {
        id: "req-001",
        method: "GET",
        path: "/api/playlists",
        headers: { Authorization: "Bearer xyz" },
        body: null,
      };
      const json = serializeRequest(req);
      const parsed = JSON.parse(json);
      expect(parsed.id).toBe("req-001");
      expect(parsed.method).toBe("GET");
      expect(parsed.path).toBe("/api/playlists");
      expect(parsed.headers.Authorization).toBe("Bearer xyz");
      expect(parsed.body).toBeNull();
    });

    it("serializes request with body", () => {
      const req: RelayRequest = {
        id: "req-002",
        method: "POST",
        path: "/api/playlists",
        headers: { "Content-Type": "application/json" },
        body: '{"url":"https://youtube.com/playlist?list=abc"}',
      };
      const json = serializeRequest(req);
      const parsed = JSON.parse(json);
      expect(parsed.body).toBe('{"url":"https://youtube.com/playlist?list=abc"}');
    });
  });

  describe("type guards", () => {
    it("isConnectMessage identifies connect messages", () => {
      const msg: ConnectMessage = {
        type: "connect",
        tvId: "t1",
        tvSecret: "s1",
        protocolVersion: 1,
        appVersion: "0.4.0",
      };
      expect(isConnectMessage(msg)).toBe(true);
      expect(isHeartbeatMessage(msg)).toBe(false);
      expect(isRelayResponse(msg)).toBe(false);
    });

    it("isHeartbeatMessage identifies heartbeat messages", () => {
      const msg: HeartbeatMessage = {
        type: "heartbeat",
        tvId: "t1",
        uptime: 100,
      };
      expect(isHeartbeatMessage(msg)).toBe(true);
      expect(isConnectMessage(msg)).toBe(false);
    });

    it("isRelayResponse identifies responses", () => {
      const msg: RelayResponse = {
        id: "r1",
        status: 200,
        headers: {},
        body: null,
      };
      expect(isRelayResponse(msg)).toBe(true);
      expect(isConnectMessage(msg)).toBe(false);
    });
  });
});
