package com.violinsheet.feature.annotation.edit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.violinsheet.core.ink.FrameHit

/**
 * What Edit mode's pen layer reports, in Page coordinates of the Page under the pen at pen down. Reaches are screen
 * distances (TAP_REACH, ERASE_REACH) already turned into Page points. Fingers never call it.
 */
interface EditInput {
    /**
     * The pen went down at [point] on Page [pageIndex] (size [pageSize]) with the Symbol, Lasso or Cover tool.
     * [frameHit] is what the pen hit on the selection's frame: the pen layer runs `hitTest` (no rotate handle)
     * when the selection is on this Page, and passes [FrameHit.Outside] otherwise. A corner starts a resize (one
     * Symbol; several only move), the inside a move of all selected (a still lift there changes nothing); otherwise
     * the topmost Symbol within [reach] of [point] is selected alone and starts a move, and empty
     * Page clears the selection and, with the Symbol tool, starts a placement: the Symbol shows under the pen at once,
     * follows [dragTo] and is placed at [penUp] where it was last shown. With the Lasso tool, empty Page starts a loop
     * instead: the result is true, and the pen layer then collects the loop and sends [loopDone] at the lift (a
     * still lift sends nothing), not [dragTo] or [penUp]. [frameMinSide] is the
     * shortest side the frame is shown with (FRAME_MIN_SIDE in Page points): a resize follows the pen on the frame as
     * shown, not on the glyph's own (maybe tiny) box.
     */
    fun penDown(pageIndex: Int, point: Offset, pageSize: Size, reach: Float, frameHit: FrameHit, frameMinSide: Float = 0f): Boolean

    /** The pen moved to [point] since [penDown]. */
    fun dragTo(point: Offset)

    /**
     * The pen lifted at [upPoint]; [moved] is whether it went further than the tap slop from the down point, and
     * [cancelled] whether the system took the pointer (palm rejection), which undoes the gesture.
     */
    fun penUp(upPoint: Offset, moved: Boolean, cancelled: Boolean)

    /** The pen went down to erase: the Eraser tool, or any tool with the side button held. Ends any selection or placement. */
    fun beginErase()

    /** The Eraser is at [point] on Page [pageIndex]: every Symbol there whose box, widened by [reach], holds it goes. */
    fun eraseAt(pageIndex: Int, point: Offset, reach: Float)

    /** The Eraser is off every Page: the next [eraseAt] starts afresh rather than sweeping from where it left. */
    fun eraserOffPage()

    /** The Eraser lifted: one undo step for what it took; [cancelled] puts it all back without saving. */
    fun endErase(cancelled: Boolean)

    /**
     * A lasso loop on Page [pageIndex] closed at the lift, [loop] in its Page points: the Symbols there whose glyph box
     * is wholly inside are selected (none clears the selection). The Lasso tool's loop sends it (EditLoopGesture).
     */
    fun loopDone(pageIndex: Int, loop: List<Offset>)

    /** Deletes the selected Symbols, as one undo step. */
    fun deleteSelection()

    fun clearSelection()
}
