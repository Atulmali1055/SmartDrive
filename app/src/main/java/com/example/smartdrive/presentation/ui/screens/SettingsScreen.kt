package com.example.smartdrive.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
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
    val use24 by vm.settings.use24Hour.collectAsStateWithLifecycle()
    val weather by vm.settings.weatherEnabled.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("24-hour time")
                        Text("Show time in 24h format on device",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = use24, onCheckedChange = { vm.settings.setUse24Hour(it) })
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Weather")
                        Text("Sync weather to device",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = weather, onCheckedChange = { vm.settings.setWeatherEnabled(it) })
                }
            }
        }
    }
}