package com.ridepulse.rider.coach.data.api

import com.ridepulse.rider.BuildConfig
import com.ridepulse.rider.coach.model.RiderWithMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class RiderDto(
    val id: String,
    val name: String,
    val email: String,
    val teamId: String? = null,
    val avatar: String? = null
)

@Serializable
private data class RidersResponse(
    val riders: List<RiderDto>
)

@Singleton
class RidersApi @Inject constructor(
    private val authApi: AuthApi
) {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getRiders(): List<RiderWithMetrics> {
        return withContext(Dispatchers.IO) {
            val token = authApi.getStoredToken()
                ?: throw IllegalStateException("No auth token found")
            val request = Request.Builder()
                .url("${BuildConfig.API_URL}/api/riders")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("Riders load failed: ${response.code}")
            }
            val payload = response.body?.string().orEmpty()
            val riders = json.decodeFromString(RidersResponse.serializer(), payload).riders
            riders.map {
                RiderWithMetrics(
                    id = it.id,
                    name = it.name,
                    email = it.email,
                    teamId = it.teamId,
                    avatar = it.avatar
                )
            }
        }
    }
}
