package com.ridepulse.rider.network

import android.util.Log
import com.ridepulse.rider.BuildConfig
import com.ridepulse.rider.coach.data.api.AuthApi
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocketManager @Inject constructor(
    private val authApi: AuthApi
) {
    private var socket: Socket? = null

    fun connect(
        onConnected: () -> Unit = {},
        onDisconnected: () -> Unit = {},
        onError: (String) -> Unit = {},
        onActiveSessions: (List<JSONObject>) -> Unit = {},
        onRiderMetrics: (JSONObject) -> Unit = {},
        onSessionCreated: (String) -> Unit = {}
    ) {
        val token = authApi.getStoredToken() ?: run {
            val msg = "No token available, not connecting socket"
            Log.w(TAG, msg)
            onError(msg)
            return
        }

        runCatching {
            val opts = IO.Options().apply {
                forceNew = true
                reconnection = true
                extraHeaders = hashMapOf("Authorization" to listOf("Bearer $token"))
            }
            val url = BuildConfig.INGEST_URL.trimEnd('/')
            socket = IO.socket(url, opts)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Socket connected")
                onConnected()
                runCatching {
                    socket?.emit("authenticate", JSONObject().put("token", token))
                }
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.w(TAG, "Socket disconnected")
                onDisconnected()
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                val message = args.firstOrNull()?.toString() ?: "Socket connect error"
                Log.e(TAG, message)
                onError(message)
            }

            socket?.on("active_sessions") { args ->
                val first = args.firstOrNull() ?: return@on
                val list = mutableListOf<JSONObject>()
                when (first) {
                    is JSONArray -> {
                        for (i in 0 until first.length()) {
                            list.add(first.getJSONObject(i))
                        }
                    }
                    is JSONObject -> list.add(first)
                }
                onActiveSessions(list)
            }

            socket?.on("rider_metrics") { args ->
                val first = args.firstOrNull() ?: return@on
                val obj = when (first) {
                    is JSONObject -> first
                    is String -> runCatching { JSONObject(first) }.getOrNull()
                    else -> runCatching { JSONObject(first.toString()) }.getOrNull()
                } ?: return@on
                onRiderMetrics(obj)
            }

            socket?.on("session_created") { args ->
                val first = args.firstOrNull() ?: return@on
                val sessionId = when (first) {
                    is JSONObject -> first.optString("sessionId")
                    is String -> runCatching { JSONObject(first).optString("sessionId") }.getOrDefault("")
                    else -> ""
                }
                if (sessionId.isNotBlank()) {
                    onSessionCreated(sessionId)
                }
            }

            socket?.connect()
        }.onFailure { e ->
            val msg = e.message ?: "Invalid socket URL"
            Log.e(TAG, msg, e)
            onError(msg)
        }
    }

    fun sendSensorData(json: JSONObject) {
        socket?.emit("sensor_data", json)
    }

    fun startSession(riderId: String, deviceInfo: JSONArray) {
        val tokenUserId = authApi.getStoredToken()
            ?.let { extractUserIdFromToken(it) }
            ?.takeIf { it.isNotBlank() }
        val effectiveRiderId = tokenUserId ?: riderId

        val payload = JSONObject()
        payload.put("riderId", effectiveRiderId)
        payload.put("deviceInfo", deviceInfo)
        socket?.emit("session_start", payload)
    }

    fun stopSession(sessionId: String) {
        val payload = JSONObject()
        payload.put("sessionId", sessionId)
        socket?.emit("session_end", payload)
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }

    private fun extractUserIdFromToken(token: String): String {
        return runCatching {
            val parts = token.split(".")
            if (parts.size < 2) return ""
            val decoded = Base64.getUrlDecoder().decode(parts[1])
            JSONObject(String(decoded, Charsets.UTF_8)).optString("userId")
        }.getOrDefault("")
    }

    companion object {
        private const val TAG = "SocketManager"
    }
}
