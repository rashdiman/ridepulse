package com.ridepulse.rider.network

import android.util.Log
import com.ridepulse.rider.data.model.DeviceInfo
import com.ridepulse.rider.data.model.SensorData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataSender @Inject constructor(
    private val socketManager: SocketManager
) {
    private data class PendingSessionStart(
        val riderId: String,
        val devices: List<DeviceInfo>
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId.asStateFlow()
    private var pendingSessionStart: PendingSessionStart? = null

    fun connect() {
        _connectionState.value = ConnectionState.CONNECTING
        socketManager.connect(
            onConnected = {
                _connectionState.value = ConnectionState.CONNECTED
                pendingSessionStart?.let {
                    emitSessionStart(it.riderId, it.devices)
                    pendingSessionStart = null
                }
            },
            onDisconnected = { _connectionState.value = ConnectionState.DISCONNECTED },
            onError = {
                Log.e(TAG, it)
                _connectionState.value = ConnectionState.ERROR
            },
            onSessionCreated = { sessionId ->
                _sessionId.value = sessionId
                Log.d(TAG, "Session created: $sessionId")
            }
        )
    }

    fun disconnect() {
        runCatching { socketManager.disconnect() }
            .onFailure { Log.w(TAG, "Error while disconnecting SocketManager", it) }
        pendingSessionStart = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _sessionId.value = null
    }

    fun startSession(riderId: String, devices: List<DeviceInfo>) {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            pendingSessionStart = PendingSessionStart(riderId, devices)
            return
        }
        emitSessionStart(riderId, devices)
    }

    private fun emitSessionStart(riderId: String, devices: List<DeviceInfo>) {
        val payload = JSONArray()
        devices.forEach { device ->
            payload.put(
                JSONObject()
                    .put("id", device.id)
                    .put("name", device.name)
                    .put("type", device.type)
                    .put("address", device.address)
            )
        }
        socketManager.startSession(riderId, payload)
    }

    fun endSession() {
        val currentSessionId = _sessionId.value ?: return
        socketManager.stopSession(currentSessionId)
        _sessionId.value = null
    }

    fun sendSensorData(data: SensorData) {
        runCatching {
            val jsonMessage = json.encodeToString(data)
            socketManager.sendSensorData(JSONObject(jsonMessage))
            Log.d(TAG, "Data sent via SocketManager")
        }.onFailure {
            Log.e(TAG, "Failed to send data via SocketManager", it)
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
