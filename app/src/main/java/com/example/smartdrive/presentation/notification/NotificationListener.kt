package com.example.smartdrive.presentation.notification

import android.app.Notification
import android.content.Context
import android.os.BatteryManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import com.example.smartdrive.SmartDriveApplication
import com.example.smartdrive.presentation.ble.BlePacket
import com.example.smartdrive.presentation.navigation.MapsParser

class NotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "SmartDriveNotif"
    }

    private val app get() = application as SmartDriveApplication
    private lateinit var telephonyManager: TelephonyManager

    @Suppress("DEPRECATION")
    private val phoneStateListener = object : PhoneStateListener() {
        @Deprecated("Deprecated in Java")
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> {
                    val name = phoneNumber ?: "Unknown"
                    app.bleManager.sendData(BlePacket.encodeRinger(name, true))
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    app.bleManager.sendData(BlePacket.encodeRinger("", false))
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        @Suppress("DEPRECATION")
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    override fun onDestroy() {
        @Suppress("DEPRECATION")
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Listener connected")
        sendTimeAndBattery()
    }

    private fun sendTimeAndBattery() {
        app.bleManager.sendData(BlePacket.encodeTime())
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        app.bleManager.sendData(BlePacket.encodePhoneBattery(level, bm.isCharging))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            handleNotification(sbn)
        } catch (e: Exception) {
            Log.e(TAG, "handleNotification error", e)
        }
    }

    private fun handleNotification(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        // ==== GOOGLE MAPS: always forwarded, bypasses filter ====
        if (pkg == "com.google.android.apps.maps") {
            val nav = MapsParser.parse(title, text, bigText)
            if (nav.distanceMeters == 0 && text.contains("Arrived", ignoreCase = true)) {
                app.bleManager.sendData(BlePacket.encodeNavigationInactive())
                return
            }
            val packet = BlePacket.encodeNavigation(
                title = nav.title.ifBlank { "${nav.distanceMeters} m" },
                duration = nav.duration,
                distance = "",
                eta = nav.eta,
                directions = nav.directions.ifBlank { text.ifBlank { bigText } },
                speed = ""
            )
            app.bleManager.sendData(packet)
            sendTimeAndBattery()
            return
        }

        // ==== OTHER APPS: filter check ====
        if (!app.settings.isNotificationAllowed(pkg)) {
            Log.d(TAG, "Filtered out: $pkg")
            return
        }

        if (title.isBlank() && text.isBlank() && bigText.isBlank()) return

        val icon = iconForPackage(pkg)
        val message = if (title.isNotBlank())
            "$title: ${text.ifBlank { bigText }}"
        else text.ifBlank { bigText }

        app.bleManager.sendData(BlePacket.encodeNotification(icon, message))
    }

    private fun iconForPackage(pkg: String): Int = when (pkg) {
        "com.whatsapp", "com.whatsapp.w4b" -> BlePacket.ICON_WHATSAPP
        "com.google.android.gm" -> BlePacket.ICON_GMAIL
        "com.facebook.orca" -> BlePacket.ICON_MESSENGER
        "com.instagram.android" -> BlePacket.ICON_INSTAGRAM
        "org.telegram.messenger" -> BlePacket.ICON_TELEGRAM
        "com.skype.raider" -> BlePacket.ICON_SKYPE
        "com.google.android.apps.messaging" -> BlePacket.ICON_MESSAGE
        else -> BlePacket.ICON_DEFAULT
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}
}
