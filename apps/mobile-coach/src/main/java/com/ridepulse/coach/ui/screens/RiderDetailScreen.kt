package com.ridepulse.coach.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ridepulse.coach.ui.viewmodel.RiderDetailViewModel
import com.ridepulse.sharedtypes.Alert
import com.ridepulse.sharedtypes.RiderMetrics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiderDetailScreen(
    riderId: String,
    onBack: () -> Unit
) {
    // ViewModel должен быть передан через навигацию
    // Для упрощения используем состояние напрямую
    var metrics by remember { mutableStateOf<RiderMetrics?>(null) }
    var alerts by remember { mutableStateOf<List<Alert>>(emptyList) }
    
    // TODO: Получение данных через ViewModel
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Детали райдера") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        if (metrics != null) {
            RiderDetailContent(
                metrics = metrics!!,
                alerts = alerts,
                modifier = Modifier.padding(padding)
            )
        } else {
            LoadingContent()
        }
    }
}

@Composable
fun RiderDetailContent(
    metrics: RiderMetrics,
    alerts: List<Alert>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Информация о райдере
        RiderInfoCard(metrics)
        
        // Текущие метрики
        MetricsGrid(metrics.currentMetrics)
        
        // График (упрощённый)
        SimpleChart(metrics.history)
        
        // Алерты
        if (alerts.isNotEmpty()) {
            AlertsList(alerts)
        }
    }
}

@Composable
fun RiderInfoCard(metrics: RiderMetrics) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Аватар
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(30.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = metrics.riderName.first().uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Информация
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = metrics.riderName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ID: ${metrics.riderId}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Сессия: ${metrics.sessionId.take(8)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MetricsGrid(metrics: com.ridepulse.sharedtypes.SensorData) {
    Column {
        Text(
            text = "Текущие метрики",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "ПУЛЬС",
                value = metrics.heartRate?.toString() ?: "--",
                unit = "bpm",
                color = Color(0xFFEF4444),
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "МОЩНОСТЬ",
                value = metrics.power?.toString() ?: "--",
                unit = "W",
                color = Color(0xFFA855F7),
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "КАДЕНС",
                value = metrics.cadence?.toString() ?: "--",
                unit = "rpm",
                color = Color(0xFF3B82F6),
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "СКОРОСТЬ",
                value = metrics.speed?.toString() ?: "--",
                unit = "км/ч",
                color = Color(0xFF06B6D4),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.aspectRatio(1f),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = color.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun SimpleChart(history: List<com.ridepulse.sharedtypes.MetricHistoryPoint>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "История метрик",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (history.isEmpty()) {
                Text(
                    text = "Нет данных",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Упрощённый график - последние 20 точек
                val recentHistory = history.takeLast(20)
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    recentHistory.forEach { point ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${point.heartRate ?: "--"} bpm",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFEF4444)
                            )
                            Text(
                                text = "${point.power ?: "--"} W",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA855F7)
                            )
                            Text(
                                text = "${point.cadence ?: "--"} rpm",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF3B82F6)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlertsList(alerts: List<Alert>) {
    Column {
        Text(
            text = "Алерты (${alerts.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (alerts.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = "Нет алертов",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            alerts.forEach { alert ->
                AlertCard(alert)
            }
        }
    }
}

@Composable
fun AlertCard(alert: Alert) {
    val severityColor = when (alert.severity) {
        com.ridepulse.sharedtypes.AlertSeverity.CRITICAL -> Color(0xFFEF4444)
        com.ridepulse.sharedtypes.AlertSeverity.WARNING -> Color(0xFFF59E0B)
        com.ridepulse.sharedtypes.AlertSeverity.INFO -> Color(0xFF3B82F6)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = severityColor.copy(alpha = 0.1f)
        ),
        border = if (!alert.acknowledged) {
            androidx.compose.foundation.BorderStroke(
                1.dp,
                severityColor
            )
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.type.replace('_', ' '),
                    style = MaterialTheme.typography.labelSmall,
                    color = severityColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            if (alert.acknowledged) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Подтверждено",
                    tint = Color(0xFF4CAF50)
                )
            }
        }
    }
}
