package com.violinsheet.core.ink

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/**
 * Where Page [pageIndex] is on screen right now: screen = Page point × [scale] + [offset]. The viewer works this out
 * from its layout and zoom; Ink and other Annotations map through it, so they stay on the notes they mark.
 */
data class PagePlacement(val pageIndex: Int, val pageSize: Size, val scale: Float, val offset: Offset) {
    val screenRect: Rect get() = Rect(offset, pageSize * scale)

    fun toScreen(page: Offset): Offset = page * scale + offset

    fun toPage(screen: Offset): Offset = (screen - offset) / scale

    /** Whether the screen point [screen] is on this Page, its edges included: the one rule for "on a Page". */
    fun holds(screen: Offset): Boolean = screenRect.let { screen.x in it.left..it.right && screen.y in it.top..it.bottom }
}
