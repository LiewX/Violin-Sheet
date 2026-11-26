package com.violinsheet.core.ink

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InkFormatTest {
    private val points = listOf(
        InkPoint(10.5f, 20.25f, 0.5f, 0),
        InkPoint(-3f, 841.9f, -1f, 16),
        InkPoint(Float.MIN_VALUE, Float.MAX_VALUE, 1f, Int.MAX_VALUE.toLong()),
    )

    @Test fun pointsRoundTripExactly() = assertEquals(points, decodeInkPoints(encodeInkPoints(points)))

    @Test fun noPointsRoundTrip() = assertEquals(emptyList(), decodeInkPoints(encodeInkPoints(emptyList())))

    @Test fun sixteenBytesAPointPlusVersion() = assertEquals(1 + 3 * 16, encodeInkPoints(points).size)

    @Test fun unknownVersionIsRejected() {
        val bytes = encodeInkPoints(points).also { it[0] = 9 }
        assertFailsWith<IllegalArgumentException> { decodeInkPoints(bytes) }
    }

    @Test fun truncatedDataIsRejected() {
        assertFailsWith<IllegalArgumentException> { decodeInkPoints(encodeInkPoints(points).copyOf(20)) }
    }

    private val placement = PagePlacement(pageIndex = 0, pageSize = Size(595f, 842f), scale = 2f, offset = Offset(100f, -50f))

    @Test fun pageToScreenAndBack() {
        val page = Offset(300f, 400f)
        assertEquals(Offset(700f, 750f), placement.toScreen(page))
        assertEquals(page, placement.toPage(placement.toScreen(page)))
    }

    @Test fun screenRectCoversTheScaledPage() =
        assertEquals(Rect(100f, -50f, 100f + 1190f, -50f + 1684f), placement.screenRect)
}
