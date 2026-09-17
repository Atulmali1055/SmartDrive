package com.example.smartdrive.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smartdrive.MainViewModel

@Composable
fun NavigationScreen(vm: MainViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Navigation", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Google Maps notifications are captured automatically when " +
                "Notification Access is granted. Put Maps in the background " +
                "during a route for updates to be sent.",
            style = MaterialTheme.typography.bodyMedium
        )
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Quick test", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { vm.sendTestNavigation() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Send sample navigation")
                }
            }
        }
    }
}
