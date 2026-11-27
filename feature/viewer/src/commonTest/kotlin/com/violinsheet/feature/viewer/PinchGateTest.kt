package com.violinsheet.feature.viewer

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PinchGateTest {
    private val two = setOf(1L, 2L)
    private val start = Offset(500f, 500f)

    /** A gate at density 1: 24 dp is 24 px; touch slop 8 px. */
    private fun gate() = PinchGate(centreSlop = 24f, spreadSlop = 8f)

    @Test fun spreadChangeOfExactlyTheFractionChangesNothing() {
        val gate = gate()
        gate.shouldApply(two, spread = 200f, centroid = start)
        assertFalse(gate.shouldApply(two, spread = 230f, centroid = start))
        assertFalse(gate.shouldApply(two, spread = 230f, centroid = start))
        gate.shouldApply(two, spread = 231f, centroid = start)
        assertTrue(gate.shouldApply(two, spread = 231f, centroid = start))
    }

    @Test fun centroidMoveOfExactlyTheSlopChangesNothing() {
        val gate = gate()
        gate.shouldApply(two, spread = 100f, centroid = start)
        assertFalse(gate.shouldApply(two, spread = 100f, centroid = start + Offset(24f, 0f)))
        assertFalse(gate.shouldApply(two, spread = 100f, centroid = start + Offset(24f, 0f)))
        gate.shouldApply(two, spread = 100f, centroid = start + Offset(25f, 0f))
        assertTrue(gate.shouldApply(two, spread = 100f, centroid = start + Offset(25f, 0f)))
    }

    @Test fun contactsLandingCloseTogetherNeedMoreThanTheTouchSlop() {
        val gate = gate()
        gate.shouldApply(two, spread = 20f, centroid = start)
        // 30 % more spread, but only 6 px: jitter of two nearby contacts (a palm), not a pinch.
        assertFalse(gate.shouldApply(two, spread = 26f, centroid = start))
        assertFalse(gate.shouldApply(two, spread = 26f, centroid = start))
        gate.shouldApply(two, spread = 30f, centroid = start)
        assertTrue(gate.shouldApply(two, spread = 30f, centroid = start))
    }

    @Test fun spreadChangeUnderTheFractionChangesNothing() {
        val gate = gate()
        gate.shouldApply(two, spread = 100f, centroid = start)
        assertFalse(gate.shouldApply(two, spread = 110f, centroid = start))
        assertFalse(gate.shouldApply(two, spread = 110f, centroid = start))
    }

    @Test fun spreadChangeOverTheFractionEngages() {
        val gate = gate()
        gate.shouldApply(two, spread = 100f, centroid = start)
        gate.shouldApply(two, spread = 120f, centroid = start)
        assertTrue(gate.shouldApply(two, spread = 125f, centroid = start))
    }

    @Test fun pinchingCloserEngagesToo() {
        val gate = gate()
        gate.shouldApply(two, spread = 100f, centroid = start)
        gate.shouldApply(two, spread = 80f, centroid = start)
        assertTrue(gate.shouldApply(two, spread = 75f, centroid = start))
    }

    @Test fun centroidMoveUnderTheSlopChangesNothing() {
        val gate = gate()
        gate.shouldApply(two, spread = 100f, centroid = start)
        assertFalse(gate.shouldApply(two, spread = 100f, centroid = start + Offset(20f, 0f)))
        assertFalse(gate.shouldApply(two, spread = 100f, centroid = start + Offset(0f, 20f)))
    }

    @Test fun centroidMoveOverTheSlopEngages() {
        val gate = gate()
        gate.shouldApply(two, spread = 100f, centroid = start)
        gate.shouldApply(two, spread = 100f, centroid = start + Offset(30f, 0f))
        assertTrue(gate.shouldApply(two, spread = 100f, centroid = start + Offset(31f, 0f)))
    }

    @Test fun onlyMovementAfterTheThresholdApplies() {
        val gate = gate()
        assertFalse(gate.shouldApply(two, spread = 100f, centroid = start))
        // The event that crosses the threshold isn't applied (no jump by the movement up to it) ...
        assertFalse(gate.shouldApply(two, spread = 130f, centroid = start))
        // ... every later one is, for the rest of the gesture, even if the fingers go back near the start.
        assertTrue(gate.shouldApply(two, spread = 140f, centroid = start))
        assertTrue(gate.shouldApply(two, spread = 100f, centroid = start))
    }

    @Test fun aThirdFingerRebaselines() {
        val gate = gate()
        gate.shouldApply(two, spread = 100f, centroid = start)
        // The third finger shifts spread and centroid at once; that is not a pinch.
        assertFalse(gate.shouldApply(setOf(1L, 2L, 3L), spread = 200f, centroid = start + Offset(100f, 0f)))
        assertFalse(gate.shouldApply(setOf(1L, 2L, 3L), spread = 210f, centroid = start + Offset(110f, 0f)))
        gate.shouldApply(setOf(1L, 2L, 3L), spread = 250f, centroid = start + Offset(100f, 0f))
        assertTrue(gate.shouldApply(setOf(1L, 2L, 3L), spread = 260f, centroid = start + Offset(100f, 0f)))
    }
}
