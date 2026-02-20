package com.ridepulse.coach.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridepulse.coach.data.api.RidersApi
import com.ridepulse.coach.data.websocket.WebSocketClient
import com.ridepulse.sharedtypes.RiderMetrics
import com.ridepulse.sharedtypes.Alert
import com.ridepulse.sharedtypes.User
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ridersApi: RidersApi,
    private val wsClient: WebSocketClient
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    
    private val _selectedRiderId = MutableStateFlow<String?>(null)
    val selectedRiderId: StateFlow<String?> = _selectedRiderId.asStateFlow()
    
    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()
    
    private val _connectionState = MutableStateFlow<WebSocketClient.ConnectionState>(
        WebSocketClient.ConnectionState.DISCONNECTED
    )
    val connectionState: StateFlow<WebSocketClient.ConnectionState> = _connectionState.asStateFlow()
    
    init {
        loadRiders()
        connectWebSocket()
    }
    
    private fun loadRiders() {
        viewModelScope.launch {
            try {
                val riders = ridersApi.getRiders()
                _uiState.value = DashboardUiState.Success(riders)
            } catch (e: Exception) {
                _uiState.value = DashboardUiState.Error(e.message ?: "Ошибка загрузки")
            }
        }
    }
    
    private fun connectWebSocket() {
        viewModelScope.launch {
            wsClient.connect()
            
            wsClient.connectionState.collect { state ->
                _connectionState.value = state
            }
            
            wsClient.onRiderMetrics { metrics ->
                // Обновляем метрики в списке райдеров
                val currentState = _uiState.value
                if (currentState is DashboardUiState.Success) {
                    val updatedRiders = currentState.riders.map { rider ->
                        if (rider.id == metrics.riderId) {
                            rider.copy(
                                currentMetrics = metrics,
                                status = if (metrics.currentMetrics.heartRate != null) 
                                    "active" else "idle"
                            )
                        } else rider
                    }
                    _uiState.value = DashboardUiState.Success(updatedRiders)
                }
            }
            
            wsClient.onAlert { alert ->
                _alerts.value = listOf(alert) + _alerts.value
            }
        }
    }
    
    fun selectRider(riderId: String) {
        _selectedRiderId.value = riderId
    }
    
    fun clearSelectedRider() {
        _selectedRiderId.value = null
    }
    
    fun acknowledgeAlert(alertId: String) {
        viewModelScope.launch {
            try {
                wsClient.acknowledgeAlert(alertId)
                _alerts.value = _alerts.value.map { alert ->
                    if (alert.id == alertId) {
                        alert.copy(acknowledged = true)
                    } else alert
                }
            } catch (e: Exception) {
                // Обработка ошибки
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        wsClient.disconnect()
    }
}

sealed class DashboardUiState {
    object Loading : DashboardUiState()
    data class Success(val riders: List<RiderWithMetrics>) : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}

data class RiderWithMetrics(
    val id: String,
    val name: String,
    val email: String,
    val teamId: String?,
    val avatar: String?,
    val status: String = "offline",
    val currentMetrics: RiderMetrics? = null
)
