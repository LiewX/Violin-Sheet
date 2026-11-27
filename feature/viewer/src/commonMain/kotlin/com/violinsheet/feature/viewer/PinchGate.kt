package com.violinsheet.feature.viewer

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/** How much the fingers' spread must change, as a fraction of its starting spread, before two fingers zoom or pan. */
internal const val PINCH_SPREAD_FRACTION = 0.15f

/** How far, in dp, the fingers' centroid must move from where two fingers first touched before they zoom or pan. */
internal const val PINCH_CENTRE_DP = 24

/** The centroid of [positions]. */
internal fun centroidOf(positions: List<Offset>): Offset = positions.reduce { a, b -> a + b } / positions.size.toFloat()

/** The spread of [positions]: the sum of their distances from [centre]. */
internal fun spreadOf(positions: List<Offset>, centre: Offset = centroidOf(positions)): Float =
    positions.sumOf { (it - centre).getDistance().toDouble() }.toFloat()

/**
 * Holds back a two-finger gesture until it is a real pinch, so a resting hand that barely moves doesn't zoom: its
 * spread must change by more than [spreadFraction] of its starting spread, and by more than [spreadSlop] (px, so two
 * contacts landing close together don't engage on jitter), or its centroid move more than [centreSlop] (px). One gate
 * per gesture. Until then, a change in the set of fingers (a third landing) starts the measuring over.
 */
internal class PinchGate(
    private val centreSlop: Float,
    private val spreadSlop: Float,
    private val spreadFraction: Float = PINCH_SPREAD_FRACTION,
) {
    private var fingers: Set<Long>? = null
    private var startSpread = 0f
    private var startCentroid = Offset.Zero
    private var engaged = false

    /**
     * Fed each event's [fingers] (pointer ids), [spread] and [centroid]; answers whether to apply this event's movement.
     * Only events after the one that crosses the threshold apply, so the view doesn't jump by the held-back movement.
     */
    fun shouldApply(fingers: Set<Long>, spread: Float, centroid: Offset): Boolean {
        if (engaged) return true
        if (fingers != this.fingers) {
            this.fingers = fingers
            startSpread = spread
            startCentroid = centroid
            return false
        }
        val spreadChanged = abs(spread - startSpread) > maxOf(startSpread * spreadFraction, spreadSlop)
        val centreMoved = (centroid - startCentroid).getDistance() > centreSlop
        engaged = spreadChanged || centreMoved
        return false
    }
}
