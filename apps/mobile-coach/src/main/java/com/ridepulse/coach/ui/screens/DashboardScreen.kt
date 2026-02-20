package com.ridepulse.coach.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ridepulse.coach.ui.viewmodel.DashboardUiState
import com.ridepulse.coach.ui.viewmodel.DashboardViewModel
import com.ridepulse.coach.ui.viewmodel.RiderWithMetrics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val alerts by viewModel.alerts.collectAsState()
    val unacknowledgedAlerts = alerts.filter { !it.acknowledged }
    
    var showOnlyActive by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("Мои райдеры")
                },
                actions = {
                    // Статус подключения
                    Icon(
                        imageVector = when (connectionState) {
                            com.ridepulse.coach.data.websocket.WebSocketClient.ConnectionState.CONNECTED -> 
                                Icons.Default.Wifi
                            com.ridepulse.coach.data.websocket.WebSocketClient.ConnectionState.CONNECTING -> 
                                Icons.Default.Sync
                            else -> Icons.Default.WifiOff
                        },
                        contentDescription = null,
                        tint = when (connectionState) {
                            com.ridepulse.coach.data.websocket.WebSocketClient.ConnectionState.CONNECTED -> 
                                Color(0xFF4CAF50)
                            com.ridepulse.coach.data.websocket.WebSocketClient.ConnectionState.CONNECTING -> 
                                Color(0xFFFF9800)
                            else -> Color(0xFFF44336)
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Алерты
                    if (unacknowledgedAlerts.isNotEmpty()) {
                        BadgedBox(badge = {
                            Badge {
                                Text(unacknowledgedAlerts.size.toString())
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Алерты"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        when (uiState) {
            is DashboardUiState.Loading -> {
                LoadingContent()
            }
            is DashboardUiState.Success -> {
                val riders = (uiState as DashboardUiState.Success).riders
                val filteredRiders = if (showOnlyActive) {
                    riders.filter { it.status == "active" }
                } else {
                    riders
                }
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // Фильтры
                    FilterRow(
                        showOnlyActive = showOnlyActive,
                        onFilterChange = { showOnlyActive = it },
                        activeCount = riders.count { it.status == "active" }
                    )
                    
                    // Список райдеров
                    if (filteredRiders.isEmpty()) {
                        EmptyState(
                            message = if (showOnlyActive) {
                                "Нет активных райдеров"
                            } else {
                                "Нет райдеров"
                            }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredRiders) { rider ->
                                RiderCard(
                                    rider = rider,
                                    onClick = { viewModel.selectRider(rider.id) }
                                )
                            }
                        }
                    }
                }
            }
            is DashboardUiState.Error -> {
                ErrorContent(
                    message = (uiState as DashboardUiState.Error).message,
                    onRetry = { /* TODO */ }
                )
            }
        }
    }
}

@Composable
fun FilterRow(
    showOnlyActive: Boolean,
    onFilterChange: (Boolean) -> Unit,
    activeCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = showOnlyActive,
            onClick = { onFilterChange(!showOnlyActive) },
            label = { 
                Text("Только активные ($activeCount)") 
            },
            leadingIcon = if (showOnlyActive) {
                {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else null
        )
    }
}

@Composable
fun RiderCard(
    rider: RiderWithMetrics,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (rider.status == "active") {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Информация о райдере
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = rider.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Статус
                    Surface(
                        color = if (rider.status == "active") {
                            Color(0xFF4CAF50)
                        } else {
                            Color(0xFF9E9E9E)
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = if (rider.status == "active") "Активен" else "Оффлайн",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color.White
                        )
                    }
                }
                
                Text(
                    text = rider.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Метрики
                rider.currentMetrics?.let { metrics ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        MetricBadge(
                            label = "Пульс",
                            value = metrics.currentMetrics.heartRate?.toString() ?: "--",
                            unit = "bpm",
                            color = Color(0xFFEF4444)
                        )
                        MetricBadge(
                            label = "Мощность",
                            value = metrics.currentMetrics.power?.toString() ?: "--",
                            unit = "W",
                            color = Color(0xFFA855F7)
                        )
                        MetricBadge(
                            label = "Каденс",
                            value = metrics.currentMetrics.cadence?.toString() ?: "--",
                            unit = "rpm",
                            color = Color(0xFF3B82F6)
                        )
                    }
                }
            }
            
            // Стрелка
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MetricBadge(
    label: String,
    value: String,
    unit: String,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
            Text(
                text = "$value $unit",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Загрузка...")
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.People,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ErrorContent(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Повторить")
            }
        }
    }
}
