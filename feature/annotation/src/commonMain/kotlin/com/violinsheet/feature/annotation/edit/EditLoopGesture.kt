package com.violinsheet.feature.annotation.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.violinsheet.core.ink.PagePlacement

/**
 * The Edit Lasso's loop in screen pixels, held in Page points of the Page it started on (kept within it), and sent to
 * [edit] as [EditInput.loopDone] at the lift. A point closer than [stepPx] on screen to the last adds nothing; a pen
 * that stays within [tapSlopPx] of where it went down tapped, which sends nothing (the pen down already cleared the
 * selection). [points] and [pageIndex] are snapshot state, so the loop drawn follows them.
 */
internal class EditLoopGesture(
    private val edit: EditInput,
    private val pages: () -> List<PagePlacement>,
    private val stepPx: Float,
    private val tapSlopPx: Float,
) {
    /** The loop so far, in Page points of [pageIndex]. */
    val points = mutableStateListOf<Offset>()

    /** The Page the loop is on, or null when no loop is being drawn. */
    var pageIndex: Int? by mutableStateOf(null)
        private set

    private var down = Offset.Zero
    private var moved = false

    /** The pen went down at [screen] on Page [pageIndex] and penDown started a loop there. */
    fun begin(pageIndex: Int, screen: Offset) {
        val page = pages().firstOrNull { it.pageIndex == pageIndex } ?: return
        points.clear()
        this.pageIndex = pageIndex
        down = screen
        moved = false
        points.add(page.toPageWithin(screen))
    }

    /** The pen is at [screen]: through the start Page's placement now, so a finger zoom mid-loop keeps it under the pen. */
    fun to(screen: Offset) {
        val page = pageIndex?.let { index -> pages().firstOrNull { it.pageIndex == index } } ?: return
        if (!moved) moved = beyondSlop(down, screen, tapSlopPx)
        val last = points.lastOrNull()
        if (last != null && (page.toScreen(last) - screen).getDistanceSquared() < stepPx * stepPx) return
        points.add(page.toPageWithin(screen))
    }

    /**
     * The pen lifted at [screens] (the lift's history, then its own point), which count unless [cancelled]. A cancel
     * (palm rejection, a system gesture, or a lost lift) drops the loop, as Ink's lasso does.
     */
    fun lift(screens: List<Offset>, cancelled: Boolean) {
        val index = pageIndex ?: return
        if (!cancelled) screens.forEach(::to)
        val loop = points.toList()
        drop()
        if (!cancelled && moved) edit.loopDone(index, loop)
    }

    private fun drop() {
        pageIndex = null
        points.clear()
    }
}
