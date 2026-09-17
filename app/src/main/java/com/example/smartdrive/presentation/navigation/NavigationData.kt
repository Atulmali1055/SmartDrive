package com.example.smartdrive.presentation.navigation

enum class Maneuver {
    NONE, STRAIGHT, LEFT, RIGHT, SLIGHT_LEFT, SLIGHT_RIGHT,
    SHARP_LEFT, SHARP_RIGHT, U_TURN, ROUNDABOUT, ARRIVED
}

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