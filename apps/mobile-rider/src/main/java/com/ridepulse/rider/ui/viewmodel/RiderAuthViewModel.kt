package com.ridepulse.rider.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridepulse.rider.coach.data.api.AuthApi
import com.ridepulse.rider.coach.data.api.AuthCredentials
import com.ridepulse.rider.coach.data.api.RegisterData
import com.ridepulse.rider.coach.model.User
import com.ridepulse.rider.coach.model.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

sealed class RiderAuthUiState {
    object Loading : RiderAuthUiState()
    object NotAuthenticated : RiderAuthUiState()
    data class Authenticated(val user: User) : RiderAuthUiState()
    data class Error(val message: String) : RiderAuthUiState()
}

@HiltViewModel
class RiderAuthViewModel @Inject constructor(
    private val authApi: AuthApi
) : ViewModel() {
    private companion object {
        const val AUTH_TIMEOUT_MS = 15000L
        const val DEV_RIDER_EMAIL = "rider.demo@ridepulse.local"
        const val DEV_RIDER_PASSWORD = "rider123"
        const val DEV_RIDER_NAME = "Demo Rider"
    }

    private val _uiState = MutableStateFlow<RiderAuthUiState>(RiderAuthUiState.Loading)
    val uiState: StateFlow<RiderAuthUiState> = _uiState.asStateFlow()

    init {
        checkAuth()
    }

    private fun checkAuth() {
        viewModelScope.launch {
            val token = authApi.getStoredToken()
            if (token.isNullOrBlank()) {
                _uiState.value = RiderAuthUiState.NotAuthenticated
                return@launch
            }
            runCatching { withTimeout(AUTH_TIMEOUT_MS) { authApi.getCurrentUser(token) } }
                .onSuccess { user ->
                    if (user.role == UserRole.RIDER) {
                        _uiState.value = RiderAuthUiState.Authenticated(user)
                    } else {
                        authApi.clearTokens()
                        _uiState.value = RiderAuthUiState.NotAuthenticated
                    }
                }
                .onFailure {
                    authApi.clearTokens()
                    _uiState.value = RiderAuthUiState.NotAuthenticated
                }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = RiderAuthUiState.Loading
            runCatching { withTimeout(AUTH_TIMEOUT_MS) { authApi.login(AuthCredentials(email, password)) } }
                .onSuccess { response ->
                    if (response.user.role != UserRole.RIDER) {
                        _uiState.value = RiderAuthUiState.Error("Account is not a rider")
                        return@onSuccess
                    }
                    authApi.storeTokens(response.tokens)
                    _uiState.value = RiderAuthUiState.Authenticated(response.user)
                }
                .onFailure { e ->
                    _uiState.value = RiderAuthUiState.Error(e.message ?: "Login failed")
                }
        }
    }

    fun register(email: String, password: String, name: String) {
        viewModelScope.launch {
            _uiState.value = RiderAuthUiState.Loading
            runCatching {
                withTimeout(AUTH_TIMEOUT_MS) {
                    authApi.register(
                        RegisterData(
                            email = email,
                            password = password,
                            name = name,
                            role = UserRole.RIDER
                        )
                    )
                }
            }
                .onSuccess { response ->
                    authApi.storeTokens(response.tokens)
                    _uiState.value = RiderAuthUiState.Authenticated(response.user)
                }
                .onFailure { e ->
                    _uiState.value = RiderAuthUiState.Error(e.message ?: "Registration failed")
                }
        }
    }

    fun logout() {
        authApi.clearTokens()
        _uiState.value = RiderAuthUiState.NotAuthenticated
    }

    fun devLoginAsRider() {
        val current = _uiState.value
        if (current is RiderAuthUiState.Authenticated) return

        viewModelScope.launch {
            _uiState.value = RiderAuthUiState.Loading

            val loginResult = runCatching {
                withTimeout(AUTH_TIMEOUT_MS) {
                    authApi.login(AuthCredentials(DEV_RIDER_EMAIL, DEV_RIDER_PASSWORD))
                }
            }

            if (loginResult.isSuccess) {
                val response = loginResult.getOrThrow()
                if (response.user.role == UserRole.RIDER) {
                    authApi.storeTokens(response.tokens)
                    _uiState.value = RiderAuthUiState.Authenticated(response.user)
                    return@launch
                }
                _uiState.value = RiderAuthUiState.Error("Demo account is not a rider")
                return@launch
            }

            runCatching {
                withTimeout(AUTH_TIMEOUT_MS) {
                    authApi.register(
                        RegisterData(
                            email = DEV_RIDER_EMAIL,
                            password = DEV_RIDER_PASSWORD,
                            name = DEV_RIDER_NAME,
                            role = UserRole.RIDER
                        )
                    )
                }
            }
                .onSuccess { response ->
                    authApi.storeTokens(response.tokens)
                    _uiState.value = RiderAuthUiState.Authenticated(response.user)
                }
                .onFailure { e ->
                    _uiState.value = RiderAuthUiState.Error(e.message ?: "Demo rider login failed")
                }
        }
    }
}
