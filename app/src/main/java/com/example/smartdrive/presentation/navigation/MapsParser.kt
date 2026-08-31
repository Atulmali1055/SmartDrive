package com.example.smartdrive.presentation.navigation

import android.util.Log
import com.example.smartdrive.presentation.notification.NotificationData
import java.util.regex.Pattern

object MapsParser {

    private const val TAG = "MapsParser"
    private const val MAPS_PACKAGE = "com.google.android.apps.maps"

    fun isGoogleMaps(packageName: String): Boolean {
        return packageName == MAPS_PACKAGE
    }

    /**
     * Parse a notification from Google Maps into NavigationData.
     */
    fun parse(notification: NotificationData): NavigationData? {
        return parse(
            notification.title ?: "",
            notification.text ?: "",
            notification.bigText ?: ""
        )
    }

    fun parse(title: String, text: String, bigText: String): NavigationData {
        // Combine all text parts
        val fullText = buildString {
            if (title.isNotEmpty()) append(title).append(" ")
            if (text.isNotEmpty()) append(text).append(" ")
            if (bigText.isNotEmpty()) append(bigText)
        }.trim()

        if (fullText.isEmpty()) return NavigationData()

        return try {
            // Extract distance – first number followed by m/km/mi
            val distancePattern = Pattern.compile("(\\d+)\\s*(m|km|mi)", Pattern.CASE_INSENSITIVE)
            val distMatcher = distancePattern.matcher(fullText)
            var distance = 0
            var distanceUnit = "m"
            if (distMatcher.find()) {
                distance = distMatcher.group(1).toInt()
                distanceUnit = distMatcher.group(2).lowercase()
                if (distanceUnit == "km") distance *= 1000
                else if (distanceUnit == "mi") distance = (distance * 1609.34).toInt()
            }

            // Maneuver detection
            val maneuver = when {
                fullText.contains(Regex("\\b(left|turn left|bear left)\\b", RegexOption.IGNORE_CASE)) -> Maneuver.LEFT
                fullText.contains(Regex("\\b(right|turn right|bear right)\\b", RegexOption.IGNORE_CASE)) -> Maneuver.RIGHT
                fullText.contains(Regex("\\b(sharp left)\\b", RegexOption.IGNORE_CASE)) -> Maneuver.SHARP_LEFT
                fullText.contains(Regex("\\b(sharp right)\\b", RegexOption.IGNORE_CASE)) -> Maneuver.SHARP_RIGHT
                fullText.contains(Regex("\\b(slight left)\\b", RegexOption.IGNORE_CASE)) -> Maneuver.SLIGHT_LEFT
                fullText.contains(Regex("\\b(slight right)\\b", RegexOption.IGNORE_CASE)) -> Maneuver.SLIGHT_RIGHT
                fullText.contains(Regex("\\b(uturn|u-turn|make a u-turn)\\b", RegexOption.IGNORE_CASE)) -> Maneuver.U_TURN
                fullText.contains(Regex("\\b(roundabout|rotary)\\b", RegexOption.IGNORE_CASE)) -> Maneuver.ROUNDABOUT
                fullText.contains(Regex("\\b(arrive|destination|you have arrived)\\b", RegexOption.IGNORE_CASE)) -> Maneuver.ARRIVED
                fullText.contains(Regex("\\b(go straight|continue straight)\\b", RegexOption.IGNORE_CASE)) -> Maneuver.STRAIGHT
                else -> Maneuver.STRAIGHT // default
            }

            // Extract street – often appears after "onto" or "on"
            val streetPattern = Pattern.compile("(?:onto|on)\\s+([A-Za-z0-9\\s.,'-]+)", Pattern.CASE_INSENSITIVE)
            val streetMatcher = streetPattern.matcher(fullText)
            var street = ""
            if (streetMatcher.find()) {
                street = streetMatcher.group(1).trim()
            }
            // Extract destination – look for "Arrive at" or "Destination"
            val destPattern = Pattern.compile("(?:Arrive at|Destination)\\s+([A-Za-z0-9\\s.,'-]+)", Pattern.CASE_INSENSITIVE)
            val destMatcher = destPattern.matcher(fullText)
            var destination = ""
            if (destMatcher.find()) {
                destination = destMatcher.group(1).trim()
            }
            // Instruction – the full text is the instruction
            val instruction = fullText

            // ETA – look for time like "2:55 PM" or "14:55"
            val etaPattern = Pattern.compile("(\\d{1,2}:\\d{2}\\s*(?:AM|PM)?)", Pattern.CASE_INSENSITIVE)
            val etaMatcher = etaPattern.matcher(fullText)
            var eta = ""
            if (etaMatcher.find()) {
                eta = etaMatcher.group(1)
            }

            // Duration – look for "X min" or "X hour"
            val durationPattern = Pattern.compile("(\\d+)\\s*(min|hour|h)", Pattern.CASE_INSENSITIVE)
            val durationMatcher = durationPattern.matcher(fullText)
            var duration = ""
            if (durationMatcher.find()) {
                duration = durationMatcher.group(1) + " " + durationMatcher.group(2)
            }

            NavigationData(
                active = true,
                maneuver = maneuver,
                distanceMeters = distance,
                street = street,
                instruction = instruction,
                eta = eta,
                duration = duration,
                destination = destination
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing notification", e)
            NavigationData()
        }
    }
}
