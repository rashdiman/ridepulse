package com.ridepulse.rider.coach.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridepulse.rider.coach.data.api.AuthApi
import com.ridepulse.rider.coach.data.api.AuthCredentials
import com.ridepulse.rider.coach.data.api.RegisterData
import com.ridepulse.rider.coach.model.User
import com.ridepulse.rider.coach.model.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

sealed class CoachAuthUiState {
    object Loading : CoachAuthUiState()
    object NotAuthenticated : CoachAuthUiState()
    data class Authenticated(val user: User) : CoachAuthUiState()
    data class Error(val message: String) : CoachAuthUiState()
}

@HiltViewModel
class CoachAuthViewModel @Inject constructor(
    private val authApi: AuthApi
) : ViewModel() {
    private companion object {
        const val AUTH_TIMEOUT_MS = 15000L
        const val DEV_COACH_EMAIL = "admin@ridepulse.com"
        const val DEV_COACH_PASSWORD = "admin123"
    }

    private val _uiState = MutableStateFlow<CoachAuthUiState>(CoachAuthUiState.Loading)
    val uiState: StateFlow<CoachAuthUiState> = _uiState.asStateFlow()

    init {
        checkAuth()
    }

    private fun checkAuth() {
        viewModelScope.launch {
            val token = authApi.getStoredToken()
            if (token.isNullOrBlank()) {
                _uiState.value = CoachAuthUiState.NotAuthenticated
                return@launch
            }
            runCatching { withTimeout(AUTH_TIMEOUT_MS) { authApi.getCurrentUser(token) } }
                .onSuccess { user ->
                    if (user.role == UserRole.COACH || user.role == UserRole.ADMIN) {
                        _uiState.value = CoachAuthUiState.Authenticated(user)
                    } else {
                        authApi.clearTokens()
                        _uiState.value = CoachAuthUiState.NotAuthenticated
                    }
                }
                .onFailure {
                    authApi.clearTokens()
                    _uiState.value = CoachAuthUiState.NotAuthenticated
                }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = CoachAuthUiState.Loading
            runCatching { withTimeout(AUTH_TIMEOUT_MS) { authApi.login(AuthCredentials(email, password)) } }
                .onSuccess { response ->
                    if (response.user.role != UserRole.COACH && response.user.role != UserRole.ADMIN) {
                        _uiState.value = CoachAuthUiState.Error("Account has no coach access")
                        return@onSuccess
                    }
                    authApi.storeTokens(response.tokens)
                    _uiState.value = CoachAuthUiState.Authenticated(response.user)
                }
                .onFailure { e ->
                    _uiState.value = CoachAuthUiState.Error(e.message ?: "Login failed")
                }
        }
    }

    fun register(email: String, password: String, name: String, role: UserRole) {
        viewModelScope.launch {
            _uiState.value = CoachAuthUiState.Loading
            runCatching { withTimeout(AUTH_TIMEOUT_MS) { authApi.register(RegisterData(email, password, name, role)) } }
                .onSuccess { response ->
                    authApi.storeTokens(response.tokens)
                    _uiState.value = CoachAuthUiState.Authenticated(response.user)
                }
                .onFailure { e ->
                    _uiState.value = CoachAuthUiState.Error(e.message ?: "Registration failed")
                }
        }
    }

    fun logout() {
        authApi.clearTokens()
        _uiState.value = CoachAuthUiState.NotAuthenticated
    }

    fun devLoginAsCoach() {
        val current = _uiState.value
        if (current is CoachAuthUiState.Authenticated) return

        viewModelScope.launch {
            _uiState.value = CoachAuthUiState.Loading
            runCatching {
                withTimeout(AUTH_TIMEOUT_MS) {
                    authApi.login(AuthCredentials(DEV_COACH_EMAIL, DEV_COACH_PASSWORD))
                }
            }
                .onSuccess { response ->
                    if (response.user.role != UserRole.COACH && response.user.role != UserRole.ADMIN) {
                        _uiState.value = CoachAuthUiState.Error("Demo account has no coach access")
                        return@onSuccess
                    }
                    authApi.storeTokens(response.tokens)
                    _uiState.value = CoachAuthUiState.Authenticated(response.user)
                }
                .onFailure { e ->
                    _uiState.value = CoachAuthUiState.Error(e.message ?: "Demo coach login failed")
                }
        }
    }
}
