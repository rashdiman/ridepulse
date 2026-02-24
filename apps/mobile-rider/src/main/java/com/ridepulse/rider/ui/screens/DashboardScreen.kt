package com.ridepulse.rider.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.json.JSONObject

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val connected = viewModel.isConnected.collectAsState()
    val sessions = viewModel.activeSessions.collectAsState()
    val latestMetric = viewModel.latestMetric.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = {
            if (!connected.value) viewModel.connect() else viewModel.disconnect()
        }) {
            Text(if (!connected.value) "Connect Dashboard" else "Disconnect")
        }

        Text("Active sessions:", modifier = Modifier.padding(top = 12.dp))
        LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
            items(sessions.value) { s ->
                val id = s.optString("id")
                val name = s.optString("riderName")
                Text("$id — $name", modifier = Modifier.padding(vertical = 4.dp))
            }
        }

        Text("Latest metric:", modifier = Modifier.padding(top = 12.dp))
        Text(latestMetric.value?.toString() ?: "—", modifier = Modifier.padding(top = 8.dp))
    }
}

