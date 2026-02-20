package com.ridepulse.coach

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.ridepulse.coach.ui.screens.LoginScreen
import com.ridepulse.coach.ui.screens.DashboardScreen
import com.ridepulse.coach.ui.screens.RiderDetailScreen
import com.ridepulse.coach.ui.theme.RidePulseCoachTheme
import com.ridepulse.coach.ui.viewmodel.AuthViewModel
import com.ridepulse.coach.ui.viewmodel.DashboardViewModel
import com.ridepulse.coach.ui.viewmodel.RiderDetailViewModel
import com.ridepulse.sharedtypes.UserRole
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private val authViewModel: AuthViewModel by viewModels()
    private val dashboardViewModel: DashboardViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            RidePulseCoachTheme {
                Surface(
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        authViewModel = authViewModel,
                        dashboardViewModel = dashboardViewModel
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    authViewModel: AuthViewModel,
    dashboardViewModel: DashboardViewModel
) {
    val uiState by authViewModel.uiState.collectAsState()
    val selectedRiderId by dashboardViewModel.selectedRiderId.collectAsState()
    
    // Разрешения
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.POST_NOTIFICATIONS
        )
    )
    
    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }
    
    when (uiState) {
        is AuthUiState.Loading -> {
            LoadingScreen()
        }
        is AuthUiState.Authenticated -> {
            if (selectedRiderId != null) {
                RiderDetailScreen(
                    riderId = selectedRiderId!!,
                    onBack = { dashboardViewModel.clearSelectedRider() }
                )
            } else {
                DashboardScreen(
                    viewModel = dashboardViewModel
                )
            }
        }
        is AuthUiState.NotAuthenticated -> {
            LoginScreen(
                onLogin = { email, password ->
                    authViewModel.login(email, password)
                },
                onRegister = { email, password, name, role ->
                    authViewModel.register(email, password, name, role)
                }
            )
        }
        is AuthUiState.Error -> {
            ErrorScreen(
                message = (uiState as AuthUiState.Error).message,
                onRetry = { authViewModel.logout() }
            )
        }
    }
}

sealed class AuthUiState {
    object Loading : AuthUiState()
    object NotAuthenticated : AuthUiState()
    data class Authenticated(val user: com.ridepulse.sharedtypes.User) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

@Composable
fun LoadingScreen() {
    androidx.compose.material3.Scaffold { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = androidx.compose.foundation.layout.Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = androidx.compose.foundation.layout.Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator()
            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.foundation.layout.height(16.dp))
            androidx.compose.material3.Text("Загрузка...")
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    androidx.compose.material3.Scaffold { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = androidx.compose.foundation.layout.Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = androidx.compose.foundation.layout.Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            androidx.compose.material3.Text(
                text = message,
                style = MaterialTheme.typography.titleLarge
            )
            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.foundation.layout.height(16.dp))
            androidx.compose.material3.Button(onClick = onRetry) {
                androidx.compose.material3.Text("Повторить")
            }
        }
    }
}
