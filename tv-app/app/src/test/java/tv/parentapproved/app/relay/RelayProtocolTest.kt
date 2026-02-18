package tv.parentapproved.app.relay

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class RelayProtocolTest {

    @Test
    fun protocolVersion_is1() {
        assertEquals(1, PROTOCOL_VERSION)
    }

    @Test
    fun serializeConnect_producesValidJson() {
        val msg = ConnectMessage(
            tvId = "abc-123",
            tvSecret = "deadbeef",
            appVersion = "0.4.0",
        )
        val json = RelayJson.serializeConnect(msg)
        val parsed = Json.parseToJsonElement(json).jsonObject
        assertEquals("connect", parsed["type"]?.jsonPrimitive?.content)
        assertEquals("abc-123", parsed["tvId"]?.jsonPrimitive?.content)
        assertEquals("deadbeef", parsed["tvSecret"]?.jsonPrimitive?.content)
        assertEquals("1", parsed["protocolVersion"]?.jsonPrimitive?.content)
        assertEquals("0.4.0", parsed["appVersion"]?.jsonPrimitive?.content)
    }

    @Test
    fun serializeHeartbeat_producesValidJson() {
        val msg = HeartbeatMessage(tvId = "abc-123", uptime = 3600)
        val json = RelayJson.serializeHeartbeat(msg)
        val parsed = Json.parseToJsonElement(json).jsonObject
        assertEquals("heartbeat", parsed["type"]?.jsonPrimitive?.content)
        assertEquals("abc-123", parsed["tvId"]?.jsonPrimitive?.content)
        assertEquals("3600", parsed["uptime"]?.jsonPrimitive?.content)
    }

    @Test
    fun serializeResponse_producesValidJson() {
        val msg = RelayResponse(
            id = "req-001",
            status = 200,
            headers = mapOf("Content-Type" to "application/json"),
            body = """{"playlists":[]}""",
        )
        val json = RelayJson.serializeResponse(msg)
        val parsed = Json.parseToJsonElement(json).jsonObject
        assertEquals("req-001", parsed["id"]?.jsonPrimitive?.content)
        assertEquals("200", parsed["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun parseRequest_validJson() {
        val raw = """{"id":"req-001","method":"GET","path":"/api/playlists","headers":{"Authorization":"Bearer xyz"},"body":null}"""
        val req = RelayJson.parseRequest(raw)
        assertNotNull(req)
        assertEquals("req-001", req!!.id)
        assertEquals("GET", req.method)
        assertEquals("/api/playlists", req.path)
        assertEquals("Bearer xyz", req.headers["Authorization"])
        assertNull(req.body)
    }

    @Test
    fun parseRequest_withBody() {
        val raw = """{"id":"req-002","method":"POST","path":"/api/playlists","headers":{"Content-Type":"application/json"},"body":"{\"url\":\"https://youtube.com\"}"}"""
        val req = RelayJson.parseRequest(raw)
        assertNotNull(req)
        assertEquals("POST", req!!.method)
        assertNotNull(req.body)
    }

    @Test
    fun parseRequest_invalidJson_returnsNull() {
        assertNull(RelayJson.parseRequest("not json"))
    }

    @Test
    fun parseRequest_missingFields_returnsNull() {
        assertNull(RelayJson.parseRequest("""{"id":"req-001"}"""))
    }

    @Test
    fun serializeResponse_nullBody() {
        val msg = RelayResponse(id = "r1", status = 204, headers = emptyMap(), body = null)
        val json = RelayJson.serializeResponse(msg)
        assertTrue(json.contains("null"))
    }
}
