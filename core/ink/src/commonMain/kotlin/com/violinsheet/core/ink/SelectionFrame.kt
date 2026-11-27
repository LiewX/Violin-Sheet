package com.violinsheet.core.ink

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

// The selection's frame, and moving, resizing and rotating the selected strokes within their Page, in Page points.

/**
 * The frame drawn around a selection, with its handles: a rectangle [halfSize] each way from [center], turned by
 * [angleDegrees] (clockwise on screen), in Page coordinates. It starts as the selection's ink box and follows each
 * action, its angle adding up, so snapping goes by the selection's total turn.
 */
data class SelectionFrame(val center: Offset, val halfSize: Size, val angleDegrees: Float) {
    /** Top-left, top-right, bottom-right and bottom-left, as the frame is turned; the opposite of corner i is (i + 2) % 4. */
    fun corners(): List<Offset> = listOf(
        toPage(Offset(-halfSize.width, -halfSize.height)),
        toPage(Offset(halfSize.width, -halfSize.height)),
        toPage(Offset(halfSize.width, halfSize.height)),
        toPage(Offset(-halfSize.width, halfSize.height)),
    )

    /** Where this frame is once [transform] has been applied to its strokes. */
    fun transformed(transform: StrokeTransform) =
        SelectionFrame(transform.apply(center), halfSize * transform.scale, angleDegrees + transform.rotationDegrees)

    /**
     * This frame as shown at [placement]: each side at least [minSidePx] long on screen, so the corners of a thin
     * selection (a straight line) stay apart and can be grabbed. For drawing and hit-testing only; the actions use the
     * frame itself.
     */
    fun shownAt(placement: PagePlacement, minSidePx: Float): SelectionFrame {
        val half = minSidePx / 2 / placement.scale
        return copy(halfSize = Size(maxOf(halfSize.width, half), maxOf(halfSize.height, half)))
    }

    /** A point given in the frame's own axes, from its centre, in Page coordinates. */
    internal fun toPage(local: Offset): Offset = center + local.rotated(angleDegrees)

    /** A Page point in the frame's own axes, from its centre. */
    internal fun toLocal(page: Offset): Offset = (page - center).rotated(-angleDegrees)

    companion object {
        /** The frame of a fresh selection: the ink box of [strokes] (not empty), unturned. */
        fun around(strokes: List<InkStroke>): SelectionFrame {
            val box = inkBounds(strokes)
            return SelectionFrame(box.center, box.size / 2f, 0f)
        }
    }
}

/** The box this stroke's ink covers: its point box widened by half its size. */
fun InkStroke.inkBounds(): Rect = pointBounds().inflate(size / 2)

/** The box the ink of all [strokes] covers. [strokes] must not be empty. */
fun inkBounds(strokes: List<InkStroke>): Rect = strokes.map { it.inkBounds() }.reduce { a, b ->
    Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
}

/** A resize never makes the frame's shorter side smaller than this (Page points), unless it already was. */
private const val MIN_FRAME_SIDE = 2f

/** A total turn within this many degrees of a right angle snaps to it. */
private const val SNAP_DEGREES = 5f

/**
 * A move by [delta] of ink whose box is [box], stopped at the edges of [page]. Ink that already sat past an edge is
 * never pushed further out.
 */
fun move(box: Rect, page: Rect, delta: Offset): StrokeTransform = StrokeTransform(
    translation = Offset(
        delta.x.coerceIn(minOf(0f, page.left - box.left), maxOf(0f, page.right - box.right)),
        delta.y.coerceIn(minOf(0f, page.top - box.top), maxOf(0f, page.bottom - box.bottom)),
    ),
)

/**
 * A resize with [corner] of [frame] dragged to [to]: a uniform scale about the opposite corner, as far as the drag goes
 * along the frame's diagonal. Stopped where the ink box [box] meets an edge of [page] (ink boxes scale linearly about
 * the corner, since sizes scale too), and where the frame's shorter side reaches [MIN_FRAME_SIDE].
 */
