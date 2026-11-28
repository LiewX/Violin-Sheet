package com.violinsheet.feature.annotation.edit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.violinsheet.core.ink.FrameHit
import com.violinsheet.core.ink.PagePlacement
import kotlin.test.Test
import kotlin.test.assertEquals

class EditEraseGestureTest {
    /** Records the erase calls as text. */
    private class Recorder : EditInput {
        val calls = mutableListOf<String>()
        override fun penDown(pageIndex: Int, point: Offset, pageSize: Size, reach: Float, frameHit: FrameHit, frameMinSide: Float) = false
        override fun dragTo(point: Offset) = Unit
        override fun penUp(upPoint: Offset, moved: Boolean, cancelled: Boolean) = Unit
        override fun beginErase() { calls += "begin" }
        override fun eraseAt(pageIndex: Int, point: Offset, reach: Float) { calls += "at $pageIndex ${point.x},${point.y} r$reach" }
        override fun eraserOffPage() { calls += "off" }
        override fun endErase(cancelled: Boolean) { calls += "end $cancelled" }
        override fun loopDone(pageIndex: Int, loop: List<Offset>) = Unit
        override fun deleteSelection() = Unit
        override fun clearSelection() = Unit
    }

    // Page 0 at 2 px per point from (0, 0), 200 × 100 px on screen.
    private val placement = PagePlacement(0, Size(100f, 50f), scale = 2f, offset = Offset.Zero)
    private val edit = Recorder()
    private val erase = EditEraseGesture(edit, { screen -> placement.takeIf { it.holds(screen) } }, reachPx = 8f)

    @Test fun theLiftPointIsErasedAtBeforeTheEnd() {
        erase.begin(Offset(10f, 10f))
        erase.lift(listOf(Offset(20f, 10f), Offset(40f, 20f)), cancelled = false)
        assertEquals(listOf("begin", "at 0 5.0,5.0 r4.0", "at 0 10.0,5.0 r4.0", "at 0 20.0,10.0 r4.0", "end false"), edit.calls)
    }

    @Test fun aCancelledLiftErasesNothingMore() {
        erase.begin(Offset(10f, 10f))
        erase.lift(listOf(Offset(40f, 20f)), cancelled = true)
        assertEquals(listOf("begin", "at 0 5.0,5.0 r4.0", "end true"), edit.calls)
    }

    @Test fun aLiftOffThePageErasesNothingThere() {
        erase.begin(Offset(10f, 10f))
        erase.lift(listOf(Offset(500f, 10f)), cancelled = false)
        assertEquals(listOf("begin", "at 0 5.0,5.0 r4.0", "off", "end false"), edit.calls)
    }
}
