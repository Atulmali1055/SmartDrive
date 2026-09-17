package com.example.smartdrive

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.smartdrive.presentation.ble.BleCommandHandler
import com.example.smartdrive.presentation.media.MusicController
import com.example.smartdrive.presentation.ui.SmartDriveApp
import com.example.smartdrive.presentation.ui.theme.SmartDriveTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as SmartDriveApplication
        val music = MusicController(this)
        app.bleManager.setCommandHandler(BleCommandHandler(music))

        setContent {
            SmartDriveTheme {
                SmartDriveApp(app = app)
            }
        }

        requestMissingPermissions()
    }

    private fun requestMissingPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed += Manifest.permission.BLUETOOTH_SCAN
            needed += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            needed += Manifest.permission.BLUETOOTH
            needed += Manifest.permission.BLUETOOTH_ADMIN
        }
        needed += Manifest.permission.ACCESS_FINE_LOCATION
        needed += Manifest.permission.READ_PHONE_STATE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }
}
