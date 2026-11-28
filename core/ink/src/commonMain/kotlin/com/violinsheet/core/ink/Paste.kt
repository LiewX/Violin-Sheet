package com.violinsheet.core.ink

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/** How far (Page points, both down and right) each paste on the copy's own Page lands from the last: about 4 mm on A4. */
const val PASTE_STEP = 12f

/**
 * Where copied ink with the ink box [box] lands on a Page of size [page] when [offset] is added: scaled down about the
 * box centre if it is bigger than the Page (its stroke sizes scale with it), moved by [offset], then shifted inside the
 * Page (keepInside). One transform for the strokes and, with SelectionFrame.transformed, for the frame.
 */
fun pastePlacement(box: Rect, page: Size, offset: Offset): StrokeTransform {
    val fit = if (box.width > page.width || box.height > page.height) {
        minOf(page.width / box.width, page.height / box.height)
    } else {
        1f
    }
    val c = box.center
    val half = Offset(box.width * fit / 2, box.height * fit / 2)
    val moved = Rect(c - half + offset, c + half + offset)
    return StrokeTransform(scale = fit, pivot = c, translation = offset + keepInside(moved, Rect(Offset.Zero, page)))
}
