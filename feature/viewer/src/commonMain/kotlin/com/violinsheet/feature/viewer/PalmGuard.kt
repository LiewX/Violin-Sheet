package com.violinsheet.feature.viewer

/** How long after the pen was last near the screen a finger touch still counts as a resting palm, in milliseconds. */
private const val PALM_MILLIS = 300L

/**
 * Tells a resting palm from a navigating finger, by when the pen was last seen: hovering (the S Pen is detected about
 * a centimetre above the glass) or touching. Times are pointer event uptimes in milliseconds.
 */
internal class PalmGuard(private val palmMillis: Long = PALM_MILLIS) {
    private var lastPenMillis: Long? = null

    fun penSeen(atMillis: Long) {
        lastPenMillis = atMillis
    }

    /** A finger touching down at [fingerDownMillis] is a palm if the pen was near shortly before. */
    fun isPalm(fingerDownMillis: Long): Boolean = lastPenMillis?.let { fingerDownMillis - it < palmMillis } ?: false

    /** The pen showed up during a finger gesture that began at [gestureStartMillis]: that touch was a palm after all. */
    fun penSeenSince(gestureStartMillis: Long): Boolean = lastPenMillis?.let { it >= gestureStartMillis } ?: false
}
