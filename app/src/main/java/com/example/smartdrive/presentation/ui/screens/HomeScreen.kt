package com.example.smartdrive.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartdrive.MainViewModel
import com.example.smartdrive.presentation.ble.ConnectionState

@Composable
fun HomeScreen(vm: MainViewModel) {
    val state by vm.connectionState.collectAsStateWithLifecycle()
    val devices by vm.discoveredDevices.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Device", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (label, color) = when (state) {
                        ConnectionState.CONNECTED -> "Connected" to MaterialTheme.colorScheme.primary
                        ConnectionState.CONNECTING -> "Connecting…" to MaterialTheme.colorScheme.tertiary
                        ConnectionState.RECONNECTING -> "Reconnecting…" to MaterialTheme.colorScheme.tertiary
                        ConnectionState.SCANNING -> "Scanning…" to MaterialTheme.colorScheme.secondary
                        ConnectionState.DISCONNECTED -> "Disconnected" to MaterialTheme.colorScheme.outline
                        is ConnectionState.ERROR -> "Error" to MaterialTheme.colorScheme.error
                    }
                    Text("●", color = color); Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.scan() }) { Text("Scan") }
            Button(onClick = { vm.stopScan() }) { Text("Stop") }
            Button(
                onClick = { vm.disconnect() },
                enabled = state is ConnectionState.CONNECTED
            ) { Text("Disconnect") }
        }

        if (devices.isNotEmpty()) {
            Text("Nearby devices", style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false).heightIn(max = 200.dp)
            ) {
                items(devices) { d ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        onClick = { vm.connect(d.device) }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(d.name ?: "Unknown", style = MaterialTheme.typography.titleSmall)
                            Text("RSSI: ${d.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.sendTestNavigation() }) { Text("Test Nav") }
            OutlinedButton(onClick = { vm.sendTime() }) { Text("Send Time") }
            OutlinedButton(onClick = { vm.sendPhoneBattery() }) { Text("Send Battery") }
        }

        vm.sendStatus.collectAsStateWithLifecycle().value?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}
