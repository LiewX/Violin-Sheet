package com.violinsheet.feature.annotation.edit

import androidx.compose.ui.geometry.Offset
import com.violinsheet.core.ink.PagePlacement

/**
 * One Edit mode erase (the Eraser tool, or any tool with the side button) in screen pixels, sent to [edit] in Page
 * coordinates of the Page under the pen at each point ([pageAt], the pen layer's rule). [reachPx] is ERASE_REACH in
 * pixels.
 */
internal class EditEraseGesture(
    private val edit: EditInput,
    private val pageAt: (screen: Offset) -> PagePlacement?,
    private val reachPx: Float,
) {
    fun begin(screen: Offset) {
        edit.beginErase()
        at(screen)
    }

    /** Between Pages the eraser erases nothing, and it doesn't sweep from where it left on coming back. */
    fun at(screen: Offset) {
        val page = pageAt(screen) ?: return edit.eraserOffPage()
        edit.eraseAt(page.pageIndex, page.toPage(screen), page.pagePoints(reachPx))
    }

    /** The pen lifted at [screens] (the lift's history, then its own point): touched too, unless [cancelled]. */
    fun lift(screens: List<Offset>, cancelled: Boolean) {
        if (!cancelled) screens.forEach(::at)
        edit.endErase(cancelled)
    }
}
