package com.violinsheet.feature.settings

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColourPickerTest {
    private val presets = listOf(
        0xFF000000, 0xFF1565C0, 0xFFC62828, 0xFF2E7D32, // pen
        0x99FFEB3B, 0x8066BB6A, 0x80F06292, // highlighter
    ).map { it.toInt() }

    private fun assertNear(expected: Int, actual: Int) {
        for (shift in listOf(16, 8, 0)) {
            val diff = abs((expected shr shift and 0xFF) - (actual shr shift and 0xFF))
            assertTrue(diff <= 1, "channel at $shift: expected ${expected.toString(16)} got ${actual.toString(16)}")
        }
    }

    @Test fun presetsRoundTripWithinOnePerChannel() {
        for (argb in presets) assertNear(argb, hsvToArgb(argbToHsv(argb), argb ushr 24))
    }

    @Test fun alphaIsKeptUnchanged() {
        for (alpha in listOf(0x80, 0x99, 0xFF)) {
            assertEquals(alpha, hsvToArgb(argbToHsv(0xFF1565C0.toInt()), alpha) ushr 24)
        }
    }

    @Test fun greyAndBlackDoNotThrow() {
        for (argb in listOf(0xFF000000, 0xFFFFFFFF, 0xFF808080).map { it.toInt() }) {
            assertEquals(argb, hsvToArgb(argbToHsv(argb), 0xFF))
        }
    }
}
