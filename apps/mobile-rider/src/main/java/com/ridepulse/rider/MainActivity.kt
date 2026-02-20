package com.ridepulse.rider

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.ridepulse.rider.data.model.DeviceInfo
import com.ridepulse.rider.ui.screens.DeviceScanScreen
import com.ridepulse.rider.ui.screens.SessionScreen
import com.ridepulse.rider.ui.theme.RidePulseTheme
import com.ridepulse.rider.ui.viewmodel.RideUiState
import com.ridepulse.rider.ui.viewmodel.RideViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private val viewModel: RideViewModel by viewModels()
    
    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* Обработка результата включения Bluetooth */ }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Устанавливаем riderId (в реальном приложении - из настроек или логина)
        viewModel.setRiderId(getOrCreateRiderId())
        
        setContent {
            RidePulseTheme {
                Surface(
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel)
                }
            }
        }
    }
    
    private fun getOrCreateRiderId(): String {
        val prefs = getSharedPreferences("ridepulse_prefs", MODE_PRIVATE)
        var riderId = prefs.getString("rider_id", null)
        if (riderId == null) {
            riderId = "rider_${System.currentTimeMillis()}"
            prefs.edit().putString("rider_id", riderId).apply()
        }
        return riderId
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(viewModel: RideViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val connectedSensors by viewModel.connectedSensors.collectAsState()
    val currentMetrics by viewModel.currentMetrics.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    
    val context = LocalContext.current
    
    // Разрешения
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )
    )
    
    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }
    
    when (uiState) {
        is RideUiState.Idle -> {
            DeviceScanScreen(
                isScanning = false,
                discoveredDevices = emptyList(),
                connectedSensors = connectedSensors,
                onScanClick = { viewModel.startScanning() },
                onStopScanClick = { viewModel.stopScanning() },
                onDeviceClick = {},
                onDisconnectClick = {}
            )
        }
        
        is RideUiState.Scanning -> {
            DeviceScanScreen(
                isScanning = true,
                discoveredDevices = discoveredDevices,
                connectedSensors = connectedSensors,
                onScanClick = { viewModel.startScanning() },
                onStopScanClick = { viewModel.stopScanning() },
                onDeviceClick = { device -> viewModel.connectDevice(device) },
                onDisconnectClick = { address -> viewModel.disconnectDevice(address) }
            )
        }
        
        is RideUiState.DevicesFound -> {
            DeviceScanScreen(
                isScanning = true,
                discoveredDevices = discoveredDevices,
                connectedSensors = connectedSensors,
                onScanClick = { viewModel.startScanning() },
                onStopScanClick = { viewModel.stopScanning() },
                onDeviceClick = { device -> viewModel.connectDevice(device) },
                onDisconnectClick = { address -> viewModel.disconnectDevice(address) }
            )
        }
        
        is RideUiState.Connecting -> {
            DeviceScanScreen(
                isScanning = false,
                discoveredDevices = discoveredDevices,
                connectedSensors = connectedSensors,
                onScanClick = { viewModel.startScanning() },
                onStopScanClick = { viewModel.stopScanning() },
                onDeviceClick = {},
                onDisconnectClick = { address -> viewModel.disconnectDevice(address) }
            )
        }
        
        is RideUiState.SessionActive -> {
            // Запускаем сессию при первом входе в это состояние
            LaunchedEffect(Unit) {
                viewModel.startSession(
                    wsUrl = BuildConfig.WS_URL,
                    apiUrl = BuildConfig.API_URL
                )
            }
            
            SessionScreen(
                metrics = currentMetrics,
                connectedSensorsCount = connectedSensors.size,
                connectionState = connectionState,
                onStopSession = { viewModel.stopSession() }
            )
        }
        
        is RideUiState.BluetoothDisabled -> {
            BluetoothDisabledScreen(onRetry = {
                // Запрос на включение Bluetooth
            })
        }
        
        is RideUiState.Error -> {
            ErrorScreen(
                message = (uiState as RideUiState.Error).message,
                onRetry = { viewModel.startScanning() }
            )
        }
    }
}

@Composable
fun BluetoothDisabledScreen(onRetry: () -> Unit) {
    androidx.compose.material3.Scaffold { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = androidx.compose.foundation.layout Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = androidx.compose.foundation.layout.Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            androidx.compose.material3.Text(
                text = "Bluetooth отключён",
                style = MaterialTheme.typography.titleLarge
            )
            androidx.compose.foundation.layout Spacer(modifier = androidx.compose.foundation.layout.height(16.dp))
            androidx.compose.material3.Button(onClick = onRetry) {
                androidx.compose.material3.Text("Включить")
            }
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
