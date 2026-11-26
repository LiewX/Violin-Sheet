package com.violinsheet.core.ink

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ErasingTest {
    /** A straight stroke along y = 0 from x = 0 to x = [length], a point every 1 pt, 1 pt thick. */
    private fun line(length: Int = 100, size: Float = 1f) =
        InkStroke(1, 0, 0, size, List(length + 1) { InkPoint(it.toFloat(), 0f, 0.5f, it.toLong()) }, StrokeTool.Pen)

    @Test fun eraserInTheMiddleCutsTheStrokeInTwo() {
        val pieces = line().cutBy(Offset(50f, 0f), radius = 5f)!!
        assertEquals(2, pieces.size)
        // The ink ends where it meets the eraser: radius 5 plus half the stroke's size.
        assertEquals(44.5f, pieces[0].last().x, 0.01f)
        assertEquals(55.5f, pieces[1].first().x, 0.01f)
        assertEquals(0f, pieces[0].first().x)
        assertEquals(100f, pieces[1].last().x)
    }

    @Test fun eraserAtTheEndLeavesOnePiece() {
        val pieces = line().cutBy(Offset(100f, 0f), radius = 5f)!!
        assertEquals(1, pieces.size)
        assertEquals(94.5f, pieces[0].last().x, 0.01f)
    }

    @Test fun eraserCoveringTheWholeStrokeLeavesNothing() {
        assertEquals(emptyList(), line(length = 4).cutBy(Offset(2f, 0f), radius = 10f))
    }

    @Test fun eraserThatMissesLeavesTheStrokeAlone() {
        assertNull(line().cutBy(Offset(50f, 20f), radius = 5f))
        assertFalse(line().touchedBy(Offset(50f, 20f), radius = 5f))
    }

    @Test fun piecesTooShortToSeeAreDropped() {
        // Eraser ends 0.3 pt short of the stroke's start: that sliver goes.
        val pieces = line().cutBy(Offset(6.8f, 0f), radius = 6f)!!
        assertEquals(1, pieces.size)
        assertTrue(pieces[0].first().x > 7f)
    }

    @Test fun eraserBetweenTwoFarApartPointsStillCuts() {
        val stroke = InkStroke(1, 0, 0, 1f, listOf(InkPoint(0f, 0f, -1f, 0), InkPoint(100f, 0f, -1f, 10)), StrokeTool.Pen)
        val pieces = stroke.cutBy(Offset(50f, 0f), radius = 5f)!!
        assertEquals(2, pieces.size)
        // Time is interpolated along the cut segment, so it never goes backwards.
        assertEquals(listOf(0L, 4L), pieces[0].map { it.elapsedMillis })
        assertEquals(listOf(5L, 10L), pieces[1].map { it.elapsedMillis })
    }

    @Test fun cuttingAPieceAtTheSameSpotAgainChangesNothing() {
        val pieces = line().cutBy(Offset(50f, 0f), radius = 5f)!!
        for (points in pieces) assertNull(line().copy(points = points).cutBy(Offset(50f, 0f), radius = 5f))
    }

    @Test fun strokeHitTestReachesTheInkNotJustTheLine() {
        val thick = line(size = 4f)
        assertTrue(thick.touchedBy(Offset(50f, 6.9f), radius = 5f)) // 5 + 2 = 7 reach
        assertFalse(thick.touchedBy(Offset(50f, 7.1f), radius = 5f))
        // Between two far-apart points.
        val sparse = InkStroke(1, 0, 0, 1f, listOf(InkPoint(0f, 0f, -1f, 0), InkPoint(100f, 0f, -1f, 10)), StrokeTool.Pen)
        assertTrue(sparse.touchedBy(Offset(50f, 3f), radius = 5f))
    }

    @Test fun singlePointStrokeIsErasedWholeOrNotAtAll() {
        val dot = InkStroke(1, 0, 0, 2f, listOf(InkPoint(10f, 10f, 0.5f, 0)), StrokeTool.Pen)
        assertEquals(emptyList(), dot.cutBy(Offset(12f, 10f), radius = 2f))
        assertNull(dot.cutBy(Offset(20f, 10f), radius = 2f))
    }

    @Test fun eraserPathFillsGapsBetweenSamples() {
        assertEquals(listOf(Offset(5f, 5f)), eraserPath(null, Offset(5f, 5f), step = 2f))
        val path = eraserPath(Offset(0f, 0f), Offset(10f, 0f), step = 2f)
        assertEquals(5, path.size)
        assertEquals(Offset(10f, 0f), path.last())
    }

    @Test fun heldEraseActionErasesWhateverIsChosen() {
        val pen = DrawTool(StrokeTool.Pen, 0, 1f)
        val eraser = Eraser(EraseMode.Area, 12f)
        assertEquals(eraser, toolForGesture(pen, eraser, eraseHeld = true))
        assertEquals(pen, toolForGesture(pen, eraser, eraseHeld = false))
    }
}
