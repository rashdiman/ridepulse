package com.ridepulse.rider

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.ridepulse.rider.coach.ui.screens.CoachRootScreen
import com.ridepulse.rider.data.model.DeviceInfo
import com.ridepulse.rider.data.model.SensorData
import com.ridepulse.rider.network.DataSender
import com.ridepulse.rider.ui.screens.DeviceScanScreen
import com.ridepulse.rider.ui.screens.SessionScreen
import com.ridepulse.rider.ui.theme.RidePulseTheme
import com.ridepulse.rider.ui.viewmodel.RideUiState
import com.ridepulse.rider.ui.viewmodel.RideViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: RideViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setRiderId(getOrCreateRiderId())

        setContent {
            RidePulseTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
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

private enum class AppMode {
    RIDER,
    COACH
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(viewModel: RideViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val connectedSensors by viewModel.connectedSensors.collectAsState()
    val currentMetrics by viewModel.currentMetrics.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    var appMode by rememberSaveable { mutableStateOf(AppMode.RIDER) }

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

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("RidePulse Unified", style = MaterialTheme.typography.titleLarge)
                Row {
                    OutlinedButton(onClick = { appMode = AppMode.RIDER }) {
                        Text("Rider")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = { appMode = AppMode.COACH }) {
                        Text("Coach")
                    }
                }
            }
        }
    ) { padding ->
        Surface(
            modifier = Modifier.padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            when (appMode) {
                AppMode.RIDER -> RiderModeContent(
                    uiState = uiState,
                    discoveredDevices = discoveredDevices,
                    connectedSensors = connectedSensors,
                    currentMetrics = currentMetrics,
                    connectionState = connectionState,
                    onStartScan = { viewModel.startScanning() },
                    onStopScan = { viewModel.stopScanning() },
                    onConnectDevice = { device -> viewModel.connectDevice(device) },
                    onDisconnectDevice = { address -> viewModel.disconnectDevice(address) },
                    onStartSession = {
                        viewModel.startSession(
                            wsUrl = BuildConfig.WS_URL,
                            apiUrl = BuildConfig.API_URL
                        )
                    },
                    onStopSession = { viewModel.stopSession() }
                )
                AppMode.COACH -> CoachRootScreen()
            }
        }
    }
}

@Composable
private fun RiderModeContent(
    uiState: RideUiState,
    discoveredDevices: List<BluetoothDevice>,
    connectedSensors: List<DeviceInfo>,
    currentMetrics: SensorData?,
    connectionState: DataSender.ConnectionState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectDevice: (BluetoothDevice) -> Unit,
    onDisconnectDevice: (String) -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit
) {
    when (uiState) {
        is RideUiState.Idle -> DeviceScanScreen(
            isScanning = false,
            discoveredDevices = emptyList(),
            connectedSensors = connectedSensors,
            onScanClick = onStartScan,
            onStopScanClick = onStopScan,
            onDeviceClick = {},
            onDisconnectClick = {}
        )
        is RideUiState.Scanning -> DeviceScanScreen(
            isScanning = true,
            discoveredDevices = discoveredDevices,
            connectedSensors = connectedSensors,
            onScanClick = onStartScan,
            onStopScanClick = onStopScan,
            onDeviceClick = onConnectDevice,
            onDisconnectClick = onDisconnectDevice
        )
        is RideUiState.DevicesFound -> DeviceScanScreen(
            isScanning = true,
            discoveredDevices = discoveredDevices,
            connectedSensors = connectedSensors,
            onScanClick = onStartScan,
            onStopScanClick = onStopScan,
            onDeviceClick = onConnectDevice,
            onDisconnectClick = onDisconnectDevice
        )
        is RideUiState.Connecting -> DeviceScanScreen(
            isScanning = false,
            discoveredDevices = discoveredDevices,
            connectedSensors = connectedSensors,
            onScanClick = onStartScan,
            onStopScanClick = onStopScan,
            onDeviceClick = {},
            onDisconnectClick = onDisconnectDevice
        )
        is RideUiState.SessionActive -> {
            LaunchedEffect(Unit) { onStartSession() }
            SessionScreen(
                metrics = currentMetrics,
                connectedSensorsCount = connectedSensors.size,
                connectionState = connectionState,
                onStopSession = onStopSession
            )
        }
        is RideUiState.BluetoothDisabled -> BluetoothDisabledScreen(onRetry = onStartScan)
        is RideUiState.Error -> ErrorScreen(message = uiState.message, onRetry = onStartScan)
    }
}

@Composable
fun BluetoothDisabledScreen(onRetry: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Bluetooth disabled", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(message, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

