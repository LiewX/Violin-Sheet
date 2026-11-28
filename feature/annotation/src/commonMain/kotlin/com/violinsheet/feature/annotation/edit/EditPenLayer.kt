package com.violinsheet.feature.annotation.edit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.violinsheet.core.ink.FRAME_MIN_SIDE
import com.violinsheet.core.ink.FrameHit
import com.violinsheet.core.ink.HANDLE_HIT_RADIUS
import com.violinsheet.core.ink.INSIDE_MARGIN
import com.violinsheet.core.ink.LASSO_STEP
import com.violinsheet.core.ink.PagePlacement
import com.violinsheet.core.ink.ROTATE_HANDLE_DISTANCE
import com.violinsheet.core.ink.drawLasso
import com.violinsheet.core.ink.drawSelectionFrame
import com.violinsheet.core.ink.isEraseHeld
import com.violinsheet.core.ink.isSystemCancel
import com.violinsheet.feature.annotation.edit.symbol.SymbolSelection

// Screen distances, whatever the zoom. A pen that stays within TAP_SLOP of where it went down tapped.
private val TAP_SLOP = 8.dp
private val TAP_REACH = 8.dp
private val ERASE_REACH = 4.dp

/** The pen gesture in progress: on a Symbol or empty Page ([Press]), the Lasso's loop, or an erase. */
private sealed interface Gesture {
    class Press(val pageIndex: Int, val down: Offset, var lastOnPage: Offset, var moved: Boolean = false) : Gesture

    data object Loop : Gesture

    data object Erase : Gesture
}

/**
 * Edit mode's pen input over the Pages at [placements], sent to [edit] in Page coordinates, the Lasso's loop drawn as
 * it goes, and the frame of [selection] drawn on top in [frameColour] (corner handles for one Symbol only, no rotate
 * handle). Only the pen counts: a finger's change is never consumed, so navigation still gets it. The S Pen side
 * button held at pen down erases Symbols, whatever the tool; Ink is never erased here (the Ink layer takes no pen
 * input in Edit mode).
 */
