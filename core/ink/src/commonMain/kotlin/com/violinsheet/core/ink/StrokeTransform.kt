package com.violinsheet.core.ink

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A lasso action as one change of Page coordinates: scale by [scale] and turn by [rotationDegrees] (clockwise on
 * screen) about [pivot], then move by [translation]. Applied to a stroke it scales the size too, and keeps pressure
 * and timing. The ViewModel bakes it into the points when the pen lifts; nothing stores it (ADR 0002).
 */
data class StrokeTransform(
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
    val translation: Offset = Offset.Zero,
    val pivot: Offset = Offset.Zero,
) {
    val isIdentity: Boolean get() = scale == 1f && rotationDegrees == 0f && translation == Offset.Zero

    fun apply(point: Offset): Offset = pivot + ((point - pivot) * scale).rotated(rotationDegrees) + translation

    fun apply(point: InkPoint): InkPoint = apply(Offset(point.x, point.y)).let { point.copy(x = it.x, y = it.y) }

    fun apply(stroke: InkStroke): InkStroke = stroke.copy(size = stroke.size * scale, points = stroke.points.map(::apply))
}

/** This vector turned by [degrees], clockwise on screen (y points down). */
internal fun Offset.rotated(degrees: Float): Offset {
    if (degrees == 0f) return this
    val r = degrees * PI.toFloat() / 180f
    val cos = cos(r)
    val sin = sin(r)
    return Offset(x * cos - y * sin, x * sin + y * cos)
}
