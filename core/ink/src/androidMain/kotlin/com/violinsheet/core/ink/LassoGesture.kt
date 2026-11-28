package com.violinsheet.core.ink

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

// A Lasso pen-down that stays within this of where it started is a tap; it picks a stroke whose ink is this near.
private val TAP_SLOP = 8.dp
private val TAP_REACH = 4.dp

/** The pen's gestures with the Lasso tool: drawing a lasso, tapping a stroke, dragging the selection. */
internal class LassoGesture(private val state: InkLayerState, density: Density) {
    private val lassoStepSquared = with(density) { LASSO_STEP.toPx() }.let { it * it }
    private val tapSlopSquared = with(density) { TAP_SLOP.toPx() }.let { it * it }
    private val tapReach = with(density) { TAP_REACH.toPx() }
    private val handleHitRadius = with(density) { HANDLE_HIT_RADIUS.toPx() }
    private val rotateHandleDistance = with(density) { ROTATE_HANDLE_DISTANCE.toPx() }
    private val insideMargin = with(density) { INSIDE_MARGIN.toPx() }
    private val frameMinSide = with(density) { FRAME_MIN_SIDE.toPx() }

    // The Page a drag on the selection runs on. Each event goes through that Page's current placement, so a finger
    // zoom mid-drag keeps the drag under the pen.
    var dragPage: Int? = null
        private set

    // Where the lasso's pen went down (screen), and whether it has stayed near there: a tap, not a lasso.
    private var lassoStart = Offset.Zero
    private var lassoIsTap = false

    val lassoing get() = state.lassoPage != null

    fun dragTo(position: Offset) {
        // Only where the pen is now matters, so the historical positions are skipped.
        state.placementOf(dragPage ?: return)?.let { state.lasso.dragTo(it.toPage(position)) }
    }

    fun endDrag(cancelled: Boolean) {
        dragPage = null
        state.lasso.endDrag(cancelled)
    }

    fun lassoTo(position: Offset) {
        val page = state.lassoPage?.let(state::placementOf) ?: return
        if ((position - lassoStart).getDistanceSquared() > tapSlopSquared) lassoIsTap = false
        val last = state.lassoPoints.lastOrNull()
        if (last != null && (page.toScreen(last) - position).getDistanceSquared() < lassoStepSquared) return
        state.lassoPoints.add(page.toPageWithin(position))
    }

    fun endLasso(cancelled: Boolean) {
        val pageIndex = state.lassoPage ?: return
        val points = state.lassoPoints.toList()
        state.lassoPage = null
        state.lassoPoints.clear()
        if (cancelled) return
        val page = state.placementOf(pageIndex)
        // A pen that barely moved drew no lasso: it picks the stroke under it, or clears the selection.
        if (lassoIsTap && page != null) state.lasso.tapAt(pageIndex, points.first(), tapReach / page.scale)
        else state.lasso.lassoDone(pageIndex, points)
    }

    /**
     * Pen down with the Lasso tool: a drag if it hits the selection's frame or handles, else a new lasso on the Page
     * under the pen (none between Pages).
     */
    fun begin(position: Offset) {
        val selected = state.selection
        val onPage = selected?.let { state.placementOf(it.pageIndex) }
        val hit = if (selected != null && onPage != null) {
            hitTest(selected.frame, onPage, position, handleHitRadius, rotateHandleDistance, insideMargin, frameMinSide)
        } else {
            FrameHit.Outside
        }
        val kind = when (hit) {
            FrameHit.RotateHandle -> DragKind.Rotate
            is FrameHit.Corner -> DragKind.Resize(hit.index)
            FrameHit.Inside -> DragKind.Move
            FrameHit.Outside -> null
        }
        if (kind != null && onPage != null) {
            dragPage = onPage.pageIndex
            state.lasso.beginDrag(kind, onPage.toPage(position), onPage.pageSize)
            return
        }
        state.lasso.clearSelection()
        val page = state.pageAt(position) ?: return
        state.lassoPage = page.pageIndex
        lassoStart = position
        lassoIsTap = true
        state.lassoPoints.add(page.toPageWithin(position))
    }
}
