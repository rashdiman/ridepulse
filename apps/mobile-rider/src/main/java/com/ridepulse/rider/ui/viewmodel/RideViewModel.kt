package com.ridepulse.rider.ui.viewmodel

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridepulse.rider.bluetooth.RidePulseBleManager
import com.ridepulse.rider.data.model.DeviceInfo
import com.ridepulse.rider.data.model.SensorData
import com.ridepulse.rider.network.DataSender
import com.ridepulse.rider.service.SensorMonitoringService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RideViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bleManager: RidePulseBleManager,
    private val dataSender: DataSender
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<RideUiState>(RideUiState.Idle)
    val uiState: StateFlow<RideUiState> = _uiState.asStateFlow()
    
    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()
    
    private val _connectedSensors = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val connectedSensors: StateFlow<List<DeviceInfo>> = _connectedSensors.asStateFlow()
    
    private val _currentMetrics = MutableStateFlow<SensorData?>(null)
    val currentMetrics: StateFlow<SensorData?> = _currentMetrics.asStateFlow()
    
    private val _connectionState = MutableStateFlow(dataSender.connectionState.value)
    val connectionState: StateFlow<DataSender.ConnectionState> = _connectionState.asStateFlow()
    
    private var riderId: String = ""
    
    init {
        viewModelScope.launch {
            // Подписываемся на состояние WebSocket
            dataSender.connectionState.collect { state ->
                _connectionState.value = state
            }
        }
        
        // Проверяем доступность Bluetooth
        if (!bleManager.isBluetoothAvailable()) {
            _uiState.value = RideUiState.Error("Bluetooth недоступен")
        } else if (!bleManager.isBluetoothEnabled()) {
            _uiState.value = RideUiState.BluetoothDisabled
        }
    }
    
    fun setRiderId(id: String) {
        riderId = id
    }
    
    fun startScanning() {
        viewModelScope.launch {
            _uiState.value = RideUiState.Scanning
            
            bleManager.startScan()
            
            // Подписываемся на обнаруженные устройства
            bleManager.discoveredDevices.collect { devices ->
                _discoveredDevices.value = devices
                if (devices.isNotEmpty()) {
                    _uiState.value = RideUiState.DevicesFound(devices)
                }
            }
        }
    }
    
    fun stopScanning() {
        bleManager.stopScan()
        _uiState.value = RideUiState.Idle
    }
    
    fun connectDevice(device: BluetoothDevice) {
        viewModelScope.launch {
            _uiState.value = RideUiState.Connecting(device.name ?: "Unknown")
            
            bleManager.connectDevice(device) { sensorData ->
                _currentMetrics.value = sensorData
            }
            
            // Подписываемся на подключённые устройства
            bleManager.connectedDevices.collect { connections ->
                _connectedSensors.value = connections.values.map { conn ->
                    DeviceInfo(
                        id = conn.device.address,
                        name = conn.device.name,
                        type = conn.type,
                        address = conn.device.address
                    )
                }
                
                if (connections.isNotEmpty()) {
                    _uiState.value = RideUiState.SessionActive(_connectedSensors.value)
                }
            }
        }
    }
    
    fun disconnectDevice(address: String) {
        bleManager.disconnectDevice(address)
    }
    
    fun startSession(wsUrl: String, apiUrl: String) {
        if (riderId.isEmpty()) {
            _uiState.value = RideUiState.Error("Rider ID не задан")
            return
        }
        
        val intent = Intent(context, SensorMonitoringService::class.java).apply {
            action = SensorMonitoringService.ACTION_START_SESSION
            putExtra(SensorMonitoringService.EXTRA_RIDER_ID, riderId)
            putExtra(SensorMonitoringService.EXTRA_WS_URL, wsUrl)
            putExtra(SensorMonitoringService.EXTRA_API_URL, apiUrl)
        }
        
        context.startService(intent)
        _uiState.value = RideUiState.SessionActive(_connectedSensors.value)
    }
    
    fun stopSession() {
        val intent = Intent(context, SensorMonitoringService::class.java).apply {
            action = SensorMonitoringService.ACTION_STOP_SESSION
        }
        context.startService(intent)
        _uiState.value = RideUiState.Idle
    }
    
    fun disconnectAll() {
        bleManager.disconnectAll()
        _connectedSensors.value = emptyList()
        _currentMetrics.value = null
        _uiState.value = RideUiState.Idle
    }
    
    override fun onCleared() {
        super.onCleared()
        bleManager.stopScan()
    }
}

sealed class RideUiState {
    object Idle : RideUiState()
    object Scanning : RideUiState()
    data class DevicesFound(val devices: List<BluetoothDevice>) : RideUiState()
    data class Connecting(val deviceName: String) : RideUiState()
    data class SessionActive(val connectedSensors: List<DeviceInfo>) : RideUiState()
    object BluetoothDisabled : RideUiState()
    data class Error(val message: String) : RideUiState()
}
ss Error(val message: String) : RideUiState()
}
