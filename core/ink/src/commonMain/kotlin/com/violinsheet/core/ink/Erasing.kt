package com.violinsheet.core.ink

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.ceil
import kotlin.math.sqrt

// The eraser's geometry, in Page points. The eraser is a circle; it reaches a stroke's ink, not just its centre line,
// so a stroke is within reach when its line comes within the eraser's radius plus half the stroke's size.

/** Pieces shorter than this (Page points) are too short to see, so a cut drops them. */
private const val MIN_PIECE_LENGTH = 0.5f

/**
 * A line that only grazes the eraser's edge doesn't count as under it. Without this margin, the ends a cut leaves
 * (exactly on the edge) would be cut again, into the same pieces, at every eraser position.
 */
private const val EDGE_MARGIN = 0.999f

/** The box around this stroke's points (not its ink). Cache it: it walks every point. */
fun InkStroke.pointBounds(): Rect {
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    for (p in points) {
        left = minOf(left, p.x)
        top = minOf(top, p.y)
        right = maxOf(right, p.x)
        bottom = maxOf(bottom, p.y)
    }
    return Rect(left, top, right, bottom)
}

/** Whether an eraser circle of [radius] at [center] could reach a stroke of [size] whose points lie in [pointBounds]. */
fun mayReach(pointBounds: Rect, size: Float, center: Offset, radius: Float): Boolean {
    val reach = radius + size / 2
    return center.x >= pointBounds.left - reach && center.x <= pointBounds.right + reach &&
        center.y >= pointBounds.top - reach && center.y <= pointBounds.bottom + reach
}

/** Whether an eraser circle of [radius] at [center] touches this stroke's ink. */
fun InkStroke.touchedBy(center: Offset, radius: Float): Boolean {
    val reach = radius + size / 2
    if (points.size == 1) return distanceSquared(points[0], center) <= reach * reach
    return (1 until points.size).any { i -> segmentDistanceSquared(points[i - 1], points[i], center) <= reach * reach }
}

/**
 * What an area eraser circle of [radius] at [center] leaves of this stroke: null if it doesn't touch it, otherwise the
 * points of each piece left, in order (none if it was all under the eraser). A piece ends where the stroke's ink enters
 * the eraser; pieces too short to see are dropped.
 */
fun InkStroke.cutBy(center: Offset, radius: Float): List<List<InkPoint>>? {
    val reach = radius + size / 2
    if (points.size == 1) return if (distanceSquared(points[0], center) <= reach * reach) emptyList() else null
    val pieces = mutableListOf<List<InkPoint>>()
    var current = mutableListOf<InkPoint>()
    var touched = false
    if (distanceSquared(points[0], center) > reach * reach) current += points[0]
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        val under = underEraser(a, b, center, reach)
        if (under == null) {
            current.addDistinct(b)
            continue
        }
        touched = true
        val (enter, leave) = under
        if (enter > 0f) {
            current.addDistinct(a.lerp(b, enter))
            pieces += current
            current = mutableListOf()
        }
        if (leave < 1f) {
            current = mutableListOf(a.lerp(b, leave))
            current.addDistinct(b)
        }
    }
    if (!touched) return null
    pieces += current
    return pieces.filter { it.size >= 2 && length(it) >= MIN_PIECE_LENGTH }
}

/**
 * Eraser positions from [from] to [to], at most [step] apart (excluding [from], including [to]), so a fast eraser
 * still sweeps everything between two input samples. [from] null: the eraser just touched down at [to].
 */
fun eraserPath(from: Offset?, to: Offset, step: Float): List<Offset> {
    if (from == null) return listOf(to)
    val count = ceil((to - from).getDistance() / step).toInt().coerceIn(1, 1000)
    return List(count) { i -> from + (to - from) * ((i + 1f) / count) }
}

/**
 * The part of segment [a]–[b] within [reach] of [center], as the segment's parameters (0 at [a], 1 at [b]) where the
 * ink enters and leaves the eraser; null if the segment stays outside (or only grazes the edge).
 */
private fun underEraser(a: InkPoint, b: InkPoint, center: Offset, reach: Float): Pair<Float, Float>? {
    val inner = reach * EDGE_MARGIN
    if (segmentDistanceSquared(a, b, center) > inner * inner) return null
    val dx = b.x - a.x
    val dy = b.y - a.y
    val fx = a.x - center.x
    val fy = a.y - center.y
    val aa = dx * dx + dy * dy
    if (aa == 0f) return 0f to 1f // both ends at the same spot, under the eraser
    val bb = 2 * (fx * dx + fy * dy)
    val cc = fx * fx + fy * fy - reach * reach
    val root = sqrt((bb * bb - 4 * aa * cc).coerceAtLeast(0f))
    return ((-bb - root) / (2 * aa)).coerceIn(0f, 1f) to ((-bb + root) / (2 * aa)).coerceIn(0f, 1f)
}

private fun distanceSquared(p: InkPoint, c: Offset): Float {
    val dx = p.x - c.x
    val dy = p.y - c.y
    return dx * dx + dy * dy
}

private fun segmentDistanceSquared(a: InkPoint, b: InkPoint, c: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lengthSquared = dx * dx + dy * dy
    val t = if (lengthSquared == 0f) 0f else (((c.x - a.x) * dx + (c.y - a.y) * dy) / lengthSquared).coerceIn(0f, 1f)
    val px = a.x + t * dx - c.x
    val py = a.y + t * dy - c.y
    return px * px + py * py
}

private fun InkPoint.lerp(to: InkPoint, t: Float) = InkPoint(
    x + (to.x - x) * t,
    y + (to.y - y) * t,
    pressure + (to.pressure - pressure) * t,
    elapsedMillis + ((to.elapsedMillis - elapsedMillis) * t).toLong(),
)

/** The Ink API rejects a point that repeats the one before it. */
private fun MutableList<InkPoint>.addDistinct(point: InkPoint) {
    val last = lastOrNull()
    if (last == null || last.x != point.x || last.y != point.y || last.elapsedMillis != point.elapsedMillis) add(point)
}

private fun length(points: List<InkPoint>): Float =
    (1 until points.size).sumOf { i -> sqrt(distanceSquared(points[i], Offset(points[i - 1].x, points[i - 1].y))).toDouble() }.toFloat()
