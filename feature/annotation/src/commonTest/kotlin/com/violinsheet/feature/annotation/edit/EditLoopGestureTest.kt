package com.violinsheet.feature.annotation.edit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.violinsheet.core.ink.FrameHit
import com.violinsheet.core.ink.PagePlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Edit Lasso's loop in screen pixels (#27 part 6): collected on its start Page, sent at the lift. */
class EditLoopGestureTest {
    /** Records the loops sent. */
    private class Recorder : EditInput {
        val loops = mutableListOf<Pair<Int, List<Offset>>>()
        override fun penDown(pageIndex: Int, point: Offset, pageSize: Size, reach: Float, frameHit: FrameHit, frameMinSide: Float) = false
        override fun dragTo(point: Offset) = Unit
        override fun penUp(upPoint: Offset, moved: Boolean, cancelled: Boolean) = Unit
        override fun beginErase() = Unit
        override fun eraseAt(pageIndex: Int, point: Offset, reach: Float) = Unit
        override fun eraserOffPage() = Unit
        override fun endErase(cancelled: Boolean) = Unit
        override fun loopDone(pageIndex: Int, loop: List<Offset>) { loops += pageIndex to loop }
        override fun deleteSelection() = Unit
        override fun clearSelection() = Unit
    }

    // A spread: Page 0 at 2 px per point from (0, 0), 200 × 100 px; Page 1 right of it from (200, 0).
    private var pages = listOf(
        PagePlacement(0, Size(100f, 50f), scale = 2f, offset = Offset.Zero),
        PagePlacement(1, Size(100f, 50f), scale = 2f, offset = Offset(200f, 0f)),
    )
    private val edit = Recorder()
    private val loop = EditLoopGesture(edit, { pages }, stepPx = 4f, tapSlopPx = 8f)

    @Test fun aLoopIsSentAtTheLiftInPagePointsWithTheLiftPoint() {
        loop.begin(0, Offset(20f, 20f))
        loop.to(Offset(60f, 20f))
        loop.to(Offset(60f, 60f))
        assertEquals(0, loop.pageIndex)
        assertEquals(3, loop.points.size)
        loop.lift(listOf(Offset(20f, 60f)), cancelled = false)
        assertEquals(listOf(0 to listOf(Offset(10f, 10f), Offset(30f, 10f), Offset(30f, 30f), Offset(10f, 30f))), edit.loops)
        assertNull(loop.pageIndex)
        assertTrue(loop.points.isEmpty())
    }

    @Test fun pointsCloserThanTheStepAddNothing() {
        loop.begin(0, Offset(20f, 20f))
        loop.to(Offset(23f, 20f))
        assertEquals(1, loop.points.size)
        loop.to(Offset(24f, 20f))
        assertEquals(2, loop.points.size)
    }

    @Test fun aStillLiftSendsNothing() {
        loop.begin(0, Offset(20f, 20f))
        loop.to(Offset(26f, 20f))
        loop.lift(listOf(Offset(27f, 21f)), cancelled = false)
        assertTrue(edit.loops.isEmpty())
        assertNull(loop.pageIndex)
    }

    @Test fun aLiftBeyondTheSlopIsALoopEvenWithoutMoves() {
        loop.begin(0, Offset(20f, 20f))
        loop.lift(listOf(Offset(60f, 60f)), cancelled = false)
        assertEquals(1, edit.loops.size)
    }

    @Test fun aCancelledLoopIsDropped() {
        loop.begin(0, Offset(20f, 20f))
        loop.to(Offset(80f, 20f))
        loop.to(Offset(80f, 80f))
        loop.lift(listOf(Offset(20f, 80f)), cancelled = true)
        assertTrue(edit.loops.isEmpty())
        assertNull(loop.pageIndex)
        assertTrue(loop.points.isEmpty())
    }

    @Test fun pointsOffTheStartPageAreKeptAtItsEdge() {
        loop.begin(0, Offset(150f, 20f))
        loop.to(Offset(260f, 20f)) // over Page 1
        loop.to(Offset(260f, 300f)) // below every Page
        loop.lift(listOf(Offset(150f, 80f)), cancelled = false)
        assertEquals(listOf(Offset(75f, 10f), Offset(100f, 10f), Offset(100f, 50f), Offset(75f, 40f)), edit.loops.single().second)
        assertEquals(0, edit.loops.single().first)
    }

    @Test fun aZoomMidLoopMapsTheNextPointsThroughTheNewPlacement() {
        loop.begin(0, Offset(20f, 20f))
        pages = listOf(PagePlacement(0, Size(100f, 50f), scale = 4f, offset = Offset.Zero))
        loop.to(Offset(80f, 20f))
        assertEquals(Offset(20f, 5f), loop.points.last())
    }

    @Test fun aLoopOnAPageNoLongerPlacedCollectsNothingMore() {
        loop.begin(1, Offset(220f, 20f))
        pages = pages.take(1)
        loop.to(Offset(280f, 60f))
        assertEquals(1, loop.points.size)
    }
}
