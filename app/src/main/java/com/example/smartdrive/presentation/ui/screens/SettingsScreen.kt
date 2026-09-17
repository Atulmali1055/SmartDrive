package com.example.smartdrive.presentation.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartdrive.MainViewModel
import com.example.smartdrive.SmartDriveApplication

@Composable
fun SettingsScreen(vm: MainViewModel, app: SmartDriveApplication) {
    val use24 by vm.settings.use24Hour.collectAsStateWithLifecycle()
    val weather by vm.settings.weatherEnabled.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("24-hour time")
                        Text("Show time as 24h on device", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = use24, onCheckedChange = { vm.settings.setUse24Hour(it) })
                }
                HorizontalDivider()
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Weather")
                        Text("Sync weather to device", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = weather, onCheckedChange = { vm.settings.setWeatherEnabled(it) })
                }
            }
        }

        Card(Modifier.clickable { /* About is a nav tab already */ }) {
            Row(Modifier.padding(16.dp)) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text("About SmartDrive")
            }
        }
    }
}
