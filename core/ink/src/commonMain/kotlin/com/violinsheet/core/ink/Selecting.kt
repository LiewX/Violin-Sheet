package com.violinsheet.core.ink

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.ceil

// The lasso: which strokes it or a tap catches (Page points), and what a pen touch on the selection's frame hits
// (screen px).
// The frame and its actions are in SelectionFrame.kt.

/** Each segment is measured in pieces no longer than this, so a lasso edge crossing a long segment counts its part. */
private const val LASSO_PIECE = 1f

/**
 * The ids (in list order) of the strokes on Page [pageIndex] the [lasso] catches: those wholly inside it (as in Samsung
 * Notes), or with [includePartlyEnclosed] those with more than half their length inside. Length, not points: slow pen
 * movement crowds points. The lasso is closed from its last point to its first, and inside follows the nonzero winding
 * rule, so both loops of a figure eight count.
 */
fun lassoSelects(strokes: List<InkStroke>, pageIndex: Int, lasso: List<Offset>, includePartlyEnclosed: Boolean): List<Long> {
    if (lasso.size < 3) return emptyList()
    val lassoBox = boundsOf(lasso)
    return strokes.filter {
        it.pageIndex == pageIndex && it.pointBounds().meets(lassoBox) && it.caughtBy(lasso, includePartlyEnclosed)
    }.map { it.id }
}

private fun InkStroke.caughtBy(lasso: List<Offset>, includePartlyEnclosed: Boolean): Boolean {
    var total = 0f
    var inside = 0f
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        val length = segmentLength(a, b)
        if (length == 0f) continue
        val count = ceil(length / LASSO_PIECE).toInt()
        val piece = length / count
        for (k in 0 until count) {
            val t = (k + 0.5f) / count
            if (windingNumber(Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t), lasso) != 0) {
                inside += piece
            } else if (!includePartlyEnclosed) {
                return false // wholly inside or not at all
            }
        }
        total += length
    }
    // A dot (or a stroke that never moved) is inside if its point is.
    if (total == 0f) return windingNumber(Offset(points[0].x, points[0].y), lasso) != 0
    return !includePartlyEnclosed || inside > total / 2
}

/**
 * The stroke on Page [pageIndex] a pen tap at [at] picks: one whose ink is within [slop] of it (as the eraser reaches
 * a stroke), the topmost (last in [strokes]) if several are. Null on empty space.
 */
fun strokeAt(strokes: List<InkStroke>, pageIndex: Int, at: Offset, slop: Float): Long? =
    strokes.lastOrNull { it.pageIndex == pageIndex && mayReach(it.pointBounds(), it.size, at, slop) && it.touchedBy(at, slop) }?.id

/** How many times [polygon] (closed from its last point to its first) winds around [p]; 0 means outside. */
private fun windingNumber(p: Offset, polygon: List<Offset>): Int {
    var winding = 0
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[(i + 1) % polygon.size]
        // Which side of a→b the point is on: positive is left.
        val side = (b.x - a.x) * (p.y - a.y) - (p.x - a.x) * (b.y - a.y)
        if (a.y <= p.y) {
            if (b.y > p.y && side > 0) winding++
        } else if (b.y <= p.y && side < 0) {
            winding--
        }
    }
    return winding
}

/** What a pen touch on a selection's frame hits. */
sealed interface FrameHit {
    data object RotateHandle : FrameHit

    /** A resize handle; [index] as in SelectionFrame.corners. */
    data class Corner(val index: Int) : FrameHit

    data object Inside : FrameHit

    data object Outside : FrameHit
}

/** Where the rotate handle is on screen: [distancePx] beyond the middle of the frame's top edge, along its angle. */
fun rotateHandle(frame: SelectionFrame, placement: PagePlacement, distancePx: Float): Offset {
    val topMiddle = placement.toScreen(frame.toPage(Offset(0f, -frame.halfSize.height)))
    return topMiddle + Offset(0f, -distancePx).rotated(frame.angleDegrees)
}

/**
 * What [screenPoint] hits on [frame] as shown at [placement] (SelectionFrame.shownAt, each side at least [minSidePx]):
 * the rotate handle first (rotateHandle), then a corner, both within [handleRadiusPx]; then the inside, the frame
 * widened by [insideMarginPx]; else outside. With [rotatable] false (a Symbol's frame) there is no rotate handle, so
 * [FrameHit.RotateHandle] never comes back.
 */
fun hitTest(
    frame: SelectionFrame,
    placement: PagePlacement,
    screenPoint: Offset,
    handleRadiusPx: Float,
    rotateHandleDistancePx: Float,
    insideMarginPx: Float,
    minSidePx: Float,
    rotatable: Boolean = true,
): FrameHit {
    val shown = frame.shownAt(placement, minSidePx)
    val reach = handleRadiusPx * handleRadiusPx
    if (rotatable && (rotateHandle(shown, placement, rotateHandleDistancePx) - screenPoint).getDistanceSquared() <= reach) {
        return FrameHit.RotateHandle
    }
    shown.corners().forEachIndexed { i, corner ->
        if ((placement.toScreen(corner) - screenPoint).getDistanceSquared() <= reach) return FrameHit.Corner(i)
    }
    val local = shown.toLocal(placement.toPage(screenPoint))
    val margin = insideMarginPx / placement.scale
    val inside = abs(local.x) <= shown.halfSize.width + margin && abs(local.y) <= shown.halfSize.height + margin
    return if (inside) FrameHit.Inside else FrameHit.Outside
}

private fun boundsOf(points: List<Offset>) =
    Rect(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })

/** Like Rect.overlaps, but a box with no width or height (a dot, a straight line) still counts when it touches. */
private fun Rect.meets(other: Rect) = left <= other.right && other.left <= right && top <= other.bottom && other.top <= bottom
