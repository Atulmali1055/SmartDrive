package com.example.smartdrive.presentation.navigation

import java.util.regex.Pattern

data class ParsedNav(
    val title: String = "",        // e.g. "350 m"
    val duration: String = "",     // e.g. "18 min"
    val eta: String = "",          // e.g. "2:55 PM"
    val directions: String = "",   // e.g. "Turn right onto MG Road"
    val distanceMeters: Int = 0
)

object MapsParser {

    fun parse(title: String, text: String, bigText: String): ParsedNav {
        val full = listOfNotNull(title, text, bigText)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()

        // Distance to next turn (from title usually)
        val distRegex = Regex("(\\d+(?:\\.\\d+)?)\\s*(m|km|ft|mi)", RegexOption.IGNORE_CASE)
        val distMatch = distRegex.find(title).takeIf { it != null } ?: distRegex.find(full)
        var distanceMeters = 0
        var titleStr = title
        if (distMatch != null) {
            val value = distMatch.groupValues[1].toDouble()
            val unit = distMatch.groupValues[2].lowercase()
            distanceMeters = when (unit) {
                "km" -> (value * 1000).toInt()
                "mi" -> (value * 1609.34).toInt()
                "ft" -> (value * 0.3048).toInt()
                else -> value.toInt()
            }
            titleStr = "${distMatch.groupValues[1]} ${distMatch.groupValues[2]}"
        }

        // ETA
        val etaRegex = Regex("(\\d{1,2}:\\d{2}\\s*(?:AM|PM)?)", RegexOption.IGNORE_CASE)
        val eta = etaRegex.find(full)?.value ?: ""

        // Duration
        val durRegex = Regex("(\\d+)\\s*(min|hour|hr|h)\\b", RegexOption.IGNORE_CASE)
        val duration = durRegex.find(full)?.let { "${it.groupValues[1]} ${it.groupValues[2]}" } ?: ""

        // Directions
        val directions = text.ifBlank { bigText }.ifBlank { full }

        return ParsedNav(
            title = titleStr,
            duration = duration,
            eta = eta,
            directions = directions,
            distanceMeters = distanceMeters
        )
    }
}