package com.ridepulse.rider.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridepulse.rider.data.model.SensorData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    metrics: SensorData?,
    connectedSensorsCount: Int,
    connectionState: com.ridepulse.rider.network.DataSender.ConnectionState,
    onStopSession: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Активная сессия") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = when (connectionState) {
                        com.ridepulse.rider.network.DataSender.ConnectionState.CONNECTED -> 
                            Color(0xFF4CAF50)
                        com.ridepulse.rider.network.DataSender.ConnectionState.CONNECTING -> 
                            Color(0xFFFF9800)
                        com.ridepulse.rider.network.DataSender.ConnectionState.ERROR -> 
                            Color(0xFFF44336)
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Статус подключения
            ConnectionStatusCard(
                connectionState = connectionState,
                connectedSensors = connectedSensorsCount
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Метрики
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Пульс
                MetricCard(
                    title = "ПУЛЬС",
                    value = metrics?.heartRate?.toString() ?: "--",
                    unit = "bpm",
                    modifier = Modifier.weight(1f),
                    color = Color(0xFFE91E63)
                )
                
                // Мощность
                MetricCard(
                    title = "МОЩНОСТЬ",
                    value = metrics?.power?.toString() ?: "--",
                    unit = "W",
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF9C27B0)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Каденс
                MetricCard(
                    title = "КАДЕНС",
                    value = metrics?.cadence?.toString() ?: "--",
                    unit = "rpm",
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF2196F3)
                )
                
                // Скорость
                MetricCard(
                    title = "СКОРОСТЬ",
                    value = metrics?.speed?.toString() ?: "--",
                    unit = "км/ч",
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF00BCD4)
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Кнопка остановки
            Button(
                onClick = onStopSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF44336)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ЗАВЕРШИТЬ СЕССИЮ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ConnectionStatusCard(
    connectionState: com.ridepulse.rider.network.DataSender.ConnectionState,
    connectedSensors: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (connectionState) {
                com.ridepulse.rider.network.DataSender.ConnectionState.CONNECTED -> 
                    Color(0xFF4CAF50).copy(alpha = 0.1f)
                com.ridepulse.rider.network.DataSender.ConnectionState.CONNECTING -> 
                    Color(0xFFFF9800).copy(alpha = 0.1f)
                com.ridepulse.rider.network.DataSender.ConnectionState.ERROR -> 
                    Color(0xFFF44336).copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surfaceVariant
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
            Column {
                Text(
                    text = getConnectionStatusText(connectionState),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (connectionState) {
                        com.ridepulse.rider.network.DataSender.ConnectionState.CONNECTED -> 
                            Color(0xFF4CAF50)
                        com.ridepulse.rider.network.DataSender.ConnectionState.CONNECTING -> 
                            Color(0xFFFF9800)
                        com.ridepulse.rider.network.DataSender.ConnectionState.ERROR -> 
                            Color(0xFFF44336)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = "Подключено сенсоров: $connectedSensors",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    color: Color
) {
    Card(
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                color = color
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.titleMedium,
                color = color.copy(alpha = 0.7f)
            )
        }
    }
}

fun getConnectionStatusText(
    state: com.ridepulse.rider.network.DataSender.ConnectionState
): String {
    return when (state) {
        com.ridepulse.rider.network.DataSender.ConnectionState.CONNECTED -> "✓ Подключено к серверу"
        com.ridepulse.rider.network.DataSender.ConnectionState.CONNECTING -> "Подключение..."
        com.ridepulse.rider.network.DataSender.ConnectionState.ERROR -> "Ошибка подключения"
        com.ridepulse.rider.network.DataSender.ConnectionState.DISCONNECTED -> "Отключено"
    }
}
