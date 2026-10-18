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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    // Tracks the pending auto-clear job so we can cancel it if a new
    // status is set before the previous one expires.
    private var sendStatusClearJob: Job? = null

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
     *
     * Note: Settings.Secure.ENABLED_NOTIFICATION_LISTENERS resolves to the same
     * string we use here ("enabled_notification_listeners"). We use the literal
     * because the platform constant symbol doesn't resolve reliably in every
     * project setup (name collision with other Settings symbols in scope).
     *
     * Matches both the long and short component-name forms — some OEM ROMs
     * store one, some the other.
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
        bleManager.sendData(BlePacket.encodeHour24Config(hour24))
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
    }

    // ---------------------------------------------------------------
    // Quick actions from the Home screen
    // ---------------------------------------------------------------

    fun sendTestNavigation() {
        if (bleManager.connectionState.value !is ConnectionState.CONNECTED) {
            setSendStatus("Not connected")
            return
        }
        val packet = BlePacket.encodeNavigation(
            title = "350 m",
            duration = "18 min",
            distance = "",
            eta = "2:55 PM",
            directions = "Turn right onto MG Road",
            speed = ""
        )
        val ok = bleManager.sendData(packet)
        setSendStatus(if (ok) "Test navigation sent" else "Send failed")
    }

    fun sendNavigationInactive() {
        if (bleManager.connectionState.value !is ConnectionState.CONNECTED) {
            setSendStatus("Not connected")
            return
        }
        val ok = bleManager.sendData(BlePacket.encodeNavigationInactive())
        setSendStatus(if (ok) "Navigation stopped on device" else "Send failed")
    }

    fun sendTime() {
        if (bleManager.connectionState.value !is ConnectionState.CONNECTED) {
            setSendStatus("Not connected")
            return
        }
        val ok = bleManager.sendData(BlePacket.encodeTime())
        setSendStatus(if (ok) "Time sent" else "Send failed")
    }

    fun sendPhoneBattery() {
        if (bleManager.connectionState.value !is ConnectionState.CONNECTED) {
            setSendStatus("Not connected")
            return
        }
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val ok = bleManager.sendData(BlePacket.encodePhoneBattery(level, bm.isCharging))
        setSendStatus(if (ok) "Battery $level% sent" else "Send failed")
    }

    // ---------------------------------------------------------------
    // Send-status helpers
    // ---------------------------------------------------------------

    /**
     * Set the current send-status message and auto-clear it after
     * [AUTO_CLEAR_MS] milliseconds. If another status arrives before the
     * timeout, the previous timer is cancelled so the newer message gets
     * the full display duration.
     */
    private fun setSendStatus(message: String) {
        _sendStatus.value = message
        sendStatusClearJob?.cancel()
        sendStatusClearJob = viewModelScope.launch {
            delay(AUTO_CLEAR_MS)
            _sendStatus.value = null
        }
    }

    /** Manually clear the status (e.g., when the user navigates away). */
    fun clearSendStatus() {
        sendStatusClearJob?.cancel()
        sendStatusClearJob = null
        _sendStatus.value = null
    }

    override fun onCleared() {
        sendStatusClearJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val AUTO_CLEAR_MS = 3_000L
    }
}