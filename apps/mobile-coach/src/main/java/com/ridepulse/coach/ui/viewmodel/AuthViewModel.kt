package com.ridepulse.coach.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridepulse.coach.data.api.AuthApi
import com.ridepulse.coach.data.api.AuthCredentials
import com.ridepulse.coach.data.api.RegisterData
import com.ridepulse.sharedtypes.User
import com.ridepulse.sharedtypes.AuthTokens
import com.ridepulse.sharedtypes.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authApi: AuthApi
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    
    init {
        checkAuth()
    }
    
    private fun checkAuth() {
        viewModelScope.launch {
            val token = authApi.getStoredToken()
            if (token != null) {
                try {
                    val user = authApi.getCurrentUser(token)
                    _uiState.value = AuthUiState.Authenticated(user)
                } catch (e: Exception) {
                    _uiState.value = AuthUiState.NotAuthenticated
                }
            } else {
                _uiState.value = AuthUiState.NotAuthenticated
            }
        }
    }
    
    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val response = authApi.login(AuthCredentials(email, password))
                authApi.storeTokens(response.tokens)
                _uiState.value = AuthUiState.Authenticated(response.user)
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "Ошибка входа")
            }
        }
    }
    
    fun register(email: String, password: String, name: String, role: UserRole) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val response = authApi.register(
                    RegisterData(email, password, name, role)
                )
                authApi.storeTokens(response.tokens)
                _uiState.value = AuthUiState.Authenticated(response.user)
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "Ошибка регистрации")
            }
        }
    }
    
    fun logout() {
        authApi.clearTokens()
        _uiState.value = AuthUiState.NotAuthenticated
    }
}
