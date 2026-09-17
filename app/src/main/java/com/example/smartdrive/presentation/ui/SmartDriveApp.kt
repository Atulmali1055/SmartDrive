package com.example.smartdrive.presentation.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.smartdrive.MainViewModel
import com.example.smartdrive.SmartDriveApplication
import com.example.smartdrive.presentation.ui.screens.AboutScreen
import com.example.smartdrive.presentation.ui.screens.BluetoothScreen
import com.example.smartdrive.presentation.ui.screens.HomeScreen
import com.example.smartdrive.presentation.ui.screens.NavigationScreen
import com.example.smartdrive.presentation.ui.screens.NotificationsScreen
import com.example.smartdrive.presentation.ui.screens.SettingsScreen
import com.example.smartdrive.presentation.ui.screens.WeatherScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartDriveApp(app: SmartDriveApplication) {
    val context = LocalContext.current
    val vm = androidx.lifecycle.viewmodel.compose.viewModel<MainViewModel> {
        MainViewModel(context, app.bleManager, app.settings)
    }

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SmartDrive") },
                actions = {
                    IconButton(onClick = {
                        navController.navigate(Screen.Settings.route) {
                            launchSingleTop = true
                        }
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                Screen.bottomNavItems.forEach { item ->
                    val selected = current?.hierarchy?.any { it.route == item.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { inner ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(inner)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    vm = vm,
                    onNavigateToBluetooth = {
                        navController.navigate(Screen.Bluetooth.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Screen.Navigation.route)    { NavigationScreen(vm) }
            composable(Screen.Notifications.route) { NotificationsScreen(vm, app) }
            composable(Screen.Weather.route)       { WeatherScreen(vm, app) }
            composable(Screen.Bluetooth.route)     { BluetoothScreen(vm) }
            composable(Screen.Settings.route)      { SettingsScreen(vm, app) }
            composable(Screen.About.route)         { AboutScreen() }
        }
    }
}