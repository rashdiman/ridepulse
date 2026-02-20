package com.ridepulse.rider.ui.screens

import android.Manifest
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.ridepulse.rider.data.model.DeviceInfo
import com.ridepulse.rider.data.model.SensorType

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
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
    val permissions = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    )

    LaunchedEffect(Unit) {
        if (!permissions.allPermissionsGranted) {
            permissions.launchMultiplePermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device scan") },
                actions = {
                    IconButton(onClick = { if (isScanning) onStopScanClick() else onScanClick() }) {
                        Icon(
                            imageVector = if (isScanning) Icons.Default.Refresh else Icons.Default.BluetoothSearching,
                            contentDescription = if (isScanning) "Stop scan" else "Start scan"
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
            if (connectedSensors.isNotEmpty()) {
                Text(
                    text = "Connected (${connectedSensors.size})",
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
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }

            if (discoveredDevices.isEmpty() && !isScanning) {
                EmptyState(
                    icon = Icons.Default.Bluetooth,
                    message = "Tap scan to discover BLE sensors",
                    buttonText = "Start scan",
                    onButtonClick = onScanClick
                )
            } else if (discoveredDevices.isEmpty() && isScanning) {
                EmptyState(
                    icon = Icons.Default.BluetoothSearching,
                    message = "Scanning...",
                    showButton = false
                )
            } else {
                Text(
                    text = "Discovered (${discoveredDevices.size})",
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
private fun ConnectedSensorCard(device: DeviceInfo, onDisconnect: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name ?: "Unknown device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(getSensorTypeName(device.type), style = MaterialTheme.typography.bodyMedium)
                Text(device.address, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
        }
    }
}

@Composable
private fun DeviceCard(device: BluetoothDevice, isConnected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(enabled = !isConnected, onClick = onClick),
        colors = if (isConnected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
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
                Text(device.name ?: "Unknown device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(device.address, style = MaterialTheme.typography.bodySmall)
            }
            if (isConnected) {
                Text("Connected", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
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
        Icon(icon, contentDescription = null, modifier = Modifier.size(80.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge)
        if (showButton && buttonText != null && onButtonClick != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onButtonClick) { Text(buttonText) }
        }
    }
}

private fun getSensorTypeName(type: SensorType): String = when (type) {
    SensorType.HEART_RATE -> "Heart rate"
    SensorType.POWER_METER -> "Power"
    SensorType.SPEED_CADENCE -> "Speed/Cadence"
    SensorType.SPEED_ONLY -> "Speed"
    SensorType.CADENCE_ONLY -> "Cadence"
    SensorType.UNKNOWN -> "Unknown"
}
