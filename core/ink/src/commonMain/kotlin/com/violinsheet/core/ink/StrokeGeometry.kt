package com.violinsheet.core.ink

import androidx.compose.ui.geometry.Offset
import kotlin.math.sqrt

// Point and segment measures shared by the eraser (Erasing.kt) and the lasso (Selecting.kt), in Page points.

internal fun distanceSquared(p: InkPoint, c: Offset): Float {
    val dx = p.x - c.x
    val dy = p.y - c.y
    return dx * dx + dy * dy
}

internal fun segmentLength(a: InkPoint, b: InkPoint): Float = sqrt(distanceSquared(b, Offset(a.x, a.y)))

/** The length of the line through [points]. */
internal fun length(points: List<InkPoint>): Float = (1 until points.size).sumOf { i -> segmentLength(points[i - 1], points[i]).toDouble() }.toFloat()
