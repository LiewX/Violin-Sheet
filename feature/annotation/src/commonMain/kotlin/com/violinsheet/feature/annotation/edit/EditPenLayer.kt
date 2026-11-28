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
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.violinsheet.core.ink.FRAME_MIN_SIDE
import com.violinsheet.core.ink.FrameHit
import com.violinsheet.core.ink.HANDLE_HIT_RADIUS
import com.violinsheet.core.ink.INSIDE_MARGIN
import com.violinsheet.core.ink.PagePlacement
import com.violinsheet.core.ink.ROTATE_HANDLE_DISTANCE
import com.violinsheet.core.ink.SelectionFrame
import com.violinsheet.core.ink.drawSelectionFrame
import com.violinsheet.core.ink.hitTest
import com.violinsheet.core.ink.isSystemCancel

// Screen distances, whatever the zoom. A pen that stays within TAP_SLOP of where it went down tapped.
private val TAP_SLOP = 8.dp
private val TAP_REACH = 8.dp
private val ERASE_REACH = 4.dp

/** Whether the pen at [now] has gone further than [slopPx] from [down] (screen px): a drag, not a tap. */
internal fun beyondSlop(down: Offset, now: Offset, slopPx: Float): Boolean = (now - down).getDistanceSquared() > slopPx * slopPx

/** A screen distance [px] as Page points on [placement]. */
internal fun PagePlacement.pagePoints(px: Float): Float = px / scale

/**
 * What [screenPoint] hits on a Symbol's [frame] (no rotate handle). A fingering's frame is shown so small that its
 * corners' hit circles overlap and cover its middle, so within them the nearest corner counts, and a pen nearer the
 * frame's centre than to any corner moves the Symbol rather than resizing it.
 */
internal fun symbolFrameHit(
    frame: SelectionFrame,
    placement: PagePlacement,
    screenPoint: Offset,
    handleRadiusPx: Float,
    insideMarginPx: Float,
    minSidePx: Float,
): FrameHit {
    val hit = hitTest(frame, placement, screenPoint, handleRadiusPx, 0f, insideMarginPx, minSidePx, rotatable = false)
    if (hit !is FrameHit.Corner) return hit
    val distances = frame.shownAt(placement, minSidePx).corners().map { (placement.toScreen(it) - screenPoint).getDistanceSquared() }
    val nearest = distances.indices.minBy { distances[it] }
    val toCentre = (placement.toScreen(frame.center) - screenPoint).getDistanceSquared()
    return if (toCentre < distances[nearest]) FrameHit.Inside else FrameHit.Corner(nearest)
}

/** The pen gesture in progress: on a Symbol or empty Page ([Press]), or an erase. */
private sealed interface Gesture {
    class Press(val pageIndex: Int, val down: Offset, var lastOnPage: Offset, var moved: Boolean = false) : Gesture

    data object Erase : Gesture
}

/**
 * Edit mode's pen input over the Pages at [placements], sent to [edit] in Page coordinates, and the frame of
 * [selection] drawn on top in [frameColour] (corner handles, no rotate handle). Only the pen counts: a finger's change
 * is never consumed, so navigation still gets it. Button state is never read, so the S Pen side button does nothing.
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
    Box(modifier.pointerInput(edit) {
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
            fun eraseAt(screen: Offset) {
                // Between Pages the eraser erases nothing, and it doesn't sweep from where it left on coming back.
                val page = pageAt(screen) ?: return edit.eraserOffPage()
                edit.eraseAt(page.pageIndex, page.toPage(screen), page.pagePoints(eraseReach))
            }
            // Ends the open gesture with no lift seen (a lost lift, or leaving the screen): a drag and an erase are
            // kept as done so far. A pending placement is dropped: penUp with moved = true places no one-glyph kind.
            // shortcut: #16's two-endpoint kinds need their own "dropped" signal here, or a drag would place one.
            fun abandon() {
                when (val open = gesture) {
                    is Gesture.Press -> edit.penUp(open.lastOnPage, moved = true, cancelled = false)
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
                                if (currentTool == EditTool.Eraser) {
                                    penPointer = change.id
                                    gesture = Gesture.Erase
                                    edit.beginErase()
                                    eraseAt(change.position)
                                    change.consume()
                                    continue
                                }
                                val page = pageAt(change.position) ?: continue
                                val point = page.toPage(change.position)
                                val selected = currentSelection?.takeIf { it.pageIndex == page.pageIndex }
                                val hit = selected?.let { symbolFrameHit(it.frame, page, change.position, handleRadius, insideMargin, minSide) }
                                    ?: FrameHit.Outside
                                penPointer = change.id
                                gesture = Gesture.Press(page.pageIndex, change.position, point)
                                edit.penDown(page.pageIndex, point, page.pageSize, page.pagePoints(tapReach), hit, page.pagePoints(minSide))
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
                                    Gesture.Erase -> {
                                        for (h in change.historical) eraseAt(h.position)
                                        eraseAt(change.position)
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
                                    Gesture.Erase -> edit.endErase(cancelled)
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
        Canvas(Modifier.fillMaxSize()) {
            val selected = selection ?: return@Canvas
            val page = placements.firstOrNull { it.pageIndex == selected.pageIndex } ?: return@Canvas
            drawSelectionFrame(selected.frame, selected.preview, page, frameColour, framePath, rotatable = false)
        }
    }
}
