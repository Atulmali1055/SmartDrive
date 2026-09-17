package com.example.smartdrive.presentation.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartdrive.MainViewModel
import com.example.smartdrive.SmartDriveApplication

@Composable
fun SettingsScreen(vm: MainViewModel, app: SmartDriveApplication) {
    val use24 by vm.use24Hour.collectAsStateWithLifecycle()
    val weather by vm.weatherEnabled.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ---- 24-hour time ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("24-hour time")
                        Text(
                            "Show time in 24h format on device",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = use24,
                        // Routes through the VM so the device gets the 0x7C packet.
                        onCheckedChange = { vm.setUse24Hour(it) }
                    )
                }

                HorizontalDivider()

                // ---- Weather ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Weather")
                        Text(
                            "Sync weather to device",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = weather,
                        onCheckedChange = { vm.setWeatherEnabled(it) }
                    )
                }
            }
        }

        // ---- Footer info (no click action) ----
        Spacer(Modifier.height(8.dp))
        Text(
            "SmartDrive v1.0.0",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Connected to: MY NAV",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}