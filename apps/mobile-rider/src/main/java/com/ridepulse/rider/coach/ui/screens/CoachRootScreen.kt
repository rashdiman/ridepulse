package com.ridepulse.rider.coach.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ridepulse.rider.coach.ui.viewmodel.CoachAuthUiState
import com.ridepulse.rider.coach.ui.viewmodel.CoachAuthViewModel
import com.ridepulse.rider.coach.ui.viewmodel.CoachDashboardUiState
import com.ridepulse.rider.coach.ui.viewmodel.CoachDashboardViewModel

@Composable
fun CoachRootScreen(
    autoAuth: Boolean = false,
    authViewModel: CoachAuthViewModel = hiltViewModel()
) {
    val authState by authViewModel.uiState.collectAsState()
    val authError = (authState as? CoachAuthUiState.Error)?.message
    val isAuthLoading = authState is CoachAuthUiState.Loading

    LaunchedEffect(autoAuth) {
        if (autoAuth) {
            authViewModel.devLoginAsCoach()
        }
    }

    when (authState) {
        is CoachAuthUiState.NotAuthenticated -> {
            CoachLoginScreen(
                isLoading = isAuthLoading,
                errorMessage = authError,
                onLogin = { email, password -> authViewModel.login(email, password) },
                onRegister = { email, password, name, role ->
                    authViewModel.register(email, password, name, role)
                }
            )
        }

        is CoachAuthUiState.Error -> {
            CoachLoginScreen(
                isLoading = false,
                errorMessage = (authState as CoachAuthUiState.Error).message,
                onLogin = { email, password -> authViewModel.login(email, password) },
                onRegister = { email, password, name, role ->
                    authViewModel.register(email, password, name, role)
                }
            )
        }

        is CoachAuthUiState.Authenticated -> {
            val dashboardViewModel: CoachDashboardViewModel = hiltViewModel()
            val dashboardState by dashboardViewModel.uiState.collectAsState()
            val selectedRiderId by dashboardViewModel.selectedRiderId.collectAsState()
            LaunchedEffect(Unit) {
                dashboardViewModel.ensureStarted()
            }

            var showAndroidDashboard = remember { mutableStateOf(false) }

            if (showAndroidDashboard.value) {
                com.ridepulse.rider.ui.screens.DashboardScreen()
                return
            }

            if (selectedRiderId != null && dashboardState is CoachDashboardUiState.Success) {
                val rider = (dashboardState as CoachDashboardUiState.Success)
                    .riders
                    .firstOrNull { it.id == selectedRiderId }
                if (rider != null) {
                    CoachRiderDetailScreen(
                        rider = rider,
                        onBack = { dashboardViewModel.clearSelectedRider() }
                    )
                } else {
                    dashboardViewModel.clearSelectedRider()
                    CoachDashboardScreen(dashboardViewModel)
                }
            } else {
                CoachDashboardScreen(dashboardViewModel)
            }
        }

        is CoachAuthUiState.Loading -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Checking auth...")
            }
        }
    }
}
