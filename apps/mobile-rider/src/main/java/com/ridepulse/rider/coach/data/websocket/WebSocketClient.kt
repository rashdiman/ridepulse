package com.ridepulse.rider.coach.data.websocket

import com.ridepulse.rider.BuildConfig
import com.ridepulse.rider.coach.model.ActiveSession
import com.ridepulse.rider.coach.model.Alert
import com.ridepulse.rider.coach.model.RiderMetrics
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketClient @Inject constructor() {
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    private val json = Json { ignoreUnknownKeys = true }
    private var socket: Socket? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val riderMetricsListeners = mutableListOf<(RiderMetrics) -> Unit>()
    private val alertListeners = mutableListOf<(Alert) -> Unit>()
    private val activeSessionsListeners = mutableListOf<(List<ActiveSession>) -> Unit>()
    private val sessionStartedListeners = mutableListOf<(ActiveSession) -> Unit>()
    private val sessionEndedListeners = mutableListOf<(ActiveSession) -> Unit>()

    fun connect(token: String? = null) {
        if (_connectionState.value == ConnectionState.CONNECTED || _connectionState.value == ConnectionState.CONNECTING) {
            return
        }

        _connectionState.value = ConnectionState.CONNECTING
        val url = BuildConfig.INGEST_URL.trimEnd('/')

        val opts = IO.Options()
        opts.forceNew = true
        opts.reconnection = true
        if (!token.isNullOrBlank()) {
            val headers = HashMap<String, List<String>>()
            headers["Authorization"] = listOf("Bearer $token")
            opts.extraHeaders = headers
        }

        socket = IO.socket(url, opts).apply {
            on(Socket.EVENT_CONNECT) {
                _connectionState.value = ConnectionState.CONNECTED
                if (!token.isNullOrBlank()) {
                    emit("authenticate", JSONObject().put("token", token))
                }
            }

            on(Socket.EVENT_CONNECT_ERROR) {
                _connectionState.value = ConnectionState.ERROR
            }

            on(Socket.EVENT_DISCONNECT) {
                _connectionState.value = ConnectionState.DISCONNECTED
            }

            on("rider_metrics") { args ->
                val payload = args.firstOrNull() ?: return@on
                val raw = when (payload) {
                    is JSONObject -> payload.toString()
                    is String -> payload
                    else -> payload.toString()
                }
                runCatching {
                    json.decodeFromString(RiderMetrics.serializer(), raw)
                }.onSuccess { metrics ->
                    riderMetricsListeners.forEach { it(metrics) }
                }
            }

            on("alert") { args ->
                val payload = args.firstOrNull() ?: return@on
                val raw = when (payload) {
                    is JSONObject -> payload.toString()
                    is String -> payload
                    else -> payload.toString()
                }
                runCatching {
                    json.decodeFromString(Alert.serializer(), raw)
                }.onSuccess { alert ->
                    alertListeners.forEach { it(alert) }
                }
            }

            on("active_sessions") { args ->
                val first = args.firstOrNull() ?: return@on
                val sessions = parseSessionList(first)
                if (sessions.isNotEmpty()) {
                    activeSessionsListeners.forEach { it(sessions) }
                } else {
                    activeSessionsListeners.forEach { it(emptyList()) }
                }
            }

            on("session_start") { args ->
                val session = args.firstOrNull()?.let(::parseSession) ?: return@on
                sessionStartedListeners.forEach { it(session) }
            }

            on("session_end") { args ->
                val session = args.firstOrNull()?.let(::parseSession) ?: return@on
                sessionEndedListeners.forEach { it(session) }
            }

            connect()
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun onRiderMetrics(listener: (RiderMetrics) -> Unit) {
        riderMetricsListeners += listener
    }

    fun onAlert(listener: (Alert) -> Unit) {
        alertListeners += listener
    }

    fun onActiveSessions(listener: (List<ActiveSession>) -> Unit) {
        activeSessionsListeners += listener
    }

    fun onSessionStarted(listener: (ActiveSession) -> Unit) {
        sessionStartedListeners += listener
    }

    fun onSessionEnded(listener: (ActiveSession) -> Unit) {
        sessionEndedListeners += listener
    }

    fun acknowledgeAlert(alertId: String) {
        socket?.emit("acknowledge_alert", JSONObject().put("alertId", alertId))
    }

    private fun parseSession(payload: Any): ActiveSession? {
        val raw = when (payload) {
            is JSONObject -> payload.toString()
            is String -> payload
            else -> payload.toString()
        }
        return runCatching { json.decodeFromString(ActiveSession.serializer(), raw) }.getOrNull()
    }

    private fun parseSessionList(payload: Any): List<ActiveSession> {
        return when (payload) {
            is JSONArray -> {
                buildList {
                    for (i in 0 until payload.length()) {
                        parseSession(payload.get(i))?.let { add(it) }
                    }
                }
            }
            else -> parseSession(payload)?.let { listOf(it) } ?: emptyList()
        }
    }
}
