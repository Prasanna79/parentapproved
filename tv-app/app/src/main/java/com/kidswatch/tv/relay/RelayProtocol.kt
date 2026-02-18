package com.kidswatch.tv.relay

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val PROTOCOL_VERSION = 1

@Serializable
data class RelayRequest(
    val id: String,
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: String? = null,
)

@Serializable
data class RelayResponse(
    val id: String,
    val status: Int,
    val headers: Map<String, String>,
    val body: String? = null,
)

@Serializable
data class ConnectMessage(
    val type: String = "connect",
    val tvId: String,
    val tvSecret: String,
    val protocolVersion: Int = PROTOCOL_VERSION,
    val appVersion: String,
)

@Serializable
data class HeartbeatMessage(
    val type: String = "heartbeat",
    val tvId: String,
    val uptime: Long,
)

object RelayJson {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun serializeConnect(msg: ConnectMessage): String = json.encodeToString(msg)
    fun serializeHeartbeat(msg: HeartbeatMessage): String = json.encodeToString(msg)
    fun serializeResponse(msg: RelayResponse): String = json.encodeToString(msg)
    fun parseRequest(raw: String): RelayRequest? = try {
        json.decodeFromString<RelayRequest>(raw)
    } catch (e: Exception) {
        null
    }
}
