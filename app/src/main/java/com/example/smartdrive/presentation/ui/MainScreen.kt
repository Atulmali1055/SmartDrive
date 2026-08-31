package com.example.smartdrive.presentation.ui

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartdrive.MainViewModel
import com.example.smartdrive.presentation.ble.ConnectionState

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val devices by viewModel.discoveredDevices.collectAsState()
    val testPacket by viewModel.testPacketInput.collectAsState()
    val sendStatus by viewModel.sendStatus.collectAsState()
    val isNotificationEnabled by viewModel.isNotificationListenerEnabled.collectAsState()

    val currentState = connectionState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Status
        Text(
            text = "Status: ${currentState.javaClass.simpleName}",
            style = MaterialTheme.typography.titleMedium
        )
        if (currentState is ConnectionState.ERROR) {
            Text(
                text = "Error: ${currentState.message}",
                color = MaterialTheme.colorScheme.error
            )
        }

        // Device list (only shown when scanning or after)
        if (devices.isNotEmpty()) {
            Text("Discovered Devices:", style = MaterialTheme.typography.titleSmall)
            LazyColumn(
                modifier = Modifier.height(100.dp)
            ) {
                items(devices) { bleDevice ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = {
                            if (currentState !is ConnectionState.CONNECTED) {
                                viewModel.connectToDevice(bleDevice.device)
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = bleDevice.name ?: "Unknown")
                            Text(text = "RSSI: ${bleDevice.rssi} dBm", fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            if (currentState is ConnectionState.SCANNING) {
                Text("Scanning... (no devices found yet)", style = MaterialTheme.typography.bodySmall)
            }
        }

        // Scan / Connect / Disconnect buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.scanDevices() },
                enabled = currentState !is ConnectionState.SCANNING && currentState !is ConnectionState.CONNECTED
            ) {
                Text("Scan")
            }
            Button(
                onClick = { viewModel.disconnect() },
                enabled = currentState is ConnectionState.CONNECTED
            ) {
                Text("Disconnect")
            }
            Button(
                onClick = { viewModel.stopScan() },
                enabled = currentState is ConnectionState.SCANNING
            ) {
                Text("Stop Scan")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Test packet input
        OutlinedTextField(
            value = testPacket,
            onValueChange = { viewModel.updateTestPacket(it) },
            label = { Text("Test Packet") },
            modifier = Modifier.fillMaxWidth()
        )

        // Send button
        Button(
            onClick = { viewModel.sendTestPacket() },
            enabled = currentState is ConnectionState.CONNECTED
        ) {
            Text("Send Test Packet")
        }

        // Send status
        sendStatus?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall)
        }

        // Notification Access section
        Text("Notification Access: ${if (isNotificationEnabled) "Enabled" else "Disabled"}")
        Row {
            Button(
                onClick = { viewModel.requestNotificationAccess() },
                enabled = !isNotificationEnabled
            ) {
                Text("Enable Notification Access")
            }
            Spacer(modifier = Modifier.weight(1f))
            // Debug button for emulator – simulate Maps notification
            Button(
                onClick = { viewModel.simulateGoogleMapsNotification() },
                enabled = currentState is ConnectionState.CONNECTED
            ) {
                Text("Simulate Maps")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Footer info
        Text(
            text = "SmartDrive Development v1.0",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}