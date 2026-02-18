package tv.parentapproved.app.relay

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

enum class RelayConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED
}

/**
 * Map relay API paths to local Ktor paths.
 * Relay uses /api/auth, /api/playlists etc.
 * TV Ktor uses /auth, /playlists etc. (no /api prefix).
 */
fun mapRelayPathToLocal(path: String): String = path.removePrefix("/api")

class RelayConnector(
    private val config: RelayConfig,
    private val localServerPort: Int = 8080,
    private val clock: () -> Long = System::currentTimeMillis,
    private val heartbeatIntervalMs: Long = 30_000,
    private val maxBackoffMs: Long = 60_000,
    private val requestTimeoutMs: Long = 10_000,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // no timeout for WS
        .build(),
    private val localHttpClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val webSocketFactory: WebSocketFactory = OkHttpWebSocketFactory(okHttpClient),
) {
    var state: RelayConnectionState = RelayConnectionState.DISCONNECTED
        private set

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var scope: CoroutineScope? = null
    private var currentBackoffMs: Long = 1000
    private var shouldReconnect = true
    private var startTime: Long = 0

    fun connect() {
        if (state != RelayConnectionState.DISCONNECTED) return
        shouldReconnect = true
        startTime = clock()
        scope = CoroutineScope(dispatcher + SupervisorJob())
        doConnect()
    }

    fun disconnect() {
        shouldReconnect = false
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        state = RelayConnectionState.DISCONNECTED
        scope?.cancel()
        scope = null
    }

    fun reconnectNow() {
        if (state == RelayConnectionState.CONNECTED || state == RelayConnectionState.CONNECTING) {
            webSocket?.close(1000, "Reconnecting")
            webSocket = null
            heartbeatJob?.cancel()
        }
        currentBackoffMs = 1000 // reset backoff
        doConnect()
    }

    private fun doConnect() {
        state = RelayConnectionState.CONNECTING
        val wsUrl = "${config.relayUrl.replace("https://", "wss://").replace("http://", "ws://")}/tv/${config.tvId}/ws"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = webSocketFactory.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                val connectMsg = ConnectMessage(
                    tvId = config.tvId,
                    tvSecret = config.tvSecret,
                    appVersion = "0.4.0",
                )
                ws.send(RelayJson.serializeConnect(connectMsg))
                state = RelayConnectionState.CONNECTED
                currentBackoffMs = 1000 // reset backoff on successful connect
                startHeartbeat(ws)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text, ws)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                state = RelayConnectionState.DISCONNECTED
                webSocket = null
                heartbeatJob?.cancel()
                scheduleReconnect()
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(code, reason)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                state = RelayConnectionState.DISCONNECTED
                webSocket = null
                heartbeatJob?.cancel()
                scheduleReconnect()
            }
        })
    }

    private fun handleMessage(text: String, ws: WebSocket) {
        val request = RelayJson.parseRequest(text) ?: return

        scope?.launch {
            try {
                val response = bridgeToLocal(request)
                ws.send(RelayJson.serializeResponse(response))
            } catch (e: Exception) {
                val errorResponse = RelayResponse(
                    id = request.id,
                    status = 500,
                    headers = mapOf("Content-Type" to "application/json"),
                    body = """{"error":"Internal server error"}""",
                )
                ws.send(RelayJson.serializeResponse(errorResponse))
            }
        }
    }

    private suspend fun bridgeToLocal(relayRequest: RelayRequest): RelayResponse {
        return withContext(dispatcher) {
            val localPath = mapRelayPathToLocal(relayRequest.path)
            val url = "http://127.0.0.1:$localServerPort$localPath"
            val requestBuilder = Request.Builder().url(url)

            // Forward headers
            relayRequest.headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            // Set method + body
            val body = relayRequest.body?.let {
                val mediaType = (relayRequest.headers["Content-Type"] ?: "application/json").toMediaTypeOrNull()
                it.toRequestBody(mediaType)
            }

            when (relayRequest.method.uppercase()) {
                "GET" -> requestBuilder.get()
                "POST" -> requestBuilder.post(body ?: "".toRequestBody(null))
                "DELETE" -> requestBuilder.delete(body)
                else -> requestBuilder.method(relayRequest.method, body)
            }

            val localResponse = localHttpClient.newCall(requestBuilder.build()).execute()

            val responseHeaders = mutableMapOf<String, String>()
            localResponse.headers.names().forEach { name ->
                localResponse.header(name)?.let { responseHeaders[name] = it }
            }

            RelayResponse(
                id = relayRequest.id,
                status = localResponse.code,
                headers = responseHeaders,
                body = localResponse.body?.string(),
            )
        }
    }

    private fun startHeartbeat(ws: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope?.launch {
            while (isActive) {
                delay(heartbeatIntervalMs)
                val heartbeat = HeartbeatMessage(
                    tvId = config.tvId,
                    uptime = (clock() - startTime) / 1000,
                )
                ws.send(RelayJson.serializeHeartbeat(heartbeat))
            }
        }
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        reconnectJob?.cancel()
        val delayMs = currentBackoffMs
        currentBackoffMs = (currentBackoffMs * 2).coerceAtMost(maxBackoffMs)
        reconnectJob = scope?.launch {
            delay(delayMs)
            doConnect()
        }
    }

    // For testing
    fun getCurrentBackoffMs(): Long = currentBackoffMs
}

interface WebSocketFactory {
    fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket
}

class OkHttpWebSocketFactory(private val client: OkHttpClient) : WebSocketFactory {
    override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
        return client.newWebSocket(request, listener)
    }
}
