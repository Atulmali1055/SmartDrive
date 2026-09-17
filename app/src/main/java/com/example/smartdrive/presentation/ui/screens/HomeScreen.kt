package com.example.smartdrive.presentation.ui.screens

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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
fun HomeScreen(
    vm: MainViewModel,
    onNavigateToBluetooth: () -> Unit = {}
) {
    val state by vm.connectionState.collectAsStateWithLifecycle()
    val sendStatus by vm.sendStatus.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // -------------------------------------------------------------
        // Device status card
        // -------------------------------------------------------------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Device", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (label, color) = when (state) {
                        ConnectionState.CONNECTED ->
                            "Connected to MY NAV" to MaterialTheme.colorScheme.primary
                        ConnectionState.CONNECTING ->
                            "Connecting…" to MaterialTheme.colorScheme.tertiary
                        ConnectionState.RECONNECTING ->
                            "Reconnecting…" to MaterialTheme.colorScheme.tertiary
                        ConnectionState.SCANNING ->
                            "Scanning…" to MaterialTheme.colorScheme.secondary
                        ConnectionState.DISCONNECTED ->
                            "Disconnected" to MaterialTheme.colorScheme.outline
                        is ConnectionState.ERROR ->
                            "Error" to MaterialTheme.colorScheme.error
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

                Spacer(Modifier.height(12.dp))

                // Only show the "go to Bluetooth" hint when there's something to do
                if (state !is ConnectionState.CONNECTED) {
                    OutlinedButton(
                        onClick = onNavigateToBluetooth,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open Bluetooth tab to connect") }
                }
            }
        }

        // -------------------------------------------------------------
        // Quick actions card
        // -------------------------------------------------------------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Quick actions", style = MaterialTheme.typography.titleMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.sendTestNavigation() },
                        modifier = Modifier.weight(1f),
                        enabled = state is ConnectionState.CONNECTED
                    ) { Text("Test Nav") }

                    OutlinedButton(
                        onClick = { vm.sendTime() },
                        modifier = Modifier.weight(1f),
                        enabled = state is ConnectionState.CONNECTED
                    ) { Text("Send Time") }
                }

                OutlinedButton(
                    onClick = { vm.sendPhoneBattery() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state is ConnectionState.CONNECTED
                ) { Text("Send Phone Battery") }

                Button(
                    onClick = { vm.sendNavigationInactive() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state is ConnectionState.CONNECTED
                ) { Text("Stop Navigation on Device") }
            }
        }

        // -------------------------------------------------------------
        // Status feedback
        // -------------------------------------------------------------
        sendStatus?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.weight(1f))
    }
}