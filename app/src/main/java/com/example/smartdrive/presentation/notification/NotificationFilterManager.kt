package com.example.smartdrive.presentation.notification

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.example.smartdrive.presentation.settings.AppSettings

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystem: Boolean
)

class NotificationFilterManager(
    private val context: Context,
    private val settings: AppSettings
) {

    /** Load all user-installed apps (non-system) that can post notifications. */
    fun loadInstalledApps(): List<InstalledApp> {
        val pm = context.packageManager
        val apps = mutableListOf<InstalledApp>()

        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (info in packages) {
            // Skip our own app
            if (info.packageName == context.packageName) continue

            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            // Only include apps that have a launcher intent (i.e., user-visible)
            val launchIntent = pm.getLaunchIntentForPackage(info.packageName) ?: continue

            apps += InstalledApp(
                packageName = info.packageName,
                label = info.loadLabel(pm).toString(),
                icon = info.loadIcon(pm),
                isSystem = isSystem
            )
        }

        return apps.sortedBy { it.label.lowercase() }
    }

    fun isAllowed(packageName: String): Boolean = settings.isNotificationAllowed(packageName)

    fun setAllowed(packageName: String, allowed: Boolean) {
        val current = settings.notificationFilter.value.toMutableSet()
        if (allowed) current.add(packageName) else current.remove(packageName)
        settings.setNotificationFilter(current)
    }
}
