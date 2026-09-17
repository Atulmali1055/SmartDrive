package com.example.smartdrive.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("SmartDrive", style = MaterialTheme.typography.headlineSmall)
        Text("Version 1.0.0", style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider()
        Text(
            "Motorcycle/bicycle navigation companion. Pairs with an ESP32 " +
                "device over BLE to display turn-by-turn directions, " +
                "notifications, calls, and weather on an ILI9341 display.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.weight(1f))
        Text("© SmartDrive", style = MaterialTheme.typography.bodySmall)
    }
}
