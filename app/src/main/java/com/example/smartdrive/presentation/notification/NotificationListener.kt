package com.example.smartdrive.presentation.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.smartdrive.presentation.ble.BleManager
import com.example.smartdrive.presentation.ble.BlePacket
import com.example.smartdrive.presentation.navigation.MapsParser

class NotificationListener :
    NotificationListenerService() {

    companion object {

        private const val TAG =
            "SmartDriveNotification"
    }

    private lateinit var bleManager:
            BleManager

    override fun onCreate() {

        super.onCreate()

        bleManager =
            BleManager.get(
                applicationContext,
            )

        Log.d(
            TAG,
            "Notification service started"
        )
    }

    override fun onListenerConnected() {

        super.onListenerConnected()

        Log.d(
            TAG,
            "Notification listener connected"
        )
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification,
    ) {

        try {

            processNotification(sbn)

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Notification error",
                e
            )
        }
    }

    private fun processNotification(
        sbn: StatusBarNotification,
    ) {

        val packageName =
            sbn.packageName

        val notification =
            sbn.notification

        val extras =
            notification.extras

        val title =
            extras
                .getCharSequence(
                    Notification.EXTRA_TITLE
                )
                ?.toString()
                ?: ""

        val text =
            extras
                .getCharSequence(
                    Notification.EXTRA_TEXT
                )
                ?.toString()
                ?: ""

        val bigText =
            extras
                .getCharSequence(
                    Notification.EXTRA_BIG_TEXT
                )
                ?.toString()
                ?: ""

        Log.d(
            TAG,
            "================================"
        )

        Log.d(
            TAG,
            "Package: $packageName"
        )

        Log.d(
            TAG,
            "Title: $title"
        )

        Log.d(
            TAG,
            "Text: $text"
        )

        Log.d(
            TAG,
            "BigText: $bigText"
        )

        Log.d(
            TAG,
            "Time: ${sbn.postTime}"
        )

        Log.d(
            TAG,
            "================================"
        )

        // =================================================
        // GOOGLE MAPS
        // =================================================

        if (
            MapsParser.isGoogleMaps(
                packageName
            )
        ) {

            val navigation =
                MapsParser.parse(
                    title = title,
                    text = text,
                    bigText = bigText
                )

            Log.d(
                TAG,
                "GOOGLE MAPS DETECTED"
            )

            Log.d(
                TAG,
                "Maneuver: ${navigation.maneuver}"
            )

            Log.d(
                TAG,
                "Distance: ${navigation.distanceMeters}"
            )

            Log.d(
                TAG,
                "Instruction: ${navigation.instruction}"
            )

            val packet =
                BlePacket.encodeNavigation(
                    active = true,
                    maneuver =
                        navigation.maneuver.name,
                    distance =
                        navigation.distanceMeters,
                    street =
                        navigation.street,
                    instruction =
                        navigation.instruction,
                    eta =
                        navigation.eta,
                    duration =
                        navigation.duration
                )

            bleManager.sendData(
                packet
            )

            return
        }

        // =================================================
        // OTHER NOTIFICATIONS
        // =================================================

        val appName =
            packageName.substringAfterLast(
                "."
            )

        if (
            title.isBlank() &&
            text.isBlank()
        ) {
            return
        }

        val packet =
            BlePacket.encodeNotification(
                app = appName,
                title = title,
                message = text.ifBlank { bigText }
            )

        bleManager.sendData(
            packet
        )
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
    ) {

        Log.d(
            TAG,
            "Notification removed: ${sbn.packageName}"
        )
    }
}
