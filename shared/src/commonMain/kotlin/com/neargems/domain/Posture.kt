package com.neargems.domain

import kotlin.math.abs

/** Which way the phone is held up: its long side vertical, or its long side level. */
enum class Orientation(val label: String) { PORTRAIT("Portrait"), LANDSCAPE("Landscape") }

/** Whether a person holds the phone or it is fixed in a mount, a dock or a car display. */
enum class Holding(val label: String) { HAND_HELD("Hand-held"), MOUNTED("Mounted") }

/** How the phone is being used right now. New modes can be added here and read wherever the app decides. */
data class PhonePosture(val orientation: Orientation, val holding: Holding) {
    val label: String get() = "${orientation.label}, ${holding.label.lowercase()}"

    companion object {
        val UNKNOWN = PhonePosture(Orientation.PORTRAIT, Holding.HAND_HELD)
    }
}

/** How long the phone must stay steady, while the vehicle moves, before it counts as mounted. */
const val STEADY_SECONDS_TO_MOUNT = 20

/** Speed above which a steady phone is taken to be in a moving vehicle rather than in a pocket. */
const val MOUNT_SPEED_MPS = 4.0

/**
 * Works out the phone's posture from the gravity reading in device axes (x across, y up the long
 * side, z out of the screen). Portrait when gravity pulls along the long side. A phone is mounted
 * when a car display is connected, or when it has stayed steady for a while as the vehicle moves.
 */
fun classifyPosture(
    gravityX: Float,
    gravityY: Float,
    carConnected: Boolean,
    steadySeconds: Long,
    speedMps: Double,
): PhonePosture {
    val orientation = if (abs(gravityY) >= abs(gravityX)) Orientation.PORTRAIT else Orientation.LANDSCAPE
    val mounted = carConnected || (steadySeconds >= STEADY_SECONDS_TO_MOUNT && speedMps >= MOUNT_SPEED_MPS)
    return PhonePosture(orientation, if (mounted) Holding.MOUNTED else Holding.HAND_HELD)
}
