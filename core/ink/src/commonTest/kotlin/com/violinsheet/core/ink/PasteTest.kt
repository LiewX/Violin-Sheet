package com.violinsheet.core.ink

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PasteTest {
    private val page = Size(600f, 800f)

    private fun stroke(vararg xy: Float, size: Float = 2f) =
        InkStroke(1, 0, 0xFF000000.toInt(), size, xy.toList().chunked(2).map { InkPoint(it[0], it[1], 1f, 0) }, StrokeTool.Pen)

    private fun inside(s: InkStroke) = inkBounds(listOf(s)).let { it.left >= -0.01f && it.top >= -0.01f && it.right <= page.width + 0.01f && it.bottom <= page.height + 0.01f }

    @Test
    fun offsetIsAddedInsideThePage() {
        val s = stroke(100f, 100f, 150f, 120f)
        val t = pastePlacement(inkBounds(listOf(s)), page, Offset(PASTE_STEP, PASTE_STEP))
        assertEquals(Offset(112f, 112f), t.apply(Offset(100f, 100f)))
        assertEquals(1f, t.scale)
    }

    @Test
    fun noOffsetKeepsPosition() {
        val s = stroke(100f, 100f, 150f, 120f)
        assertTrue(pastePlacement(inkBounds(listOf(s)), page, Offset.Zero).isIdentity)
    }

    @Test
    fun crossingTheRightOrBottomEdgeIsMovedInside() {
        val s = stroke(500f, 700f, 599f, 799f)
        val moved = pastePlacement(inkBounds(listOf(s)), page, Offset(24f, 24f)).apply(s)
        assertTrue(inside(moved))
        assertEquals(page.width, inkBounds(listOf(moved)).right, 0.01f)
        assertEquals(page.height, inkBounds(listOf(moved)).bottom, 0.01f)
    }

    @Test
    fun inkBiggerThanThePageIsScaledToFit() {
        val s = stroke(-100f, -50f, 900f, 100f, size = 10f)
        val moved = pastePlacement(inkBounds(listOf(s)), page, Offset(12f, 12f)).apply(s)
        assertTrue(inside(moved))
        assertEquals(10f * page.width / 1010f, moved.size, 0.001f)
    }

    @Test
    fun rotatedFrameKeepsItsAngle() {
        val s = stroke(550f, 750f, 590f, 790f)
        val frame = SelectionFrame(Offset(570f, 770f), Size(30f, 30f), 90f)
        val t = pastePlacement(inkBounds(listOf(s)), page, Offset(12f, 12f))
        assertEquals(90f, frame.transformed(t).angleDegrees)
    }
}
