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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
    val allowAll by app.settings.allowAll.collectAsStateWithLifecycle()

    var listenerEnabled by remember { mutableStateOf(vm.isNotificationListenerEnabled()) }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var showDenyAllDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { manager.loadInstalledApps() }
    }
    LaunchedEffect(Unit) { listenerEnabled = vm.isNotificationListenerEnabled() }

    // Apps filtered by search query (ignored when allowAll is on)
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---------------------------------------------------------------
        // Notification Access card
        // ---------------------------------------------------------------
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Notification Access",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (listenerEnabled) "Enabled" else "Disabled",
                        color = if (listenerEnabled)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.openNotificationSettings() },
                        enabled = !listenerEnabled
                    ) { Text("Open Settings") }
                }
            }
        }

        // ---------------------------------------------------------------
        // Master "Allow all" card
        // ---------------------------------------------------------------
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Allow all apps",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Forward notifications from every installed app",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = allowAll,
                            onCheckedChange = { checked ->
                                if (checked) app.settings.allowAll()
                                else app.settings.resetToDefaults()
                            }
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { app.settings.resetToDefaults() },
                            modifier = Modifier.weight(1f),
                            enabled = !allowAll
                        ) { Text("Reset defaults") }

                        OutlinedButton(
                            onClick = { showDenyAllDialog = true },
                            modifier = Modifier.weight(1f),
                            enabled = !allowAll && allowed.isNotEmpty()
                        ) { Text("Deny all") }
                    }
                }
            }
        }

        // ---------------------------------------------------------------
        // Search + section header (hidden while allowAll is on)
        // ---------------------------------------------------------------
        item {
            Column {
                Text("Enabled apps", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (allowAll)
                        "All apps are currently allowed. Turn off the master switch above to filter individually."
                    else
                        "Toggle which apps can send notifications to the ESP32 display.",
                    style = MaterialTheme.typography.bodySmall
                )

                if (!allowAll) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search apps") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${filtered.size} app(s)",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // ---------------------------------------------------------------
        // App list (dimmed & disabled while allowAll is on)
        // ---------------------------------------------------------------
        items(filtered, key = { it.packageName }) { a ->
            val checked = allowAll || allowed.contains(a.packageName)

            Column(
                Modifier
                    .fillMaxWidth()
                    .alpha(if (allowAll) 0.5f else 1f)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(a.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            a.packageName,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = checked,
                        enabled = !allowAll,
                        onCheckedChange = { manager.setAllowed(a.packageName, it) }
                    )
                }
                HorizontalDivider()
            }
        }

        // ---------------------------------------------------------------
        // Bottom spacer so last row isn't hidden by the nav bar
        // ---------------------------------------------------------------
        item { Spacer(Modifier.height(24.dp)) }
    }

    // ---------------------------------------------------------------
    // Deny-all confirmation dialog
    // ---------------------------------------------------------------
    if (showDenyAllDialog) {
        AlertDialog(
            onDismissRequest = { showDenyAllDialog = false },
            title = { Text("Deny all notifications?") },
            text = {
                Text(
                    "Only Google Maps navigation will be forwarded. " +
                        "You can re-enable individual apps at any time."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    app.settings.clearFilter()
                    showDenyAllDialog = false
                }) { Text("Deny all") }
            },
            dismissButton = {
                TextButton(onClick = { showDenyAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}