package com.ridepulse.rider.bluetooth

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import com.ridepulse.rider.data.model.SensorData
import com.ridepulse.rider.data.model.SensorType
import no.nordicsemi.android.ble.BleManager

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
        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            val genericAccess = gatt.getService(BleUuids.GENERIC_ACCESS)
            deviceNameCharacteristic = genericAccess?.getCharacteristic(BleUuids.DEVICE_NAME)
            if (deviceNameCharacteristic != null) {
                deviceName = deviceNameCharacteristic?.getStringValue(0)
            }

            val hrService = gatt.getService(BleUuids.HEART_RATE_SERVICE)
            heartRateCharacteristic = hrService?.getCharacteristic(BleUuids.HEART_RATE_MEASUREMENT)
            if (heartRateCharacteristic != null) {
                sensorType = SensorType.HEART_RATE
            }

            val powerService = gatt.getService(BleUuids.CYCLING_POWER_SERVICE)
            powerCharacteristic = powerService?.getCharacteristic(BleUuids.CYCLING_POWER_MEASUREMENT)
            if (powerCharacteristic != null) {
                sensorType = SensorType.POWER_METER
            }

            val cscService = gatt.getService(BleUuids.CSC_SERVICE)
            cscCharacteristic = cscService?.getCharacteristic(BleUuids.CSC_MEASUREMENT)
            if (cscCharacteristic != null) {
                sensorType = SensorType.SPEED_CADENCE
            }

            return heartRateCharacteristic != null ||
                powerCharacteristic != null ||
                cscCharacteristic != null
        }

        override fun initialize() {
            heartRateCharacteristic?.let { characteristic ->
                setNotificationCallback(characteristic).with { _, data ->
                    data.value?.let(::parseHeartRate)
                }
                enableNotifications(characteristic).enqueue()
            }
            powerCharacteristic?.let { characteristic ->
                setNotificationCallback(characteristic).with { _, data ->
                    data.value?.let(::parsePower)
                }
                enableNotifications(characteristic).enqueue()
            }
            cscCharacteristic?.let { characteristic ->
                setNotificationCallback(characteristic).with { _, data ->
                    data.value?.let(::parseCsc)
                }
                enableNotifications(characteristic).enqueue()
            }

            requestMtu(247).enqueue()
        }

        override fun onServicesInvalidated() {
            heartRateCharacteristic = null
            powerCharacteristic = null
            cscCharacteristic = null
            deviceNameCharacteristic = null
        }
    }

    private fun parseHeartRate(data: ByteArray) {
        if (data.isEmpty()) return
        val is16Bit = (data[0].toInt() and 0x01) == 1
        val heartRate = when {
            is16Bit && data.size >= 3 -> ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
            data.size >= 2 -> data[1].toInt() and 0xFF
            else -> return
        }

        onDataReceived(
            SensorData(
                riderId = "",
                sessionId = "",
                timestamp = System.currentTimeMillis(),
                heartRate = heartRate
            )
        )
    }

    private fun parsePower(data: ByteArray) {
        if (data.size < 4) return
        val powerRaw = ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        val power = if (powerRaw > 32767) powerRaw - 65536 else powerRaw

        val flags = data[0].toInt() and 0xFF
        val cadence = if ((flags and 0x02) != 0 && data.size >= 5) data[4].toInt() and 0xFF else null

        onDataReceived(
            SensorData(
                riderId = "",
                sessionId = "",
                timestamp = System.currentTimeMillis(),
                power = power,
                cadence = cadence
            )
        )
    }

    private fun parseCsc(data: ByteArray) {
        if (data.isEmpty()) return

        val flags = data[0].toInt() and 0xFF
        var offset = 1

        var crankRevolutions: Long? = null

        if ((flags and 0x01) != 0 && data.size >= offset + 6) {
            offset += 6
        }

        if ((flags and 0x02) != 0 && data.size >= offset + 4) {
            crankRevolutions = ((data[offset + 1].toLong() and 0xFFL) shl 8) or
                (data[offset].toLong() and 0xFFL)
        }

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
