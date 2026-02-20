package com.ridepulse.rider.coach.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ridepulse.rider.coach.data.websocket.WebSocketClient
import com.ridepulse.rider.coach.model.RiderWithMetrics
import com.ridepulse.rider.coach.ui.viewmodel.CoachDashboardUiState
import com.ridepulse.rider.coach.ui.viewmodel.CoachDashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachDashboardScreen(viewModel: CoachDashboardViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val alerts by viewModel.alerts.collectAsState()
    val unacknowledgedAlerts = alerts.filter { !it.acknowledged }

    var showOnlyActive by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coach Dashboard") },
                actions = {
                    Icon(
                        imageVector = when (connectionState) {
                            WebSocketClient.ConnectionState.CONNECTED -> Icons.Default.Wifi
                            WebSocketClient.ConnectionState.CONNECTING -> Icons.Default.Sync
                            else -> Icons.Default.WifiOff
                        },
                        contentDescription = null,
                        tint = when (connectionState) {
                            WebSocketClient.ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                            WebSocketClient.ConnectionState.CONNECTING -> Color(0xFFFF9800)
                            else -> Color(0xFFF44336)
                        }
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                    if (unacknowledgedAlerts.isNotEmpty()) {
                        BadgedBox(badge = { Badge { Text(unacknowledgedAlerts.size.toString()) } }) {
                            Icon(Icons.Default.Notifications, contentDescription = "Alerts")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when (uiState) {
            is CoachDashboardUiState.Loading -> CoachLoadingContent()
            is CoachDashboardUiState.Error -> CoachErrorContent((uiState as CoachDashboardUiState.Error).message)
            is CoachDashboardUiState.Success -> {
                val riders = (uiState as CoachDashboardUiState.Success).riders
                val filtered = if (showOnlyActive) riders.filter { it.status == "active" } else riders

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    FilterChip(
                        selected = showOnlyActive,
                        onClick = { showOnlyActive = !showOnlyActive },
                        label = { Text("Only active (${riders.count { it.status == "active" }})") },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    if (filtered.isEmpty()) {
                        CoachEmptyState(if (showOnlyActive) "No active riders" else "No riders")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filtered) { rider ->
                                CoachRiderCard(
                                    rider = rider,
                                    onClick = { viewModel.selectRider(rider.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoachRiderCard(rider: RiderWithMetrics, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (rider.status == "active") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(rider.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(rider.email, style = MaterialTheme.typography.bodyMedium)
                rider.currentMetrics?.let { metrics ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("HR: ${metrics.currentMetrics.heartRate ?: "--"} bpm")
                    Text("Power: ${metrics.currentMetrics.power ?: "--"} W")
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun CoachLoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text("Loading riders...")
        }
    }
}

@Composable
private fun CoachErrorContent(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun CoachEmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(56.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(message)
        }
    }
}
