package com.example.smartdrive

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.smartdrive.presentation.ble.BleManager
import com.example.smartdrive.presentation.ui.MainScreen
import com.example.smartdrive.presentation.theme.SmartDriveTheme

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: BleManager
    private lateinit var viewModel: MainViewModel

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            // proceed
            viewModel.scanDevices()
        } else {
            // Show a message? For now, just handle gracefully.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bleManager = BleManager.get(this)
        viewModel = MainViewModel(this, bleManager)

        viewModel.setNotificationAccessRequest {
            openNotificationAccessSettings()
        }

        setContent {
            SmartDriveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }

        // Check permissions after UI is set
        if (!hasRequiredPermissions()) {
            requestPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkNotificationListenerEnabled()
    }

    private fun hasRequiredPermissions(): Boolean {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        // Location permission for scanning (required on Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }
    fun openNotificationAccessSettings() {
        val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }
}
