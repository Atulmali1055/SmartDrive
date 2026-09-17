package com.example.smartdrive.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartdrive.MainViewModel
import com.example.smartdrive.SmartDriveApplication
import com.example.smartdrive.presentation.notification.InstalledApp
import com.example.smartdrive.presentation.notification.NotificationFilterManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun NotificationsScreen(vm: MainViewModel, app: SmartDriveApplication) {
    val context = LocalContext.current
    val manager = remember { NotificationFilterManager(context, app.settings) }
    val allowed by vm.notificationFilter.collectAsStateWithLifecycle()

    var listenerEnabled by remember { mutableStateOf(vm.isNotificationListenerEnabled()) }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { manager.loadInstalledApps() }
    }
    LaunchedEffect(Unit) { listenerEnabled = vm.isNotificationListenerEnabled() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Notification Access", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (listenerEnabled) "Enabled" else "Disabled",
                    color = if (listenerEnabled)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.openNotificationSettings() },
                    enabled = !listenerEnabled
                ) { Text("Open Settings") }
            }
        }

        Text("Enabled apps", style = MaterialTheme.typography.titleMedium)
        Text(
            "Toggle which apps can send notifications to the ESP32 display.",
            style = MaterialTheme.typography.bodySmall
        )

        LazyColumn(Modifier.weight(1f)) {
            items(apps, key = { it.packageName }) { a ->
                val checked = allowed.contains(a.packageName)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(a.label, style = MaterialTheme.typography.bodyLarge)
                        Text(a.packageName, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = checked,
                        onCheckedChange = { manager.setAllowed(a.packageName, it) }
                    )
                }
                HorizontalDivider()
            }
        }
    }
}
