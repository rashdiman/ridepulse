package com.ridepulse.rider.coach.data.api

import android.content.Context
import com.ridepulse.rider.BuildConfig
import com.ridepulse.rider.coach.model.AuthTokens
import com.ridepulse.rider.coach.model.User
import com.ridepulse.rider.coach.model.UserRole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AuthCredentials(
    val email: String,
    val password: String
)

@Serializable
data class RegisterData(
    val email: String,
    val password: String,
    val name: String,
    val role: UserRole
)

@Serializable
data class AuthResponse(
    val user: User,
    val tokens: AuthTokens
)

@Singleton
class AuthApi @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json".toMediaType()

    suspend fun login(credentials: AuthCredentials): AuthResponse {
        return withContext(Dispatchers.IO) {
            val body = json.encodeToString(AuthCredentials.serializer(), credentials).toRequestBody(mediaType)
            val request = Request.Builder()
                .url("${BuildConfig.API_URL}/api/auth/login")
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("Login failed: ${response.code}")
            }
            val payload = response.body?.string().orEmpty()
            json.decodeFromString(AuthResponse.serializer(), payload)
        }
    }

    suspend fun register(data: RegisterData): AuthResponse {
        return withContext(Dispatchers.IO) {
            val body = json.encodeToString(RegisterData.serializer(), data).toRequestBody(mediaType)
            val request = Request.Builder()
                .url("${BuildConfig.API_URL}/api/auth/register")
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("Register failed: ${response.code}")
            }
            val payload = response.body?.string().orEmpty()
            json.decodeFromString(AuthResponse.serializer(), payload)
        }
    }

    suspend fun getCurrentUser(token: String): User {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${BuildConfig.API_URL}/api/auth/me")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("Fetch user failed: ${response.code}")
            }
            val payload = response.body?.string().orEmpty()
            json.decodeFromString(User.serializer(), payload)
        }
    }

    fun storeTokens(tokens: AuthTokens) {
        context.getSharedPreferences("coach_auth", Context.MODE_PRIVATE)
            .edit()
            .putString("accessToken", tokens.accessToken)
            .putString("refreshToken", tokens.refreshToken)
            .apply()
    }

    fun getStoredToken(): String? {
        return context.getSharedPreferences("coach_auth", Context.MODE_PRIVATE)
            .getString("accessToken", null)
    }

    fun clearTokens() {
        context.getSharedPreferences("coach_auth", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
