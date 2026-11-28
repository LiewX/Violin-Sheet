package com.violinsheet.core.ink

import android.view.View
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.StrokeInput

/**
 * A wet stroke's tool, which the Ink API stroke doesn't carry, and the Page its points are in: the one the pen started
 * on, or the nearest one when it started off every Page. [start] is where that Page was at pen down.
 */
internal class WetStroke(val start: PagePlacement, val tool: StrokeTool)

/**
 * The pen's input: draws, erases and lassos as [state]'s tool says. Pen input goes through Compose rather than a View
 * touch listener: Compose sees every pointer on its own, so the pen draws even inside a gesture a resting palm started
 * (navigation's palm guard deals with the palm itself). Finger touches pass through.
 */
internal fun Modifier.penInput(state: InkLayerState, hostView: View): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        var stroke: InProgressStrokeId? = null
        var erasing: Eraser? = null
        var penPointer: PointerId? = null
        var placement: PagePlacement? = null
        var startMillis = 0L
        val lasso = LassoGesture(state, this)
        fun input(change: PointerInputChange, position: Offset, atMillis: Long): StrokeInput {
            val page = placement!!.toPage(position)
            return StrokeInput.create(page.x, page.y, atMillis - startMillis, InputToolType.STYLUS, pressure = change.pressure)
        }
        // The eraser erases on whichever Page it is over; between Pages it erases nothing.
        fun eraseAt(position: Offset) {
            val page = state.pageAt(position)
            state.eraserCircle = page?.let { position to erasing!!.size / 2 * it.scale }
            if (page != null) state.erase.eraseAt(page.pageIndex, page.toPage(position)) else state.erase.eraserOffPage()
        }
        // Every point of a pen event, its batched history first; used on moves and on the lift.
        fun eraseAlong(change: PointerInputChange) {
            for (h in change.historical) eraseAt(h.position)
            eraseAt(change.position)
        }
        fun endErase(cancelled: Boolean) {
            erasing = null
            state.eraserCircle = null
            state.erase.endErase(cancelled)
        }
        try {
            while (true) {
                val event = awaitPointerEvent()
                val strokesView = state.view ?: continue
                for (change in event.changes) {
                    if (change.type != PointerType.Stylus) continue
                    val current = stroke
                    when {
                        change.pressed && !change.previousPressed -> {
                            // A gesture still open means its lift was lost: drop a stroke rather than block the pen;
                            // keep what an erase did.
                            current?.let {
                                strokesView.cancelStroke(it)
                                state.wetStrokes.remove(it)
                                stroke = null
                            }
                            if (erasing != null) endErase(cancelled = false)
                            // A drag is kept as done so far; a half-drawn lasso is dropped, like a stroke.
                            if (lasso.dragPage != null) lasso.endDrag(cancelled = false)
                            lasso.endLasso(cancelled = true)
                            // Decided once, at pen down: pressing or releasing the side button mid-gesture changes
                            // nothing until the pen lifts.
                            val chosen = toolForGesture(state.tool, state.eraser, event.isEraseHeld())
                            // Whatever the tool, the pen touching a Page is where a paste goes.
                            state.pageAt(change.position)?.let { state.onPenDown(it.pageIndex) }
                            if (chosen is Lasso) {
                                penPointer = change.id
                                event.motionEvent?.let(hostView::requestUnbufferedDispatch)
                                lasso.begin(change.position)
                                change.consume()
                                continue
                            }
                            if (chosen is Eraser) {
                                erasing = chosen
                                penPointer = change.id
                                event.motionEvent?.let(hostView::requestUnbufferedDispatch)
                                state.erase.beginErase(chosen)
                                eraseAt(change.position)
                                change.consume()
                                continue
                            }
                            val draw = chosen as DrawTool
                            // The stroke is drawn in the coordinates of the Page the pen starts on, or the nearest
                            // one if it starts off every Page; at lift it is cut into one stroke per Page it ran over.
                            val page = state.nearestPage(change.position) ?: continue
                            placement = page
                            startMillis = change.uptimeMillis
                            penPointer = change.id
                            // This stroke's events as they come, not batched per frame (Ink API advice).
                            event.motionEvent?.let(hostView::requestUnbufferedDispatch)
                            stroke = strokesView.startStroke(
                                input(change, change.position, change.uptimeMillis),
                                brush(draw.tool, draw.colour, draw.size),
                                page.pageToScreen(),
                            ).also { state.wetStrokes[it] = WetStroke(page, draw.tool) }
                            change.consume()
                        }
                        erasing != null && change.id == penPointer && change.pressed -> {
                            eraseAlong(change)
                            change.consume()
                        }
                        erasing != null && change.id == penPointer -> {
                            // The lift ends the erase. If palm rejection or a system gesture took the pen, take the erasing back.
                            val cancelled = event.isSystemCancel()
                            if (!cancelled) eraseAlong(change) // the lift's own points are touched too
                            endErase(cancelled = cancelled)
                            change.consume()
                        }
                        lasso.dragPage != null && change.id == penPointer && change.pressed -> {
                            lasso.dragTo(change.position)
                            change.consume()
                        }
                        lasso.dragPage != null && change.id == penPointer -> {
                            // Palm rejection or a system gesture took the pen: the selection stays where it was.
                            lasso.endDrag(cancelled = event.isSystemCancel())
                            change.consume()
                        }
                        lasso.lassoing && change.id == penPointer && change.pressed -> {
                            for (h in change.historical) lasso.lassoTo(h.position)
                            lasso.lassoTo(change.position)
                            change.consume()
                        }
                        lasso.lassoing && change.id == penPointer -> {
                            lasso.endLasso(cancelled = event.isSystemCancel())
                            change.consume()
                        }
                        current != null && change.id == penPointer && change.pressed -> {
                            val batch = MutableStrokeInputBatch()
                            for (h in change.historical) batch.add(input(change, h.position, h.uptimeMillis))
                            batch.add(input(change, change.position, change.uptimeMillis))
                            strokesView.addToStroke(batch, current)
                            change.consume()
                        }
                        current != null && change.id == penPointer -> {
                            if (event.isSystemCancel()) {
                                // Palm rejection or a system gesture took the pen: drop the stroke, no stray mark.
                                strokesView.cancelStroke(current)
                                state.wetStrokes.remove(current)
                            } else {
                                strokesView.finishStroke(input(change, change.position, change.uptimeMillis), current)
                            }
                            stroke = null
                            change.consume()
                        }
                    }
                }
            }
        } finally {
            // Leaving the screen mid-stroke: drop the unfinished stroke rather than keep half of it. Mid-erase: keep
            // what was erased, as one undo step.
            stroke?.let {
                state.view?.cancelStroke(it)
                state.wetStrokes.remove(it)
            }
            if (erasing != null) endErase(cancelled = false)
            if (lasso.dragPage != null) lasso.endDrag(cancelled = false)
            lasso.endLasso(cancelled = true)
        }
    }
}
