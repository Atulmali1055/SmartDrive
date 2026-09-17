package com.example.smartdrive

import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartdrive.presentation.ble.BleDevice
import com.example.smartdrive.presentation.ble.BleManager
import com.example.smartdrive.presentation.ble.BlePacket
import com.example.smartdrive.presentation.ble.ConnectionState
import com.example.smartdrive.presentation.notification.NotificationListener
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

    // ---------------------------------------------------------------
    // Exposed state
    // ---------------------------------------------------------------

    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState
    val discoveredDevices: StateFlow<List<BleDevice>> = bleManager.discoveredDevices
    val notificationFilter: StateFlow<Set<String>> = settings.notificationFilter
    val use24Hour: StateFlow<Boolean> = settings.use24Hour
    val weatherEnabled: StateFlow<Boolean> = settings.weatherEnabled

    private val _sendStatus = MutableStateFlow<String?>(null)
    val sendStatus: StateFlow<String?> = _sendStatus.asStateFlow()

    init {
        // Whenever the connection becomes CONNECTED, sync settings to the device.
        viewModelScope.launch {
            bleManager.connectionState.collect { state ->
                if (state is ConnectionState.CONNECTED) {
                    syncSettingsToDevice()
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // BLE scan / connect / disconnect
    // ---------------------------------------------------------------

    fun scan() {
        viewModelScope.launch { bleManager.startScan() }
    }

    fun stopScan() {
        bleManager.stopScan()
    }

    fun connect(device: BluetoothDevice) {
        bleManager.connectToDevice(device)
    }

    fun disconnect() {
        bleManager.disconnect()
    }

    // ---------------------------------------------------------------
    // Notification listener access
    // ---------------------------------------------------------------

    fun openNotificationSettings() {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * Whether our NotificationListenerService is currently authorized by the user.
     * Uses the platform constant (Settings.Secure.ENABLED_NOTIFICATION_LISTENERS)
     * and matches against both the long and short component name forms — some
     * OEM ROMs store one, some the other.
     */
    fun isNotificationListenerEnabled(): Boolean {
        val cn = ComponentName(context, NotificationListener::class.java)
        val flatLong = cn.flattenToString()
        val flatShort = cn.flattenToShortString()
        val raw = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return raw.contains(flatLong) || raw.contains(flatShort)
    }

    // ---------------------------------------------------------------
    // Settings sync
    // ---------------------------------------------------------------

    /**
     * Push current app settings to the ESP32. Called on every connect.
     *
     * Chronos config packets used here:
     *   0x7C  CF_HR24  → byte[6]: 0 = 24h mode, 1 = 12h mode
     */
    private fun syncSettingsToDevice() {
        val hour24 = settings.use24Hour.value
        val cfg = BlePacket.encodeHour24Config(hour24)
        bleManager.sendData(cfg)
    }

    /**
     * Called from the Settings UI when the user toggles 24h mode.
     * Persists to prefs AND sends to the device immediately.
     */
    fun setUse24Hour(use24: Boolean) {
        settings.setUse24Hour(use24)
        if (bleManager.connectionState.value is ConnectionState.CONNECTED) {
            bleManager.sendData(BlePacket.encodeHour24Config(use24))
        }
    }

    fun setWeatherEnabled(enabled: Boolean) {
        settings.setWeatherEnabled(enabled)
        // Weather enable/disable is a phone-side decision; no BLE packet needed.
        // The ESP32 only receives weather when the phone decides to send it.
    }

    // ---------------------------------------------------------------
    // Quick actions from the Home screen
    // ---------------------------------------------------------------

    fun sendTestNavigation() {
        val packet = BlePacket.encodeNavigation(
            title = "350 m",
            duration = "18 min",
            distance = "",
            eta = "2:55 PM",
            directions = "Turn right onto MG Road",
            speed = ""
        )
        bleManager.sendData(packet)
    }

    fun sendNavigationInactive() {
        bleManager.sendData(BlePacket.encodeNavigationInactive())
    }

    fun sendTime() {
        bleManager.sendData(BlePacket.encodeTime())
    }

    fun sendPhoneBattery() {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        bleManager.sendData(BlePacket.encodePhoneBattery(level, bm.isCharging))
    }
}