package com.example.smartdrive.presentation.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("smartdrive_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USE_24_HOUR = "use_24_hour"
        private const val KEY_WEATHER_ENABLED = "weather_enabled"
        private const val KEY_ALLOWED_APPS = "allowed_apps"

        val DEFAULT_ALLOWED: Set<String> = setOf(
            // Messaging
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            // Social
            "com.snapchat.android",
            "com.instagram.android",
            "com.facebook.orca",
            "com.discord",
            "com.twitter.android",
            "com.reddit.frontpage",
            "com.linkedin.android",
            // Email
            "com.google.android.gm",
            "com.microsoft.office.outlook",
            // Work
            "com.microsoft.teams",
            "com.microsoft.office.officehubrow",
            "com.skype.raider"
        )
    }

    private val _use24Hour = MutableStateFlow(prefs.getBoolean(KEY_USE_24_HOUR, false))
    val use24Hour: StateFlow<Boolean> = _use24Hour.asStateFlow()

    private val _weatherEnabled = MutableStateFlow(prefs.getBoolean(KEY_WEATHER_ENABLED, false))
    val weatherEnabled: StateFlow<Boolean> = _weatherEnabled.asStateFlow()

    private val _notificationFilter = MutableStateFlow(
        prefs.getStringSet(KEY_ALLOWED_APPS, DEFAULT_ALLOWED) ?: DEFAULT_ALLOWED
    )
    val notificationFilter: StateFlow<Set<String>> = _notificationFilter.asStateFlow()

    fun setUse24Hour(value: Boolean) {
        prefs.edit().putBoolean(KEY_USE_24_HOUR, value).apply()
        _use24Hour.value = value
    }

    fun setWeatherEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_WEATHER_ENABLED, value).apply()
        _weatherEnabled.value = value
    }

    fun isNotificationAllowed(packageName: String): Boolean {
        return _notificationFilter.value.contains(packageName)
    }

    fun setNotificationAllowed(packageName: String, allowed: Boolean) {
        val current = _notificationFilter.value.toMutableSet()
        if (allowed) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        prefs.edit().putStringSet(KEY_ALLOWED_APPS, current).apply()
        _notificationFilter.value = current
    }

    fun clearNotificationFilter() {
        prefs.edit().remove(KEY_ALLOWED_APPS).apply()
        _notificationFilter.value = DEFAULT_ALLOWED
    }
}