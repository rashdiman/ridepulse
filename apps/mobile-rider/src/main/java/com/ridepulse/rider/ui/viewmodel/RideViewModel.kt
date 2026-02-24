package com.ridepulse.rider.ui.viewmodel

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RideViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bleManager: RidePulseBleManager,
    private val dataSender: DataSender
) : ViewModel() {
    private val devicePrefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val rememberedDevices = mutableSetOf<String>()
    private var autoReconnectActive = false
    private var autoReconnectTimeoutJob: Job? = null

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
        rememberedDevices += devicePrefs.getStringSet(KEY_REMEMBERED_DEVICE_ADDRESSES, emptySet()).orEmpty()

        viewModelScope.launch {
            dataSender.connectionState.collect { state ->
                _connectionState.value = state
            }
        }

        viewModelScope.launch {
            bleManager.discoveredDevices.collect { devices ->
                _discoveredDevices.value = devices
                if (_uiState.value is RideUiState.Scanning && devices.isNotEmpty()) {
                    _uiState.value = RideUiState.DevicesFound(devices)
                }
                tryAutoConnectRemembered(devices)
            }
        }

        viewModelScope.launch {
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
                    rememberedDevices += connections.keys
                    persistRememberedDevices()
                }

                if (_connectedSensors.value.isNotEmpty() && _uiState.value !is RideUiState.SessionActive) {
                    _uiState.value = RideUiState.SessionActive(_connectedSensors.value)
                }

                val stillMissing = rememberedDevices.any { it !in connections.keys }
                if (autoReconnectActive && !stillMissing) {
                    finishAutoReconnect()
                }
            }
        }

        if (!bleManager.isBluetoothAvailable()) {
            _uiState.value = RideUiState.Error("Bluetooth unavailable")
        } else if (!bleManager.isBluetoothEnabled()) {
            _uiState.value = RideUiState.BluetoothDisabled
        }
    }

    fun setRiderId(id: String) {
        riderId = id
        startAutoReconnectIfNeeded()
    }

    fun clearRiderId() {
        riderId = ""
    }

    fun startScanning() {
        _uiState.value = RideUiState.Scanning
        bleManager.startScan()
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
            rememberedDevices += device.address
            persistRememberedDevices()
        }
    }

    fun disconnectDevice(address: String) {
        bleManager.disconnectDevice(address)
    }

    fun startSession() {
        if (riderId.isEmpty()) {
            _uiState.value = RideUiState.Error("Rider ID is not set")
            return
        }

        val intent = Intent(context, SensorMonitoringService::class.java).apply {
            action = SensorMonitoringService.ACTION_START_SESSION
            putExtra(SensorMonitoringService.EXTRA_RIDER_ID, riderId)
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
        finishAutoReconnect()
        bleManager.stopScan()
        bleManager.disconnectAll()
        _discoveredDevices.value = emptyList()
        _connectedSensors.value = emptyList()
        _currentMetrics.value = null
        _uiState.value = RideUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        finishAutoReconnect()
        bleManager.stopScan()
    }

    private fun startAutoReconnectIfNeeded() {
        if (riderId.isBlank() || rememberedDevices.isEmpty()) return
        if (!bleManager.isBluetoothEnabled()) return
        if (autoReconnectActive) return

        autoReconnectActive = true
        _uiState.value = RideUiState.Scanning
        bleManager.startScan()

        autoReconnectTimeoutJob?.cancel()
        autoReconnectTimeoutJob = viewModelScope.launch {
            delay(AUTO_RECONNECT_SCAN_TIMEOUT_MS)
            if (autoReconnectActive) {
                finishAutoReconnect()
            }
        }
    }

    private fun tryAutoConnectRemembered(discovered: List<BluetoothDevice>) {
        if (!autoReconnectActive) return

        val connectedAddresses = bleManager.connectedDevices.value.keys
        rememberedDevices
            .filter { it !in connectedAddresses }
            .forEach { address ->
                val device = discovered.firstOrNull { it.address == address } ?: return@forEach
                bleManager.connectDevice(device) { sensorData ->
                    _currentMetrics.value = sensorData
                }
            }
    }

    private fun finishAutoReconnect() {
        autoReconnectTimeoutJob?.cancel()
        autoReconnectTimeoutJob = null
        if (autoReconnectActive) {
            bleManager.stopScan()
        }
        autoReconnectActive = false
    }

    private fun persistRememberedDevices() {
        devicePrefs.edit().putStringSet(KEY_REMEMBERED_DEVICE_ADDRESSES, rememberedDevices).apply()
    }

    private companion object {
        private const val PREFS_NAME = "ridepulse_devices"
        private const val KEY_REMEMBERED_DEVICE_ADDRESSES = "remembered_device_addresses"
        private const val AUTO_RECONNECT_SCAN_TIMEOUT_MS = 15000L
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
