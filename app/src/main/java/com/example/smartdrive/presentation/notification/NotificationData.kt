package com.example.smartdrive.presentation.notification

data class NotificationData(
    val packageName: String,
    val appName: String?,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val timestamp: Long = System.currentTimeMillis()
)