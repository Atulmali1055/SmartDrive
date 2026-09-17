package com.example.smartdrive.presentation.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("smartdrive_settings", Context.MODE_PRIVATE)

    private val _notificationFilter = MutableStateFlow(loadFilter())
    val notificationFilter: StateFlow<Set<String>> = _notificationFilter

    private val _weatherEnabled = MutableStateFlow(prefs.getBoolean(KEY_WEATHER, true))
    val weatherEnabled: StateFlow<Boolean> = _weatherEnabled

    private val _use24Hour = MutableStateFlow(prefs.getBoolean(KEY_24H, true))
    val use24Hour: StateFlow<Boolean> = _use24Hour

    // ---- Notification filter ----
    // Default: allow common apps. Empty set = allow all.
    private fun loadFilter(): Set<String> {
        val stored = prefs.getStringSet(KEY_FILTER, null) ?: return DEFAULT_ALLOWED
        return stored
    }

    fun setNotificationFilter(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_FILTER, packages).apply()
        _notificationFilter.value = packages
    }

    fun isNotificationAllowed(pkg: String): Boolean {
        val filter = _notificationFilter.value
        // If filter contains "*", allow all; if empty, allow none except maps
        if (filter.contains("*")) return true
        if (pkg == "com.google.android.apps.maps") return true // navigation always
        return filter.contains(pkg)
    }

    // ---- Weather ----
    fun setWeatherEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEATHER, enabled).apply()
        _weatherEnabled.value = enabled
    }

    // ---- Time format ----
    fun setUse24Hour(use24: Boolean) {
        prefs.edit().putBoolean(KEY_24H, use24).apply()
        _use24Hour.value = use24
    }

    companion object {
        private const val KEY_FILTER = "notif_filter"
        private const val KEY_WEATHER = "weather_enabled"
        private const val KEY_24H = "use_24h"

        // Sensible defaults - the apps most users want
        val DEFAULT_ALLOWED: Set<String> = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "com.google.android.apps.messaging",
            "com.google.android.gm",
            "com.facebook.orca",
            "com.instagram.android"
        )
    }
}
