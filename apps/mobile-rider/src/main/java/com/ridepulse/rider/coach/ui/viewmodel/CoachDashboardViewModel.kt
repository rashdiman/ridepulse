package com.ridepulse.rider.coach.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridepulse.rider.coach.data.api.AuthApi
import com.ridepulse.rider.coach.data.api.RidersApi
import com.ridepulse.rider.coach.data.websocket.WebSocketClient
import com.ridepulse.rider.coach.model.ActiveSession
import com.ridepulse.rider.coach.model.Alert
import com.ridepulse.rider.coach.model.RiderWithMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class CoachDashboardUiState {
    object Loading : CoachDashboardUiState()
    data class Success(val riders: List<RiderWithMetrics>) : CoachDashboardUiState()
    data class Error(val message: String) : CoachDashboardUiState()
}

@HiltViewModel
class CoachDashboardViewModel @Inject constructor(
    private val ridersApi: RidersApi,
    private val authApi: AuthApi,
    private val wsClient: WebSocketClient
) : ViewModel() {
    private var started = false

    private val _uiState = MutableStateFlow<CoachDashboardUiState>(CoachDashboardUiState.Loading)
    val uiState: StateFlow<CoachDashboardUiState> = _uiState.asStateFlow()

    private val _selectedRiderId = MutableStateFlow<String?>(null)
    val selectedRiderId: StateFlow<String?> = _selectedRiderId.asStateFlow()

    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()
    private val activeSessionRiderIds = MutableStateFlow<Set<String>>(emptySet())

    val connectionState: StateFlow<WebSocketClient.ConnectionState> = wsClient.connectionState

    fun ensureStarted() {
        if (started) return
        started = true
        loadRiders()
        connectWebSocket()
    }

    fun loadRiders() {
        viewModelScope.launch {
            _uiState.value = CoachDashboardUiState.Loading
            runCatching { ridersApi.getRiders() }
                .onSuccess { riders ->
                    _uiState.value = CoachDashboardUiState.Success(applyActiveStatuses(riders))
                }
                .onFailure { e -> _uiState.value = CoachDashboardUiState.Error(e.message ?: "Failed to load riders") }
        }
    }

    private fun connectWebSocket() {
        wsClient.onRiderMetrics { metrics ->
            val current = _uiState.value
            if (current !is CoachDashboardUiState.Success) return@onRiderMetrics
            val updated = current.riders.map { rider ->
                if (rider.id == metrics.riderId) {
                    rider.copy(
                        currentMetrics = metrics,
                        status = if (activeSessionRiderIds.value.contains(rider.id) || metrics.currentMetrics.heartRate != null) "active" else "idle"
                    )
                } else {
                    rider
                }
            }
            _uiState.value = CoachDashboardUiState.Success(updated)
        }

        wsClient.onAlert { alert ->
            _alerts.value = listOf(alert) + _alerts.value
        }

        wsClient.onActiveSessions { sessions ->
            updateActiveSessions(sessions)
        }

        wsClient.onSessionStarted { session ->
            updateActiveSessions(addSession = session)
        }

        wsClient.onSessionEnded { session ->
            updateActiveSessions(removeSession = session)
        }

        wsClient.connect(authApi.getStoredToken())
    }

    fun selectRider(riderId: String) {
        _selectedRiderId.value = riderId
    }

    fun clearSelectedRider() {
        _selectedRiderId.value = null
    }

    fun acknowledgeAlert(alertId: String) {
        wsClient.acknowledgeAlert(alertId)
        _alerts.value = _alerts.value.map { alert ->
            if (alert.id == alertId) alert.copy(acknowledged = true) else alert
        }
    }

    override fun onCleared() {
        super.onCleared()
        wsClient.disconnect()
    }

    private fun updateActiveSessions(
        sessions: List<ActiveSession>? = null,
        addSession: ActiveSession? = null,
        removeSession: ActiveSession? = null
    ) {
        val updatedSet = when {
            sessions != null -> sessions.map { it.riderId }.toSet()
            addSession != null -> activeSessionRiderIds.value + addSession.riderId
            removeSession != null -> activeSessionRiderIds.value - removeSession.riderId
            else -> activeSessionRiderIds.value
        }
        activeSessionRiderIds.value = updatedSet

        val current = _uiState.value
        if (current is CoachDashboardUiState.Success) {
            _uiState.value = CoachDashboardUiState.Success(applyActiveStatuses(current.riders))
        }
    }

    private fun applyActiveStatuses(riders: List<RiderWithMetrics>): List<RiderWithMetrics> {
        val activeIds = activeSessionRiderIds.value
        return riders.map { rider ->
            if (activeIds.contains(rider.id)) {
                rider.copy(status = "active")
            } else {
                val fallbackStatus = if (rider.currentMetrics?.currentMetrics?.heartRate != null) "idle" else "offline"
                rider.copy(status = fallbackStatus)
            }
        }
    }
}
