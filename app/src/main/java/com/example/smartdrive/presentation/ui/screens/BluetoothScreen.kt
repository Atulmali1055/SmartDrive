package com.example.smartdrive.presentation.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartdrive.MainViewModel
import com.example.smartdrive.presentation.ble.ConnectionState

@Composable
fun BluetoothScreen(vm: MainViewModel) {
    val state by vm.connectionState.collectAsStateWithLifecycle()
    val devices by vm.discoveredDevices.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- Status card ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Status", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (label, color) = when (state) {
                        ConnectionState.CONNECTED     ->
                            "Connected"     to MaterialTheme.colorScheme.primary
                        ConnectionState.CONNECTING    ->
                            "Connecting…"   to MaterialTheme.colorScheme.tertiary
                        ConnectionState.RECONNECTING  ->
                            "Reconnecting…" to MaterialTheme.colorScheme.tertiary
                        ConnectionState.SCANNING      ->
                            "Scanning…"     to MaterialTheme.colorScheme.secondary
                        ConnectionState.DISCONNECTED  ->
                            "Disconnected"  to MaterialTheme.colorScheme.outline
                        is ConnectionState.ERROR      ->
                            "Error"         to MaterialTheme.colorScheme.error
                    }
                    Text("●", color = color)
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
                if (state is ConnectionState.ERROR) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        (state as ConnectionState.ERROR).message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // ---- Scan / Stop / Disconnect ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { vm.scan() },
                modifier = Modifier.weight(1f),
                enabled = state !is ConnectionState.SCANNING &&
                        state !is ConnectionState.CONNECTED &&
                        state !is ConnectionState.CONNECTING &&
                        state !is ConnectionState.RECONNECTING
            ) { Text("Scan") }

            Button(
                onClick = { vm.stopScan() },
                modifier = Modifier.weight(1f),
                enabled = state is ConnectionState.SCANNING
            ) { Text("Stop") }

            Button(
                onClick = { vm.disconnect() },
                modifier = Modifier.weight(1f),
                enabled = state is ConnectionState.CONNECTED
            ) { Text("Disconnect") }
        }

        // ---- Device list ----
        Text("Nearby devices", style = MaterialTheme.typography.titleMedium)

        if (devices.isEmpty()) {
            Text(
                when (state) {
                    is ConnectionState.SCANNING    -> "Scanning… no devices found yet."
                    is ConnectionState.CONNECTED   -> "Connected."
                    is ConnectionState.CONNECTING,
                    is ConnectionState.RECONNECTING -> "Connecting…"
                    else                           -> "Tap Scan to search for MY NAV."
                },
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(devices, key = { it.device.address }) { d ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (state !is ConnectionState.CONNECTED &&
                                state !is ConnectionState.CONNECTING &&
                                state !is ConnectionState.RECONNECTING
                            ) {
                                vm.connect(d.device)
                            }
                        }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                d.name ?: "Unknown",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "${d.device.address}  •  ${d.rssi} dBm",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}