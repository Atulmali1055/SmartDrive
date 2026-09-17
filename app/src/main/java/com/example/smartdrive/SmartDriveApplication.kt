package com.example.smartdrive

import android.app.Application
import com.example.smartdrive.presentation.ble.BleManager
import com.example.smartdrive.presentation.settings.AppSettings

class SmartDriveApplication : Application() {

    lateinit var bleManager: BleManager
        private set

    lateinit var settings: AppSettings
        private set

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        bleManager = BleManager(this)
    }
}
