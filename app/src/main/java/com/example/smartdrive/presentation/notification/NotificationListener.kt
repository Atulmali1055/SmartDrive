package com.example.smartdrive.presentation.notification

import android.app.Notification
import android.content.Context
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.ContactsContract
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telephony.TelephonyCallback
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

    // ---- API 31+ callback ----
    private var telephonyCallback: TelephonyCallback? = null

    // ---- Legacy callback (< API 31) ----
    @Suppress("DEPRECATION")
    private var legacyPhoneStateListener: android.telephony.PhoneStateListener? = null

    override fun onCreate() {
        super.onCreate()
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        registerCallListener()
        ListenerKeepAliveService.start(this)
    }

    override fun onDestroy() {
        unregisterCallListener()
        super.onDestroy()
    }

    private fun registerCallListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleCallState(state)
                }
            }
            telephonyCallback = cb
            telephonyManager.registerTelephonyCallback(mainExecutor, cb)
        } else {
            @Suppress("DEPRECATION")
            val listener = object : android.telephony.PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleCallState(state, phoneNumber)
                }
            }
            legacyPhoneStateListener = listener
            @Suppress("DEPRECATION")
            telephonyManager.listen(listener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun unregisterCallListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
            telephonyCallback = null
        } else {
            @Suppress("DEPRECATION")
            legacyPhoneStateListener?.let {
                @Suppress("DEPRECATION")
                telephonyManager.listen(it, android.telephony.PhoneStateListener.LISTEN_NONE)
            }
            legacyPhoneStateListener = null
        }
    }

    private fun handleCallState(state: Int, phoneNumber: String? = null) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                // API 31+: we don't get the number here; caller info comes via contacts
                // but for now, we can only show "Incoming"
                val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Incoming call"
                           else resolveContactName(phoneNumber) ?: phoneNumber ?: "Incoming call"
                Log.d(TAG, "Ringer start: $name")
                app.bleManager.sendData(BlePacket.encodeRinger(name, true))
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                app.bleManager.sendData(BlePacket.encodeRinger("", false))
            }
        }
    }

    private fun resolveContactName(number: String?): String? {
        if (number.isNullOrBlank()) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Contact lookup failed", e)
            null
        }
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
        try { handleNotification(sbn) } catch (e: Exception) { Log.e(TAG, "handleNotification", e) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == "com.google.android.apps.maps") {
            Log.d(TAG, "Maps notification removed → navigation inactive")
            app.bleManager.sendData(BlePacket.encodeNavigationInactive())
        }
    }

    private fun handleNotification(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        // ---- Google Maps: always forwarded ----
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

        // ---- Other apps: filter ----
        if (!app.settings.isNotificationAllowed(pkg)) {
            Log.d(TAG, "Filtered: $pkg")
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
}