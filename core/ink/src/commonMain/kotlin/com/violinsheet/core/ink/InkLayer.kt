package com.violinsheet.core.ink

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

/**
 * Draws [strokes] on the Pages at [placements] and lets the pen use [tool]: a finished stroke goes to [onStroke], an
 * erase gesture to [erase], both in Page coordinates. Holding the pen's erase action erases with [eraser] instead
 * (toolForGesture). Only the pen draws and erases; finger touches pass through to navigation (CLAUDE.md: input split).
 * Platform code: native low-latency ink per platform (ADR 0002).
 */
@Composable
expect fun InkLayer(
    placements: List<PagePlacement>,
    strokes: List<InkStroke>,
    tool: InkTool,
    eraser: Eraser,
    onStroke: (InkStroke) -> Unit,
    erase: EraseInput,
    modifier: Modifier = Modifier,
)

/** One erase gesture, pen down to up, as InkLayer reports it. */
interface EraseInput {
    fun beginErase(eraser: Eraser)

    /** The eraser is now at [at] (Page coordinates) on Page [pageIndex]. */
    fun eraseAt(pageIndex: Int, at: Offset)

    /** The eraser is off every Page: the next [eraseAt] starts afresh rather than sweeping from where it left. */
    fun eraserOffPage()

    /** Pen up. [cancelled]: the system took the pen away (palm rejection), so nothing of the gesture should stay. */
    fun endErase(cancelled: Boolean)
}
