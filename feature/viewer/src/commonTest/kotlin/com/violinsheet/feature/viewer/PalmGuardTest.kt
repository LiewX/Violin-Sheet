package com.violinsheet.feature.viewer

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PalmGuardTest {
    @Test fun fingerWithNoPenAroundNavigates() = assertFalse(PalmGuard().isPalm(fingerDownMillis = 10_000))

    @Test fun fingerSoonAfterPenIsAPalm() {
        val guard = PalmGuard(palmMillis = 600)
        guard.penSeen(10_000)
        assertTrue(guard.isPalm(10_500))
    }

    @Test fun fingerLongAfterPenNavigates() {
        val guard = PalmGuard(palmMillis = 600)
        guard.penSeen(10_000)
        assertFalse(guard.isPalm(10_700))
    }

    @Test fun penArrivingDuringAGestureMarksItAPalm() {
        val guard = PalmGuard()
        guard.penSeen(9_000)
        assertFalse(guard.penSeenSince(gestureStartMillis = 10_000))
        guard.penSeen(10_200) // the pen comes into hover range while the palm rests
        assertTrue(guard.penSeenSince(gestureStartMillis = 10_000))
    }
}
