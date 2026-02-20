package com.ridepulse.rider.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SensorData(
    val riderId: String,
    val sessionId: String,
    val timestamp: Long,
    val heartRate: Int? = null,           // bpm
    val power: Int? = null,               // watts
    val cadence: Int? = null,             // rpm
    val speed: Float? = null,             // km/h
    val location: LocationData? = null
)

@Serializable
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracy: Float? = null
)

@Serializable
data class DeviceInfo(
    val id: String,
    val name: String?,
    val type: SensorType,
    val address: String
)

enum class SensorType {
    HEART_RATE,
    POWER_METER,
    SPEED_CADENCE,
    SPEED_ONLY,
    CADENCE_ONLY,
    UNKNOWN
}

@Serializable
data class SessionStart(
    val riderId: String,
    val startTime: Long,
    val deviceInfo: List<DeviceInfo>
)

@Serializable
data class SessionEnd(
    val riderId: String,
    val sessionId: String,
    val endTime: Long
)
