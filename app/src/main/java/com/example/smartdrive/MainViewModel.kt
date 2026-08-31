package com.example.smartdrive

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartdrive.presentation.ble.BleDevice
import com.example.smartdrive.presentation.ble.BleManager
import com.example.smartdrive.presentation.ble.ConnectionState
import com.example.smartdrive.presentation.notification.NotificationListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val context: Context, private val bleManager: BleManager) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState
    val discoveredDevices: StateFlow<List<BleDevice>> = bleManager.discoveredDevices

    private val _sendStatus = MutableStateFlow<String?>(null)
    val sendStatus: StateFlow<String?> = _sendStatus.asStateFlow()

    // Test packet input – default to navigation test packet
    private val _testPacketInput = MutableStateFlow("NAV|1|RIGHT|350|MG Road|Turn right onto MG Road|2:55 PM|18 min")
    val testPacketInput: StateFlow<String> = _testPacketInput.asStateFlow()

    // Notification access state
    private val _isNotificationListenerEnabled = MutableStateFlow(false)
    val isNotificationListenerEnabled: StateFlow<Boolean> = _isNotificationListenerEnabled.asStateFlow()

    private var notificationAccessRequest: (() -> Unit)? = null

    init {
        // NotificationListener now uses BleManager.get() internally
        checkNotificationListenerEnabled()
    }

    fun updateTestPacket(newValue: String) {
        _testPacketInput.value = newValue
    }

    fun scanDevices() {
        viewModelScope.launch {
            bleManager.startScan()
        }
    }

    fun stopScan() {
        bleManager.stopScan()
    }

    fun connectToDevice(device: BluetoothDevice) {
        bleManager.connectToDevice(device)
    }

    fun disconnect() {
        bleManager.disconnect()
    }

    fun sendTestPacket() {
        val packet = _testPacketInput.value
        if (packet.isBlank()) {
            _sendStatus.value = "Packet empty"
            return
        }
        val success = bleManager.sendData(packet)
        _sendStatus.value = if (success) "Sent: $packet" else "Send failed"
        // Clear after 5 seconds
        viewModelScope.launch {
            kotlinx.coroutines.delay(5000)
            _sendStatus.value = null
        }
    }

    fun checkNotificationListenerEnabled(): Boolean {
        val componentName = android.content.ComponentName(
            context,
            NotificationListener::class.java
        )
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        val isEnabled = enabledListeners?.contains(componentName.flattenToString()) == true
        _isNotificationListenerEnabled.value = isEnabled
        return isEnabled
    }

    fun setNotificationAccessRequest(block: () -> Unit) {
        notificationAccessRequest = block
    }

    fun requestNotificationAccess() {
        notificationAccessRequest?.invoke()
    }

    // For testing on emulator: simulate a Google Maps notification
    fun simulateGoogleMapsNotification() {
        // This will be called from UI – we can directly parse and send
        val testNotification = com.example.smartdrive.presentation.notification.NotificationData(
            packageName = "com.google.android.apps.maps",
            appName = "Google Maps",
            title = "350 m",
            text = "Turn right onto MG Road, ETA 2:55 PM, 18 min",
            bigText = null
        )
        val navData = com.example.smartdrive.presentation.navigation.MapsParser.parse(testNotification)
        navData?.let {
            val packet = com.example.smartdrive.presentation.ble.BlePacket.encodeNavigation(
                active = it.active,
                maneuver = it.maneuver.name,
                distance = it.distanceMeters,
                street = it.street,
                instruction = it.instruction,
                eta = it.eta,
                duration = it.duration
            )
            bleManager.sendData(packet)
            _sendStatus.value = "Sent simulated: $packet"
        } ?: run {
            _sendStatus.value = "Failed to parse simulated notification"
        }
    }
}