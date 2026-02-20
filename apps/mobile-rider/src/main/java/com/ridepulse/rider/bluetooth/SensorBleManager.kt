package com.ridepulse.rider.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import com.ridepulse.rider.data.model.SensorData
import com.ridepulse.rider.data.model.SensorType
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.data.Data

class SensorBleManager(
    context: Context,
    private val onDataReceived: (SensorData) -> Unit
) : BleManager(context) {
    
    private var heartRateCharacteristic: BluetoothGattCharacteristic? = null
    private var powerCharacteristic: BluetoothGattCharacteristic? = null
    private var cscCharacteristic: BluetoothGattCharacteristic? = null
    private var deviceNameCharacteristic: BluetoothGattCharacteristic? = null
    
    private var deviceName: String? = null
    private var sensorType: SensorType = SensorType.UNKNOWN
    
    override fun getGattCallback(): BleManagerGattCallback = GattCallback()
    
    private inner class GattCallback : BleManagerGattCallback() {
        
        override fun isRequiredServiceSupported(gatt: BluetoothGattServiceWrapper): Boolean {
            // Проверяем доступные сервисы
            deviceNameCharacteristic = gatt.findCharacteristic(BleUuids.DEVICE_NAME)
            if (deviceNameCharacteristic != null) {
                deviceName = deviceNameCharacteristic?.getStringValue(0)
            }
            
            // Heart Rate Service
            val hrService = gatt.findService(BleUuids.HEART_RATE_SERVICE)
            if (hrService != null) {
                heartRateCharacteristic = hrService.findCharacteristic(BleUuids.HEART_RATE_MEASUREMENT)
                if (heartRateCharacteristic != null) {
                    sensorType = SensorType.HEART_RATE
                    Log.d(TAG, "Обнаружен Heart Rate сенсор")
                }
            }
            
            // Cycling Power Service
            val powerService = gatt.findService(BleUuids.CYCLING_POWER_SERVICE)
            if (powerService != null) {
                powerCharacteristic = powerService.findCharacteristic(BleUuids.CYCLING_POWER_MEASUREMENT)
                if (powerCharacteristic != null) {
                    sensorType = SensorType.POWER_METER
                    Log.d(TAG, "Обнаружен Power Meter")
                }
            }
            
            // Cycling Speed and Cadence Service
            val cscService = gatt.findService(BleUuids.CSC_SERVICE)
            if (cscService != null) {
                cscCharacteristic = cscService.findCharacteristic(BleUuids.CSC_MEASUREMENT)
                if (cscCharacteristic != null) {
                    sensorType = SensorType.SPEED_CADENCE
                    Log.d(TAG, "Обнаружен Speed/Cadence сенсор")
                }
            }
            
            return heartRateCharacteristic != null ||
                   powerCharacteristic != null ||
                   cscCharacteristic != null
        }
        
        override fun initialize() {
            // Включаем уведомления для всех доступных характеристик
            heartRateCharacteristic?.let { enableNotifications(it).enqueue() }
            powerCharacteristic?.let { enableNotifications(it).enqueue() }
            cscCharacteristic?.let { enableNotifications(it).enqueue() }
            
            requestMtu(512).enqueue()
        }
        
        override fun onServicesInvalidated() {
            heartRateCharacteristic = null
            powerCharacteristic = null
            cscCharacteristic = null
            deviceNameCharacteristic = null
        }
        
        override fun onCharacteristicNotified(
            gatt: BluetoothGattCharacteristic,
            data: Data
        ) {
            when (gatt.uuid) {
                BleUuids.HEART_RATE_MEASUREMENT -> parseHeartRate(data.value)
                BleUuids.CYCLING_POWER_MEASUREMENT -> parsePower(data.value)
                BleUuids.CSC_MEASUREMENT -> parseCsc(data.value)
            }
        }
    }
    
    /**
     * Парсинг данных Heart Rate Measurement
     * Формат: https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.characteristic.heart_rate_measurement.xml
     */
    private fun parseHeartRate(data: ByteArray) {
        if (data.isEmpty()) return
        
        val is16Bit = (data[0].toInt() and 0x01) == 1
        val heartRate = if (is16Bit && data.size >= 3) {
            ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        } else if (data.size >= 2) {
            data[1].toInt() and 0xFF
        } else {
            return
        }
        
        Log.d(TAG, "Heart Rate: $heartRate bpm")
        
        onDataReceived(
            SensorData(
                riderId = "", // Заполняется на уровне приложения
                sessionId = "", // Заполняется на уровне приложения
                timestamp = System.currentTimeMillis(),
                heartRate = heartRate
            )
        )
    }
    
    /**
     * Парсинг данных Cycling Power Measurement
     * Формат: https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.characteristic.cycling_power_measurement.xml
     */
    private fun parsePower(data: ByteArray) {
        if (data.size < 4) return
        
        // Flags
        val flags = data[0].toInt() and 0xFF
        
        // Instantaneous Power (watts) - всегда присутствует, offset 2
        val power = ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        // Power это signed 16-bit
        val signedPower = if (power > 32767) power - 65536 else power
        
        var cadence: Int? = null
        
        // Cadence present? (bit 2)
        val cadencePresent = (flags and 0x02) != 0
        if (cadencePresent && data.size >= 5) {
            cadence = data[4].toInt() and 0xFF
        }
        
        Log.d(TAG, "Power: $signedPower W, Cadence: $cadence rpm")
        
        onDataReceived(
            SensorData(
                riderId = "",
                sessionId = "",
                timestamp = System.currentTimeMillis(),
                power = signedPower,
                cadence = cadence
            )
        )
    }
    
    /**
     * Парсинг данных Cycling Speed and Cadence Measurement
     * Формат: https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.characteristic.csc_measurement.xml
     */
    private fun parseCsc(data: ByteArray) {
        if (data.isEmpty()) return
        
        val flags = data[0].toInt() and 0xFF
        var offset = 1
        
        var wheelRevolutions: Long? = null
        var wheelTime: Int? = null
        var crankRevolutions: Long? = null
        var crankTime: Int? = null
        
        // Wheel revolution data present? (bit 0)
        if ((flags and 0x01) != 0 && data.size >= offset + 7) {
            wheelRevolutions = ((data[offset + 3].toInt() and 0xFFL) shl 24) or
                             ((data[offset + 2].toInt() and 0xFFL) shl 16) or
                             ((data[offset + 1].toInt() and 0xFFL) shl 8) or
                             (data[offset].toInt() and 0xFFL)
            wheelTime = ((data[offset + 5].toInt() and 0xFF) shl 8) or
                       (data[offset + 4].toInt() and 0xFF)
            offset += 6
        }
        
        // Crank revolution data present? (bit 1)
        if ((flags and 0x02) != 0 && data.size >= offset + 5) {
            crankRevolutions = ((data[offset + 1].toInt() and 0xFFL) shl 8) or
                             (data[offset].toInt() and 0xFFL)
            crankTime = ((data[offset + 3].toInt() and 0xFF) shl 8) or
                       (data[offset + 2].toInt() and 0xFF)
        }
        
        // Расчёт скорости и каденса на основе разницы между измерениями
        // Для простоты возвращаем сырые данные - расчёт будет на уровне сервиса
        Log.d(TAG, "CSC: Wheel=$wheelRevolutions, Crank=$crankRevolutions")
        
        onDataReceived(
            SensorData(
                riderId = "",
                sessionId = "",
                timestamp = System.currentTimeMillis(),
                cadence = crankRevolutions?.toInt()
            )
        )
    }
    
    fun getDeviceName(): String? = deviceName
    
    fun getSensorType(): SensorType = sensorType
    
    companion object {
        private const val TAG = "SensorBleManager"
    }
}
