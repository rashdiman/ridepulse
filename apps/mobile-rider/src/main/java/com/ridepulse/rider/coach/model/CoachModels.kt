package com.ridepulse.rider.coach.model

import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    RIDER,
    COACH,
    ADMIN
}

@Serializable
enum class AlertSeverity {
    CRITICAL,
    WARNING,
    INFO
}

@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String,
    val role: UserRole,
    val teamId: String? = null,
    val avatar: String? = null
)

@Serializable
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String? = null
)

@Serializable
data class SensorData(
    val heartRate: Int? = null,
    val power: Int? = null,
    val cadence: Int? = null,
    val speed: Float? = null
)

@Serializable
data class MetricHistoryPoint(
    val timestamp: Long,
    val heartRate: Int? = null,
    val power: Int? = null,
    val cadence: Int? = null,
    val speed: Float? = null
)

@Serializable
data class RiderMetrics(
    val riderId: String,
    val riderName: String,
    val sessionId: String,
    val currentMetrics: SensorData,
    val history: List<MetricHistoryPoint> = emptyList()
)

@Serializable
data class Alert(
    val id: String,
    val riderId: String,
    val type: String,
    val message: String,
    val severity: AlertSeverity,
    val acknowledged: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class RiderWithMetrics(
    val id: String,
    val name: String,
    val email: String,
    val teamId: String?,
    val avatar: String?,
    val status: String = "offline",
    val currentMetrics: RiderMetrics? = null
)
