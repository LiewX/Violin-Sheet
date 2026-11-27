package com.violinsheet.core.ink

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * Draws [strokes] on the Pages at [placements] and lets the pen use [tool]: a finished stroke goes to [onStroke], an
 * erase gesture to [erase], both in Page coordinates. Holding the pen's erase action erases with [eraser] instead
 * (toolForGesture). With the Lasso tool the pen selects and drags [selection] through [lasso]; the frame is drawn in
 * [selectionColour]. Only the pen draws, erases and selects; finger touches pass through to navigation (CLAUDE.md:
 * input split).
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
    selection: Selection?,
    lasso: LassoInput,
    selectionColour: Color,
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

/** A selection of strokes on one Page, as the lasso made it. */
data class Selection(
    val pageIndex: Int,
    /** In drawing order. */
    val ids: List<Long>,
    /** The frame as it was before the drag in progress, if any; show it with [preview] applied (SelectionFrame.transformed). */
    val frame: SelectionFrame,
    /** The drag in progress, clamped to the Page: draw the selected strokes with it applied. Null when no drag runs. */
    val preview: StrokeTransform?,
)

/** What a pen drag on a selection does, decided at pen down (hitTest). */
sealed interface DragKind {
    data object Move : DragKind

    /** Resizes with corner [corner] (SelectionFrame.corners) about the opposite one. */
    data class Resize(val corner: Int) : DragKind

    data object Rotate : DragKind
}

/** The Lasso tool's gestures, as InkLayer and the toolbar report them, in Page coordinates. */
interface LassoInput {
    /** A lasso drawn through [pagePoints] on Page [pageIndex]: selects what it caught, or clears the selection. */
    fun lassoDone(pageIndex: Int, pagePoints: List<Offset>)

    /** A pen tap at [pagePoint] on Page [pageIndex]: selects the stroke within [slop] (Page points) of it alone, or clears. */
    fun tapAt(pageIndex: Int, pagePoint: Offset, slop: Float)

    /** Pen down on the selection at [pagePoint]; its Page is [pageSize] (Page points), the edge the drag stops at. */
    fun beginDrag(kind: DragKind, pagePoint: Offset, pageSize: Size)

    fun dragTo(pagePoint: Offset)

    /** Pen up: the drag is saved as one undo step. [cancelled]: the system took the pen away, so nothing changes. */
    fun endDrag(cancelled: Boolean)

    fun deleteSelection()

    /** Ends the selection without changing it (a page turn, a tap outside it). */
    fun clearSelection()
}
