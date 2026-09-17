package com.example.smartdrive.presentation.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("smartdrive_settings", Context.MODE_PRIVATE)

    // ---------------------------------------------------------------
    // Notification filter
    // ---------------------------------------------------------------

    /**
     * The set of package names that are allowed to forward notifications.
     *
     * Special values:
     *   - Contains "*"  → allow ALL apps (except Maps which is always allowed)
     *   - Empty set     → deny all apps (except Maps which is always allowed)
     *   - Otherwise     → allow only the listed packages
     *
     * On first run, defaults to [DEFAULT_ALLOWED].
     */
    private val _notificationFilter = MutableStateFlow(loadFilter())
    val notificationFilter: StateFlow<Set<String>> = _notificationFilter.asStateFlow()

    /**
     * Convenience flag: true if the filter is set to allow all apps.
     * Backed by the same storage as [notificationFilter], kept in sync.
     */
    private val _allowAll = MutableStateFlow(loadFilter().contains(ALL_WILDCARD))
    val allowAll: StateFlow<Boolean> = _allowAll.asStateFlow()

    private fun loadFilter(): Set<String> {
        // Use a distinct sentinel: if the key is missing entirely, apply defaults.
        // If the key exists (even as empty set), respect the user's choice.
        if (!prefs.contains(KEY_FILTER)) return DEFAULT_ALLOWED
        return prefs.getStringSet(KEY_FILTER, DEFAULT_ALLOWED) ?: DEFAULT_ALLOWED
    }

    private fun saveFilter(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_FILTER, packages).apply()
        _notificationFilter.value = packages
        _allowAll.value = packages.contains(ALL_WILDCARD)
    }

    // ---- Public mutation API ----

    fun setNotificationFilter(packages: Set<String>) {
        saveFilter(packages)
    }

    fun allowAll() {
        saveFilter(setOf(ALL_WILDCARD))
    }

    /** Deny all except Google Maps (which is bypassed anyway). */
    fun clearFilter() {
        saveFilter(emptySet())
    }

    fun resetToDefaults() {
        prefs.edit().remove(KEY_FILTER).apply()
        _notificationFilter.value = DEFAULT_ALLOWED
        _allowAll.value = false
    }

    // ---- Query API ----

    /**
     * Whether notifications from [packageName] should be forwarded to the ESP32.
     *
     * Google Maps is always allowed so navigation works regardless of filter.
     */
    fun isNotificationAllowed(packageName: String): Boolean {
        // Navigation always gets through
        if (packageName == "com.google.android.apps.maps") return true

        val filter = _notificationFilter.value

        // Allow-all wildcard
        if (filter.contains(ALL_WILDCARD)) return true

        // Explicit package membership
        return filter.contains(packageName)
    }

    // ---------------------------------------------------------------
    // Weather
    // ---------------------------------------------------------------

    private val _weatherEnabled = MutableStateFlow(prefs.getBoolean(KEY_WEATHER, true))
    val weatherEnabled: StateFlow<Boolean> = _weatherEnabled.asStateFlow()

    fun setWeatherEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEATHER, enabled).apply()
        _weatherEnabled.value = enabled
    }

    // ---------------------------------------------------------------
    // Time format
    // ---------------------------------------------------------------

    private val _use24Hour = MutableStateFlow(prefs.getBoolean(KEY_24H, true))
    val use24Hour: StateFlow<Boolean> = _use24Hour.asStateFlow()

    fun setUse24Hour(use24: Boolean) {
        prefs.edit().putBoolean(KEY_24H, use24).apply()
        _use24Hour.value = use24
    }

    // ---------------------------------------------------------------
    // Constants
    // ---------------------------------------------------------------

    companion object {
        private const val KEY_FILTER   = "notif_filter"
        private const val KEY_WEATHER  = "weather_enabled"
        private const val KEY_24H      = "use_24h"

        /** Sentinel that means "allow every app". */
        const val ALL_WILDCARD = "*"

        /**
         * Default set of apps allowed to forward notifications on first launch.
         * Users can toggle individual apps or pick "Allow all" from the UI.
         */
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
}