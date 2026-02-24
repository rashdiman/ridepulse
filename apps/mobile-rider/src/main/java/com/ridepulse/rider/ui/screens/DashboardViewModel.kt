package com.ridepulse.rider.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridepulse.rider.coach.data.websocket.WebSocketClient
import com.ridepulse.rider.coach.data.api.AuthApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val wsClient: WebSocketClient,
    private val authApi: AuthApi
) : ViewModel() {
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _activeSessions = MutableStateFlow<List<JSONObject>>(emptyList())
    val activeSessions: StateFlow<List<JSONObject>> = _activeSessions.asStateFlow()

    private val _latestMetric = MutableStateFlow<JSONObject?>(null)
    val latestMetric: StateFlow<JSONObject?> = _latestMetric.asStateFlow()

    init {
        wsClient.onActiveSessions { sessions ->
            viewModelScope.launch {
                _activeSessions.value = sessions.map { session ->
                    JSONObject()
                        .put("id", session.id)
                        .put("riderId", session.riderId)
                        .put("riderName", session.riderName ?: session.riderId)
                }
            }
        }

        wsClient.onRiderMetrics { metrics ->
            viewModelScope.launch {
                _latestMetric.value = JSONObject()
                    .put("riderId", metrics.riderId)
                    .put("riderName", metrics.riderName)
                    .put("sessionId", metrics.sessionId)
                    .put("heartRate", metrics.currentMetrics.heartRate)
                    .put("power", metrics.currentMetrics.power)
                    .put("cadence", metrics.currentMetrics.cadence)
                    .put("speed", metrics.currentMetrics.speed)
            }
        }

        wsClient.onAlert { alert ->
            // no-op for now
        }
    }

    fun connect() {
        viewModelScope.launch {
            val token = authApi.getStoredToken()
            wsClient.connect(token)
            _isConnected.value = true
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            wsClient.disconnect()
            _isConnected.value = false
        }
    }

    // helper to request active sessions via existing HTTP endpoint
    fun refreshActiveSessions(apiUrl: String) {
        viewModelScope.launch {
            try {
                // simple HTTP GET to /api/sessions/active
                val url = "${apiUrl.trimEnd('/')}/api/sessions/active"
                val text = java.net.URL(url).readText()
                val arr = JSONArray(text)
                val list = mutableListOf<JSONObject>()
                for (i in 0 until arr.length()) {
                    list.add(arr.getJSONObject(i))
                }
                _activeSessions.value = list
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