@Composable
internal fun EditPenLayer(
    placements: List<PagePlacement>,
    edit: EditInput,
    tool: EditTool,
    selection: SymbolSelection?,
    frameColour: Color,
    modifier: Modifier = Modifier,
) {
    val currentPlacements by rememberUpdatedState(placements)
    val currentTool by rememberUpdatedState(tool)
    val currentSelection by rememberUpdatedState(selection)
    val framePath = remember { Path() }
    val loopPath = remember { Path() }
    val density = LocalDensity.current
    val loopDash = remember(density) { with(density) { PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())) } }
    val loop = remember(edit, density) {
        with(density) { EditLoopGesture(edit, { currentPlacements }, LASSO_STEP.toPx(), TAP_SLOP.toPx()) }
    }
    Box(modifier.pointerInput(edit, loop) {
        val tapSlop = TAP_SLOP.toPx()
        val tapReach = TAP_REACH.toPx()
        val eraseReach = ERASE_REACH.toPx()
        val handleRadius = HANDLE_HIT_RADIUS.toPx()
        val insideMargin = INSIDE_MARGIN.toPx()
        val minSide = FRAME_MIN_SIDE.toPx()
        fun placementOf(pageIndex: Int) = currentPlacements.firstOrNull { it.pageIndex == pageIndex }
        fun pageAt(screen: Offset) = currentPlacements.firstOrNull { it.holds(screen) }
        awaitPointerEventScope {
            var penPointer: PointerId? = null
            var gesture: Gesture? = null
            val erase = EditEraseGesture(edit, ::pageAt, eraseReach)
            // Ends the open gesture with no lift seen (a lost lift, or leaving the screen): a drag, a placement and an
            // erase are kept as done so far; a placement lands where it was last shown (#27 design 7a.2). A loop
            // is dropped, as Ink's lasso.
            // shortcut: #16's two-endpoint kinds place from the down point to lastOnPage here; check that is wanted.
            fun abandon() {
                when (val open = gesture) {
                    is Gesture.Press -> edit.penUp(open.lastOnPage, moved = true, cancelled = false)
                    Gesture.Loop -> loop.lift(emptyList(), cancelled = true)
                    Gesture.Erase -> edit.endErase(cancelled = false)
                    null -> Unit
                }
                gesture = null
                penPointer = null
            }
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    for (change in event.changes) {
                        if (change.type != PointerType.Stylus) continue
                        val open = gesture
                        when {
                            change.pressed && !change.previousPressed -> {
                                abandon()
                                // Decided once, at pen down: pressing or releasing the side button mid-gesture
                                // changes nothing until the pen lifts.
                                if (editToolForGesture(currentTool, event.isEraseHeld()) == EditTool.Eraser) {
                                    penPointer = change.id
                                    gesture = Gesture.Erase
                                    erase.begin(change.position)
                                    change.consume()
                                    continue
                                }
                                val page = pageAt(change.position) ?: continue
                                val point = page.toPage(change.position)
                                val selected = currentSelection?.takeIf { it.pageIndex == page.pageIndex }
                                val hit = selected?.let { symbolFrameHit(it.frame, page, change.position, handleRadius, insideMargin, minSide, it.single) }
                                    ?: FrameHit.Outside
                                penPointer = change.id
                                val startsLoop = edit.penDown(page.pageIndex, point, page.pageSize, page.pagePoints(tapReach), hit, page.pagePoints(minSide))
                                gesture = if (startsLoop) {
                                    loop.begin(page.pageIndex, change.position)
                                    Gesture.Loop
                                } else {
                                    Gesture.Press(page.pageIndex, change.position, point)
                                }
                                change.consume()
                            }
                            open == null || change.id != penPointer -> Unit
                            change.pressed -> {
                                when (open) {
                                    is Gesture.Press -> {
                                        // Through the Page's placement now, so a finger zoom mid-drag keeps it under the pen.
                                        val page = placementOf(open.pageIndex)
                                        if (page != null) {
                                            open.lastOnPage = page.toPage(change.position)
                                            if (!open.moved) open.moved = beyondSlop(open.down, change.position, tapSlop)
                                            // Within the slop it is still a tap: the Symbol doesn't wobble under it.
                                            if (open.moved) edit.dragTo(open.lastOnPage)
                                        }
                                    }
                                    Gesture.Loop -> {
                                        for (h in change.historical) loop.to(h.position)
                                        loop.to(change.position)
                                    }
                                    Gesture.Erase -> {
                                        for (h in change.historical) erase.at(h.position)
                                        erase.at(change.position)
                                    }
                                }
                                change.consume()
                            }
                            else -> {
                                // Palm rejection or a system gesture took the pen: the gesture is undone.
                                val cancelled = event.isSystemCancel()
                                when (open) {
                                    is Gesture.Press -> {
                                        val page = placementOf(open.pageIndex)
                                        val up = page?.toPage(change.position) ?: open.lastOnPage
                                        if (!cancelled && page != null) {
                                            if (!open.moved) open.moved = beyondSlop(open.down, change.position, tapSlop)
                                            // The lift's own position counts: a drag ends where the pen left the Page.
                                            if (open.moved) edit.dragTo(up)
                                        }
                                        edit.penUp(up, open.moved, cancelled)
                                    }
                                    // The lift's own point is touched too, and closes the loop.
                                    Gesture.Loop -> loop.lift(change.historical.map { it.position } + change.position, cancelled)
                                    Gesture.Erase -> erase.lift(change.historical.map { it.position } + change.position, cancelled)
                                }
                                gesture = null
                                penPointer = null
                                change.consume()
                            }
                        }
                    }
                }
            } finally {
                abandon()
            }
        }
    }) {
        // The loop being drawn: a dashed outline, as Ink's lasso.
        Canvas(Modifier.fillMaxSize()) {
            val page = loop.pageIndex?.let { index -> placements.firstOrNull { it.pageIndex == index } } ?: return@Canvas
            drawLasso(page, loop.points, loopPath, loopDash)
        }
        Canvas(Modifier.fillMaxSize()) {
            val selected = selection ?: return@Canvas
            val page = placements.firstOrNull { it.pageIndex == selected.pageIndex } ?: return@Canvas
            drawSelectionFrame(selected.frame, selected.preview, page, frameColour, framePath, rotatable = false, handles = selected.single)
        }
    }
}
