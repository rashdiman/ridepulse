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
            runCatching { authApi.getCurrentUser(token) }
                .onSuccess { user -> _uiState.value = CoachAuthUiState.Authenticated(user) }
                .onFailure { _uiState.value = CoachAuthUiState.NotAuthenticated }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = CoachAuthUiState.Loading
            runCatching { authApi.login(AuthCredentials(email, password)) }
                .onSuccess { response ->
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
            runCatching { authApi.register(RegisterData(email, password, name, role)) }
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
}
