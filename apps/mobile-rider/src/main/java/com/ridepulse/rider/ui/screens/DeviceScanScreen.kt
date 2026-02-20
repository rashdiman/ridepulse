package com.ridepulse.rider.ui.screens

import android.Manifest
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.ridepulse.rider.bluetooth.SensorType
import com.ridepulse.rider.data.model.DeviceInfo

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun DeviceScanScreen(
    isScanning: Boolean,
    discoveredDevices: List<BluetoothDevice>,
    connectedSensors: List<DeviceInfo>,
    onScanClick: () -> Unit,
    onStopScanClick: () -> Unit,
    onDeviceClick: (BluetoothDevice) -> Unit,
    onDisconnectClick: (String) -> Unit
) {
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    )
    
    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Поиск сенсоров") },
                actions = {
                    IconButton(onClick = { if (isScanning) onStopScanClick() else onScanClick() }) {
                        Icon(
                            if (isScanning) Icons.Default.Refresh else Icons.Default.BluetoothSearching,
                            contentDescription = if (isScanning) "Остановить" else "Искать"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Секция подключённых сенсоров
            if (connectedSensors.isNotEmpty()) {
                Text(
                    text = "Подключено (${connectedSensors.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
                
                connectedSensors.forEach { device ->
                    ConnectedSensorCard(
                        device = device,
                        onDisconnect = { onDisconnectClick(device.id) }
                    )
                }
                
                HorizontalDivider()
            }
            
            // Секция обнаруженных устройств
            if (discoveredDevices.isEmpty() && !isScanning) {
                EmptyState(
                    icon = Icons.Default.Bluetooth,
                    message = "Нажмите на иконку поиска для начала сканирования",
                    buttonText = "Начать поиск",
                    onButtonClick = onScanClick
                )
            } else if (discoveredDevices.isEmpty() && isScanning) {
                EmptyState(
                    icon = Icons.Default.BluetoothSearching,
                    message = "Идёт поиск устройств...",
                    showButton = false
                )
            } else {
                Text(
                    text = "Обнаружено (${discoveredDevices.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
                
                LazyColumn {
                    items(discoveredDevices) { device ->
                        DeviceCard(
                            device = device,
                            isConnected = connectedSensors.any { it.id == device.address },
                            onClick = { onDeviceClick(device) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectedSensorCard(
    device: DeviceInfo,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
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
                Text(
                    text = device.name ?: "Неизвестное устройство",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = getSensorTypeName(device.type),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                )
            }
            
            OutlinedButton(onClick = onDisconnect) {
                Text("Отключить")
            }
        }
    }
}

@Composable
fun DeviceCard(
    device: BluetoothDevice,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(enabled = !isConnected, onClick = onClick),
        colors = if (isConnected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Неизвестное устройство",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isConnected) {
                Text(
                    text = "✓ Подключено",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    buttonText: String? = null,
    showButton: Boolean = true,
    onButtonClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (showButton && buttonText != null && onButtonClick != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onButtonClick) {
                Text(buttonText)
            }
        }
    }
}

fun getSensorTypeName(type: SensorType): String {
    return when (type) {
        SensorType.HEART_RATE -> "Пульс"
        SensorType.POWER_METER -> "Мощность"
        SensorType.SPEED_CADENCE -> "Скорость/Каденс"
        SensorType.SPEED_ONLY -> "Скорость"
        SensorType.CADENCE_ONLY -> "Каденс"
        SensorType.UNKNOWN -> "Неизвестный тип"
    }
}
