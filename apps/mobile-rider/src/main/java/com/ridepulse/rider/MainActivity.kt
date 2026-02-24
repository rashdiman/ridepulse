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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
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
import com.ridepulse.rider.ui.screens.RiderAuthScreen
import com.ridepulse.rider.ui.screens.SessionScreen
import com.ridepulse.rider.ui.theme.RidePulseTheme
import com.ridepulse.rider.ui.viewmodel.RiderAuthUiState
import com.ridepulse.rider.ui.viewmodel.RiderAuthViewModel
import com.ridepulse.rider.ui.viewmodel.RideUiState
import com.ridepulse.rider.ui.viewmodel.RideViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val rideViewModel: RideViewModel by viewModels()
    private val riderAuthViewModel: RiderAuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RidePulseTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainScreen(rideViewModel, riderAuthViewModel)
                }
            }
        }
    }
}

private enum class AppMode {
    RIDER,
    COACH
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(viewModel: RideViewModel, riderAuthViewModel: RiderAuthViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val connectedSensors by viewModel.connectedSensors.collectAsState()
    val currentMetrics by viewModel.currentMetrics.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val riderAuthState by riderAuthViewModel.uiState.collectAsState()

    var appMode by rememberSaveable { mutableStateOf(AppMode.RIDER) }
    var showRiderMenu by rememberSaveable { mutableStateOf(false) }

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

    LaunchedEffect(riderAuthState) {
        when (val state = riderAuthState) {
            is RiderAuthUiState.Authenticated -> viewModel.setRiderId(state.user.id)
            else -> {
                viewModel.clearRiderId()
                viewModel.disconnectAll()
            }
        }
    }

    LaunchedEffect(appMode) {
        if (appMode == AppMode.RIDER) {
            riderAuthViewModel.devLoginAsRider()
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
                    if (appMode == AppMode.RIDER && riderAuthState is RiderAuthUiState.Authenticated) {
                        IconButton(onClick = { showRiderMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Rider menu")
                        }
                        DropdownMenu(
                            expanded = showRiderMenu,
                            onDismissRequest = { showRiderMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Logout") },
                                onClick = {
                                    showRiderMenu = false
                                    viewModel.stopSession()
                                    viewModel.disconnectAll()
                                    riderAuthViewModel.logout()
                                }
                            )
                        }
                    }
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
                AppMode.RIDER -> when (val authState = riderAuthState) {
                    is RiderAuthUiState.Loading -> LoadingScreen("Checking rider auth...")
                    is RiderAuthUiState.NotAuthenticated -> RiderAuthScreen(
                        isLoading = false,
                        errorMessage = null,
                        onLogin = { email, password -> riderAuthViewModel.login(email, password) },
                        onRegister = { email, password, name -> riderAuthViewModel.register(email, password, name) }
                    )
                    is RiderAuthUiState.Error -> RiderAuthScreen(
                        isLoading = false,
                        errorMessage = authState.message,
                        onLogin = { email, password -> riderAuthViewModel.login(email, password) },
                        onRegister = { email, password, name -> riderAuthViewModel.register(email, password, name) }
                    )
                    is RiderAuthUiState.Authenticated -> RiderModeContent(
                        uiState = uiState,
                        discoveredDevices = discoveredDevices,
                        connectedSensors = connectedSensors,
                        currentMetrics = currentMetrics,
                        connectionState = connectionState,
                        onStartScan = { viewModel.startScanning() },
                        onStopScan = { viewModel.stopScanning() },
                        onConnectDevice = { device -> viewModel.connectDevice(device) },
                        onDisconnectDevice = { address -> viewModel.disconnectDevice(address) },
                        onStartSession = { viewModel.startSession() },
                        onStopSession = { viewModel.stopSession() }
                    )
                }
                AppMode.COACH -> CoachRootScreen(autoAuth = true)
            }
        }
    }
}

@Composable
private fun LoadingScreen(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text(message)
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
    val tabs = listOf("Connections", "Monitoring")
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    if (uiState is RideUiState.SessionActive) {
        LaunchedEffect(uiState) { onStartSession() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> when (uiState) {
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
                is RideUiState.SessionActive -> DeviceScanScreen(
                    isScanning = false,
                    discoveredDevices = discoveredDevices,
                    connectedSensors = connectedSensors,
                    onScanClick = onStartScan,
                    onStopScanClick = onStopScan,
                    onDeviceClick = onConnectDevice,
                    onDisconnectClick = onDisconnectDevice
                )
                is RideUiState.BluetoothDisabled -> BluetoothDisabledScreen(onRetry = onStartScan)
                is RideUiState.Error -> ErrorScreen(message = uiState.message, onRetry = onStartScan)
            }
            else -> when (uiState) {
                is RideUiState.SessionActive -> SessionScreen(
                    metrics = currentMetrics,
                    connectedSensorsCount = connectedSensors.size,
                    connectionState = connectionState,
                    onStopSession = onStopSession
                )
                is RideUiState.BluetoothDisabled -> BluetoothDisabledScreen(onRetry = onStartScan)
                is RideUiState.Error -> ErrorScreen(message = uiState.message, onRetry = onStartScan)
                else -> MonitoringIdleScreen(
                    connectedSensorsCount = connectedSensors.size,
                    onStartSession = onStartSession
                )
            }
        }
    }
}

@Composable
private fun MonitoringIdleScreen(
    connectedSensorsCount: Int,
    onStartSession: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Monitoring is not running", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Connected sensors: $connectedSensorsCount")
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onStartSession,
            enabled = connectedSensorsCount > 0
        ) {
            Text("Start monitoring")
        }
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
