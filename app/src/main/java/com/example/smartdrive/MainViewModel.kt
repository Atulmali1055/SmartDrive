package com.example.smartdrive

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartdrive.presentation.ble.BleManager
import com.example.smartdrive.presentation.ble.BlePacket
import com.example.smartdrive.presentation.ble.ConnectionState
import com.example.smartdrive.presentation.ble.BleDevice
import com.example.smartdrive.presentation.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val context: Context,
    private val bleManager: BleManager,
    val settings: AppSettings
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState
    val discoveredDevices: StateFlow<List<BleDevice>> = bleManager.discoveredDevices
    val notificationFilter = settings.notificationFilter

    private val _sendStatus = MutableStateFlow<String?>(null)
    val sendStatus: StateFlow<String?> = _sendStatus.asStateFlow()

    fun scan() {
        viewModelScope.launch { bleManager.startScan() }
    }
    fun stopScan() = bleManager.stopScan()
    fun connect(device: android.bluetooth.BluetoothDevice) = bleManager.connectToDevice(device)
    fun disconnect() = bleManager.disconnect()

    fun openNotificationSettings() {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
    fun isNotificationListenerEnabled(): Boolean {
        val cn = android.content.ComponentName(
            context, com.example.smartdrive.presentation.notification.NotificationListener::class.java
        )
        val flat = cn.flattenToString()
        val enabled = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabled.contains(flat) || enabled.contains(cn.flattenToShortString())
    }

    fun sendTestNavigation() {
        val packet = BlePacket.encodeNavigation(
            title = "350 m",
            duration = "18 min",
            distance = "",
            eta = "2:55 PM",
            directions = "Turn right onto MG Road"
        )
        bleManager.sendData(packet)
    }

    fun sendPhoneBattery() {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        bleManager.sendData(BlePacket.encodePhoneBattery(level, bm.isCharging))
    }

    fun sendTime() {
        bleManager.sendData(BlePacket.encodeTime())
    }
}
