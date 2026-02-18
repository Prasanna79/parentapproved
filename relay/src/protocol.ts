/**
 * WebSocket protocol types for relay communication.
 * These types are shared between the relay (TypeScript) and TV (Kotlin).
 * Changes here MUST be mirrored in tv-app RelayProtocol.kt.
 */

export const PROTOCOL_VERSION = 1;

/** Request forwarded from relay to TV via WebSocket */
export interface RelayRequest {
  id: string;
  method: string;
  path: string;
  headers: Record<string, string>;
  body: string | null;
}

/** Response from TV back to relay via WebSocket */
export interface RelayResponse {
  id: string;
  status: number;
  headers: Record<string, string>;
  body: string | null;
}

/** Sent by TV on WebSocket open */
export interface ConnectMessage {
  type: "connect";
  tvId: string;
  tvSecret: string;
  protocolVersion: number;
  appVersion: string;
}

/** Sent by TV every 30s to keep connection alive */
export interface HeartbeatMessage {
  type: "heartbeat";
  tvId: string;
  uptime: number;
}

/** Union type for all TV→Relay messages */
export type TvMessage = ConnectMessage | HeartbeatMessage | RelayResponse;

/** Parse a raw WebSocket message string into a typed message */
export function parseTvMessage(raw: string): TvMessage | null {
  try {
    const msg = JSON.parse(raw);
    if (typeof msg !== "object" || msg === null) return null;

    // ConnectMessage
    if (msg.type === "connect" && typeof msg.tvId === "string" && typeof msg.tvSecret === "string") {
      return msg as ConnectMessage;
    }

    // HeartbeatMessage
    if (msg.type === "heartbeat" && typeof msg.tvId === "string" && typeof msg.uptime === "number") {
      return msg as HeartbeatMessage;
    }

    // RelayResponse (has id + status)
    if (typeof msg.id === "string" && typeof msg.status === "number") {
      return msg as RelayResponse;
    }

    return null;
  } catch {
    return null;
  }
}

/** Serialize a RelayRequest to send to TV */
export function serializeRequest(req: RelayRequest): string {
  return JSON.stringify(req);
}

/** Validate that a parsed message is a ConnectMessage */
export function isConnectMessage(msg: TvMessage): msg is ConnectMessage {
  return "type" in msg && msg.type === "connect";
}

/** Validate that a parsed message is a HeartbeatMessage */
export function isHeartbeatMessage(msg: TvMessage): msg is HeartbeatMessage {
  return "type" in msg && msg.type === "heartbeat";
}

/** Validate that a parsed message is a RelayResponse */
export function isRelayResponse(msg: TvMessage): msg is RelayResponse {
  return "id" in msg && "status" in msg && !("type" in msg);
}
