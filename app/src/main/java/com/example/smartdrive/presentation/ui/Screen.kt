package com.example.smartdrive.presentation.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {

    object Home          : Screen("home",          "Home",      Icons.Default.Home)
    object Navigation    : Screen("navigation",    "Nav",       Icons.Default.Navigation)
    object Notifications : Screen("notifications", "Alerts",    Icons.Default.Notifications)
    object Weather       : Screen("weather",       "Weather",   Icons.Default.Cloud)
    object Bluetooth     : Screen("bluetooth",     "Bluetooth", Icons.Default.Bluetooth)
    object Settings      : Screen("settings",      "Settings",  Icons.Default.Settings)
    object About         : Screen("about",         "About",     Icons.Default.Info)

    companion object {
        /** Bottom navigation items — rightmost is Bluetooth, matching Chronos UX. */
        val bottomNavItems = listOf(Home, Navigation, Notifications, Weather, Bluetooth)
    }
}