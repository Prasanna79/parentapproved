package com.kidswatch.tv.relay

import org.junit.Assert.*
import org.junit.Test

/**
 * Protocol contract tests — TV (Kotlin) side.
 * Ensures the Kotlin protocol types serialize/parse identically to the TypeScript side.
 * Both sides use the same JSON fixtures. If either side drifts, these tests fail.
 */
class ProtocolContractTest {

    // Shared JSON fixtures — these MUST be identical to relay/test/protocol-contract.test.ts
    private val CONNECT_JSON = """{"type":"connect","tvId":"contract-tv-001","tvSecret":"aabbccdd","protocolVersion":1,"appVersion":"0.4.0"}"""
    private val HEARTBEAT_JSON = """{"type":"heartbeat","tvId":"contract-tv-001","uptime":7200}"""
    private val REQUEST_JSON = """{"id":"contract-req-001","method":"POST","path":"/api/playlists","headers":{"Authorization":"Bearer token123","Content-Type":"application/json"},"body":"{\"url\":\"https://youtube.com/playlist?list=PLabc\"}"}"""
    private val RESPONSE_JSON = """{"id":"contract-req-001","status":201,"headers":{"Content-Type":"application/json"},"body":"{\"id\":42,\"displayName\":\"My Playlist\"}"}"""
    private val RESPONSE_NO_BODY_JSON = """{"id":"contract-req-002","status":204,"headers":{},"body":null}"""

    @Test
    fun serializeConnect_matchesFixture() {
        val msg = ConnectMessage(
            tvId = "contract-tv-001",
            tvSecret = "aabbccdd",
            appVersion = "0.4.0",
        )
        val json = RelayJson.serializeConnect(msg)
        val parsed = RelayJson.json.parseToJsonElement(json)
        val expected = RelayJson.json.parseToJsonElement(CONNECT_JSON)
        assertEquals(expected, parsed)
    }

    @Test
    fun serializeHeartbeat_matchesFixture() {
        val msg = HeartbeatMessage(
            tvId = "contract-tv-001",
            uptime = 7200,
        )
        val json = RelayJson.serializeHeartbeat(msg)
        val parsed = RelayJson.json.parseToJsonElement(json)
        val expected = RelayJson.json.parseToJsonElement(HEARTBEAT_JSON)
        assertEquals(expected, parsed)
    }

    @Test
    fun parseRequest_matchesFixture() {
        val req = RelayJson.parseRequest(REQUEST_JSON)
        assertNotNull(req)
        assertEquals("contract-req-001", req!!.id)
        assertEquals("POST", req.method)
        assertEquals("/api/playlists", req.path)
        assertEquals("Bearer token123", req.headers["Authorization"])
        assertEquals("application/json", req.headers["Content-Type"])
        assertTrue(req.body!!.contains("PLabc"))
    }

    @Test
    fun serializeResponse_matchesFixture() {
        val resp = RelayResponse(
            id = "contract-req-001",
            status = 201,
            headers = mapOf("Content-Type" to "application/json"),
            body = """{"id":42,"displayName":"My Playlist"}""",
        )
        val json = RelayJson.serializeResponse(resp)
        val parsed = RelayJson.json.parseToJsonElement(json)
        val expected = RelayJson.json.parseToJsonElement(RESPONSE_JSON)
        assertEquals(expected, parsed)
    }

    @Test
    fun serializeResponse_nullBody_matchesFixture() {
        val resp = RelayResponse(
            id = "contract-req-002",
            status = 204,
            headers = emptyMap(),
            body = null,
        )
        val json = RelayJson.serializeResponse(resp)
        val parsed = RelayJson.json.parseToJsonElement(json)
        val expected = RelayJson.json.parseToJsonElement(RESPONSE_NO_BODY_JSON)
        assertEquals(expected, parsed)
    }

    @Test
    fun protocolVersion_matches() {
        assertEquals(1, PROTOCOL_VERSION)
    }
}
