package com.ridepulse.rider.coach.data.websocket

import com.ridepulse.rider.BuildConfig
import com.ridepulse.rider.coach.model.Alert
import com.ridepulse.rider.coach.model.RiderMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class SocketEnvelope(
    val event: String,
    val data: String
)

@Singleton
class WebSocketClient @Inject constructor() {
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()
    private var socket: WebSocket? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val riderMetricsListeners = CopyOnWriteArrayList<(RiderMetrics) -> Unit>()
    private val alertListeners = CopyOnWriteArrayList<(Alert) -> Unit>()

    fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTED || _connectionState.value == ConnectionState.CONNECTING) {
            return
        }
        _connectionState.value = ConnectionState.CONNECTING
        val request = Request.Builder().url(BuildConfig.WS_URL).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = ConnectionState.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = ConnectionState.ERROR
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        })
    }

    fun disconnect() {
        socket?.close(1000, "disconnect")
        socket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun onRiderMetrics(listener: (RiderMetrics) -> Unit) {
        riderMetricsListeners += listener
    }

    fun onAlert(listener: (Alert) -> Unit) {
        alertListeners += listener
    }

    fun acknowledgeAlert(alertId: String) {
        val payload = "{\"event\":\"ack_alert\",\"alertId\":\"$alertId\"}"
        socket?.send(payload)
    }

    private fun handleMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        runCatching {
            val metrics = json.decodeFromString(RiderMetrics.serializer(), trimmed)
            riderMetricsListeners.forEach { it(metrics) }
            return
        }

        runCatching {
            val alert = json.decodeFromString(Alert.serializer(), trimmed)
            alertListeners.forEach { it(alert) }
            return
        }

        runCatching {
            val envelope = json.decodeFromString(SocketEnvelope.serializer(), trimmed)
            when (envelope.event) {
                "rider_metrics" -> {
                    val metrics = json.decodeFromString(RiderMetrics.serializer(), envelope.data)
                    riderMetricsListeners.forEach { it(metrics) }
                }
                "alert" -> {
                    val alert = json.decodeFromString(Alert.serializer(), envelope.data)
                    alertListeners.forEach { it(alert) }
                }
            }
        }
    }
}
