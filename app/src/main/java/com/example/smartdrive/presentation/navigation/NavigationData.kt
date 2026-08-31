package com.example.smartdrive.presentation.navigation

/**
 * Represents the type of movement for a navigation step.
 */
enum class Maneuver {
    U_TURN,
    SHARP_LEFT,
    SHARP_RIGHT,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    LEFT,
    RIGHT,
    ROUNDABOUT,
    STRAIGHT,
    ARRIVED,
    NONE
}

/**
 * Represents navigation information extracted from a navigation source
 * such as Google Maps.
 */
data class NavigationData(
    val active: Boolean = false,
    val maneuver: Maneuver = Maneuver.NONE,
    val distanceMeters: Int = 0,
    val street: String = "",
    val instruction: String = "",
    val eta: String = "",
    val duration: String = "",
    val destination: String = ""
)
