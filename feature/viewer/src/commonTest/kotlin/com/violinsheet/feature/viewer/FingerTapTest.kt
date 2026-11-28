package com.violinsheet.feature.viewer

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FingerTapTest {
    private fun tap(start: Offset, drag: Offset = Offset.Zero, durationMillis: Long = 100) =
        fingerTapOf(start, drag, width = 1000f, tapSlop = 10f, durationMillis = durationMillis)

    @Test fun quickTapInTheMiddleIsAMiddleTap() = assertEquals(FingerTap.Middle, tap(Offset(500f, 500f)))

    @Test fun quickTapAtTheRightEdgeTurnsForward() = assertEquals(FingerTap.Edge(1), tap(Offset(950f, 500f), Offset(2f, 1f)))

    @Test fun quickTapAtTheLeftEdgeTurnsBack() = assertEquals(FingerTap.Edge(-1), tap(Offset(50f, 500f)))

    @Test fun justInsideTheEdgeBandIsTheMiddle() {
        assertEquals(FingerTap.Middle, tap(Offset(200f, 500f)))
        assertEquals(FingerTap.Middle, tap(Offset(800f, 500f)))
    }

    @Test fun aTapUpTo250MillisCounts() = assertEquals(FingerTap.Middle, tap(Offset(500f, 500f), durationMillis = 250))

    @Test fun aTouchLongerThan250MillisIsNoTap() {
        assertNull(tap(Offset(500f, 500f), durationMillis = 251))
        assertNull(tap(Offset(950f, 500f), durationMillis = 400))
    }

    @Test fun moveUpToTheSlopStillTaps() = assertEquals(FingerTap.Middle, tap(Offset(500f, 500f), Offset(6f, 8f)))

    @Test fun moveBeyondTheSlopIsNoTap() {
        assertNull(tap(Offset(500f, 500f), Offset(8f, 8f)))
        assertNull(tap(Offset(950f, 500f), Offset(-50f, 0f)))
    }
}