fun resize(frame: SelectionFrame, box: Rect, page: Rect, corner: Int, to: Offset): StrokeTransform {
    val corners = frame.corners()
    val anchor = corners[(corner + 2) % 4]
    val diagonal = corners[corner] - anchor
    val diagonalSquared = diagonal.getDistanceSquared()
    if (diagonalSquared == 0f) return StrokeTransform()
    val wanted = ((to.x - anchor.x) * diagonal.x + (to.y - anchor.y) * diagonal.y) / diagonalSquared
    val shorter = frame.halfSize.minDimension * 2
    var smallest = if (shorter > 0f) minOf(1f, MIN_FRAME_SIDE / shorter) else 1f
    // An anchor past a Page edge (a turned frame near the edge): shrinking pulls the ink towards it, so stop where the
    // ink reaches that edge; ink already past it may stay as it is (1).
    if (anchor.x < page.left && box.left > anchor.x) smallest = maxOf(smallest, minOf(1f, (page.left - anchor.x) / (box.left - anchor.x)))
    if (anchor.x > page.right && box.right < anchor.x) smallest = maxOf(smallest, minOf(1f, (anchor.x - page.right) / (anchor.x - box.right)))
    if (anchor.y < page.top && box.top > anchor.y) smallest = maxOf(smallest, minOf(1f, (page.top - anchor.y) / (box.top - anchor.y)))
    if (anchor.y > page.bottom && box.bottom < anchor.y) smallest = maxOf(smallest, minOf(1f, (anchor.y - page.bottom) / (anchor.y - box.bottom)))
    // Per edge, the scale at which it reaches the Page edge; an edge already past it may stay as it is (1).
    var largest = Float.POSITIVE_INFINITY
    if (box.left < anchor.x) largest = minOf(largest, maxOf(1f, (anchor.x - page.left) / (anchor.x - box.left)))
    if (box.right > anchor.x) largest = minOf(largest, maxOf(1f, (page.right - anchor.x) / (box.right - anchor.x)))
    if (box.top < anchor.y) largest = minOf(largest, maxOf(1f, (anchor.y - page.top) / (anchor.y - box.top)))
    if (box.bottom > anchor.y) largest = minOf(largest, maxOf(1f, (page.bottom - anchor.y) / (box.bottom - anchor.y)))
    return StrokeTransform(scale = wanted.coerceAtMost(largest).coerceAtLeast(smallest), pivot = anchor)
}

/**
 * A rotation of [strokes] in [frame] about its centre, by the angle the drag from [from] to [to] turns through, the
 * frame's total angle snapped (snapAngle). The turned ink is then shifted inside [page] (keepInside); a turn that
 * snaps back to none isn't shifted, so ink already past an edge doesn't make it a step.
 */
fun rotate(frame: SelectionFrame, strokes: List<InkStroke>, page: Rect, from: Offset, to: Offset): StrokeTransform {
    val c = frame.center
    val turned = (atan2(to.y - c.y, to.x - c.x) - atan2(from.y - c.y, from.x - c.x)) * 180f / PI.toFloat()
    val turn = StrokeTransform(rotationDegrees = snapAngle(frame.angleDegrees + turned) - frame.angleDegrees, pivot = c)
    if (turn.rotationDegrees == 0f) return turn
    // The turned ink box, measured point by point: this runs on every pen move, so no stroke is copied.
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    for (stroke in strokes) {
        val half = stroke.size / 2
        for (p in stroke.points) {
            val q = turn.apply(Offset(p.x, p.y))
            left = minOf(left, q.x - half)
            top = minOf(top, q.y - half)
            right = maxOf(right, q.x + half)
            bottom = maxOf(bottom, q.y + half)
        }
    }
    return turn.copy(translation = keepInside(Rect(left, top, right, bottom), page))
}

/** [degrees], or the right angle (0°, 90°, 180°, 270°) it is within [SNAP_DEGREES] of. */
fun snapAngle(degrees: Float): Float {
    val right = (degrees / 90f).roundToInt() * 90f
    return if (abs(degrees - right) <= SNAP_DEGREES) right else degrees
}

/**
 * The smallest move that puts [box] inside [page]. On an axis where the box is bigger than the Page, it centres the
 * box instead.
 */
fun keepInside(box: Rect, page: Rect): Offset = Offset(
    shiftInside(box.left, box.right, page.left, page.right),
    shiftInside(box.top, box.bottom, page.top, page.bottom),
)

private fun shiftInside(low: Float, high: Float, pageLow: Float, pageHigh: Float): Float = when {
    high - low > pageHigh - pageLow -> (pageLow + pageHigh) / 2 - (low + high) / 2
    low < pageLow -> pageLow - low
    high > pageHigh -> pageHigh - high
    else -> 0f
}
