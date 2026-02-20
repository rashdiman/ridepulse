package com.ridepulse.rider.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.ridepulse.rider.data.model.SensorData
import com.ridepulse.rider.data.model.SensorType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.data.Data
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UUID сервисов и характеристик для стандартных BLE датчиков
 */
object BleUuids {
    // Heart Rate Service
    val HEART_RATE_SERVICE = java.util.UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
    val HEART_RATE_MEASUREMENT = java.util.UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")
    
    // Cycling Power Service
    val CYCLING_POWER_SERVICE = java.util.UUID.fromString("00001818-0000-1000-8000-00805F9B34FB")
    val CYCLING_POWER_MEASUREMENT = java.util.UUID.fromString("00002A63-0000-1000-8000-00805F9B34FB")
    val CYCLING_POWER_FEATURE = java.util.UUID.fromString("00002A65-0000-1000-8000-00805F9B34FB")
    
    // Cycling Speed and Cadence Service
    val CSC_SERVICE = java.util.UUID.fromString("00001816-0000-1000-8000-00805F9B34FB")
    val CSC_MEASUREMENT = java.util.UUID.fromString("00002A5B-0000-1000-8000-00805F9B34FB")
    val CSC_FEATURE = java.util.UUID.fromString("00002A5C-0000-1000-8000-00805F9B34FB")
    
    // Generic Access
    val GENERIC_ACCESS = java.util.UUID.fromString("00001800-0000-1000-8000-00805F9B34FB")
    val DEVICE_NAME = java.util.UUID.fromString("00002A00-0000-1000-8000-00805F9B34FB")
    
    // Client Characteristic Configuration
    val CCC_DESCRIPTOR = java.util.UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
}

@Singleton
class RidePulseBleManager @Inject constructor(
    @ApplicationContext
    private val context: Context
) {
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning
    
    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices
    
    private val _connectedDevices = MutableStateFlow<Map<String, SensorConnection>>(emptyMap())
    val connectedDevices: StateFlow<Map<String, SensorConnection>> = _connectedDevices
    
    private val scanCallback = object : android.bluetooth.le.ScanCallback() {
        override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
            val device = result.device
            if (!_discoveredDevices.value.any { it.address == device.address }) {
                _discoveredDevices.value = _discoveredDevices.value + device
            }
        }
    }
    
    private val bluetoothLeScanner: android.bluetooth.le.BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    fun isBluetoothAvailable(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) &&
               bluetoothAdapter != null
    }
    
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    @SuppressLint("MissingPermission")
    fun startScan(sensorTypes: List<String> = emptyList()) {
        if (!isBluetoothAvailable() || !isBluetoothEnabled()) {
            Log.e(TAG, "Bluetooth недоступен")
            return
        }
        
        _discoveredDevices.value = emptyList()
        
        val scanFilters = mutableListOf<android.bluetooth.le.ScanFilter>()
        // Можно добавить фильтры по сервисам
        
        val scanSettings = android.bluetooth.le.ScanSettings.Builder()
            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        try {
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
            _isScanning.value = true
            Log.d(TAG, "Сканирование запущено")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка запуска сканирования", e)
        }
    }
    
    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
            _isScanning.value = false
            Log.d(TAG, "Сканирование остановлено")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка остановки сканирования", e)
        }
    }
    
    @SuppressLint("MissingPermission")
    fun connectDevice(device: BluetoothDevice, onDataReceived: (SensorData) -> Unit) {
        val manager = SensorBleManager(context, onDataReceived)
        manager.connect(device)
            .timeout(10000)
            .useAutoConnect(false)
            .retry(3, 100)
            .enqueue()
        
        val connection = SensorConnection(
            device = device,
            manager = manager,
            type = determineSensorType(device.name)
        )
        
        _connectedDevices.value = _connectedDevices.value + (device.address to connection)
        Log.d(TAG, "Подключение к ${device.name} (${device.address})")
    }
    
    @SuppressLint("MissingPermission")
    fun disconnectDevice(address: String) {
        _connectedDevices.value[address]?.let { connection ->
            connection.manager.disconnect().enqueue()
            _connectedDevices.value = _connectedDevices.value - address
            Log.d(TAG, "Отключение от $address")
        }
    }
    
    fun disconnectAll() {
        _connectedDevices.value.keys.toList().forEach { disconnectDevice(it) }
    }
    
    private fun determineSensorType(name: String?): SensorType {
        return when {
            name?.contains("HRM", ignoreCase = true) == true ||
            name?.contains("Heart", ignoreCase = true) == true -> SensorType.HEART_RATE
            name?.contains("Power", ignoreCase = true) == true -> SensorType.POWER_METER
            name?.contains("Cadence", ignoreCase = true) == true ||
            name?.contains("Speed", ignoreCase = true) == true -> SensorType.SPEED_CADENCE
            else -> SensorType.UNKNOWN
        }
    }
    
    companion object {
        private const val TAG = "RidePulseBleManager"
    }
}

data class SensorConnection(
    val device: BluetoothDevice,
    val manager: SensorBleManager,
    val type: SensorType
)
