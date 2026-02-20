package com.ridepulse.rider.network

import android.util.Log
import com.ridepulse.rider.data.model.DeviceInfo
import com.ridepulse.rider.data.model.SensorData
import com.ridepulse.rider.data.model.SessionEnd
import com.ridepulse.rider.data.model.SessionStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataSender @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId.asStateFlow()

    private val webSocketListener = object : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket connected")
            _connectionState.value = ConnectionState.CONNECTED
        }

        override fun onMessage(ws: WebSocket, text: String) {
            Log.d(TAG, "Message from server: $text")
        }

        override fun onClosing(ws: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: $code - $reason")
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $code - $reason")
            _connectionState.value = ConnectionState.DISCONNECTED
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket error", t)
            _connectionState.value = ConnectionState.ERROR
        }
    }

    fun connect(wsUrl: String) {
        if (webSocket != null) {
            disconnect()
        }

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, webSocketListener)
        _connectionState.value = ConnectionState.CONNECTING
    }

    fun disconnect() {
        webSocket?.close(1000, "Closed by user")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _sessionId.value = null
    }

    fun startSession(
        riderId: String,
        devices: List<DeviceInfo>,
        apiUrl: String
    ) {
        val sessionStart = SessionStart(
            riderId = riderId,
            startTime = System.currentTimeMillis(),
            deviceInfo = devices
        )

        val jsonBody = json.encodeToString(sessionStart)
        val mediaType = "application/json".toMediaType()
        val body = jsonBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("$apiUrl/api/sessions/start")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "Failed to create session", e)
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val parsed = json.parseToJsonElement(responseBody ?: "{}").jsonObject
                    val sessionId = parsed["sessionId"]?.jsonPrimitive?.content
                    _sessionId.value = sessionId
                    Log.d(TAG, "Session created: $sessionId")
                } else {
                    Log.e(TAG, "Failed to create session: ${response.code}")
                }
            }
        })
    }

    fun endSession(riderId: String, apiUrl: String) {
        val currentSessionId = _sessionId.value ?: return

        val sessionEnd = SessionEnd(
            riderId = riderId,
            sessionId = currentSessionId,
            endTime = System.currentTimeMillis()
        )

        val jsonBody = json.encodeToString(sessionEnd)
        val mediaType = "application/json".toMediaType()
        val body = jsonBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("$apiUrl/api/sessions/end")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "Failed to end session", e)
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d(TAG, "Session ended")
                    _sessionId.value = null
                } else {
                    Log.e(TAG, "Failed to end session: ${response.code}")
                }
            }
        })
    }

    fun sendSensorData(data: SensorData) {
        webSocket?.let { ws ->
            try {
                val jsonMessage = json.encodeToString(data)
                ws.send(jsonMessage)
                Log.d(TAG, "Data sent: $jsonMessage")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send data", e)
            }
        } ?: run {
            Log.w(TAG, "WebSocket is not connected")
        }
    }

    fun isConnected(): Boolean = _connectionState.value == ConnectionState.CONNECTED

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    companion object {
        private const val TAG = "DataSender"
    }
}
