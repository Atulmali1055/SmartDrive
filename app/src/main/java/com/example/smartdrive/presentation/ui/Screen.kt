package com.example.smartdrive.presentation.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Navigation : Screen("navigation", "Nav", Icons.Default.Navigation)
    object Notifications : Screen("notifications", "Alerts", Icons.Default.Notifications)
    object Weather : Screen("weather", "Weather", Icons.Default.Cloud)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object About : Screen("about", "About", Icons.Default.Info)

    companion object {
        val bottomNavItems = listOf(Home, Navigation, Notifications, Weather, Settings)
    }
}
