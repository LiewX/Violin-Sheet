package com.violinsheet.feature.viewer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp

/** What finger gestures on the Sheet do. The pen is ignored: it draws (CLAUDE.md: input split). */
internal interface SheetGestureHandler {
    val zoomed: Boolean

    /** Two fingers: zoom by [factor] around [centroid] and move by [pan]. */
    fun transform(centroid: Offset, pan: Offset, factor: Float)

    /** One finger while zoomed. Returns the overscroll: the part of [delta] stopped at the content's edge. */
    fun pan(delta: Offset): Offset

    fun turn(direction: Int)
}

/** Centroid, pan and zoom factor of two or more fingers since the last event. */
private fun pinchOf(fingers: List<PointerInputChange>): Triple<Offset, Offset, Float> {
    fun centroid(positions: List<Offset>) = positions.reduce { a, b -> a + b } / positions.size.toFloat()
    fun spread(positions: List<Offset>, centre: Offset) = positions.sumOf { (it - centre).getDistance().toDouble() }.toFloat()
    val now = fingers.map { it.position }
    val before = fingers.map { it.previousPosition }
    val centre = centroid(now)
    val previousCentre = centroid(before)
    val previousSpread = spread(before, previousCentre)
    val zoom = if (previousSpread > 0f) spread(now, centre) / previousSpread else 1f
    return Triple(centre, centre - previousCentre, zoom)
}

/** Pinch zooms; one finger pans when zoomed; swipes (past the edge when zoomed) and edge taps turn the page. */
@Composable
internal fun Modifier.sheetGestures(handler: SheetGestureHandler): Modifier {
    val current by rememberUpdatedState(handler)
    return pointerInput(Unit) {
        val swipeDistance = 48.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.type != PointerType.Touch) return@awaitEachGesture
            val zoomedAtStart = current.zoomed
            var drag = Offset.Zero
            var overscroll = Offset.Zero
            var multiTouch = false
            while (true) {
                val event = awaitPointerEvent()
                // Fingers only: the pen's changes are left unconsumed for the Ink canvas (#6).
                val fingers = event.changes.filter { it.type == PointerType.Touch && it.pressed }
                if (fingers.isEmpty()) break
                if (fingers.size > 1) {
                    multiTouch = true
                    val (centroid, pan, zoom) = pinchOf(fingers)
                    current.transform(centroid, pan, zoom)
                } else if (!multiTouch) {
                    val delta = fingers.first().positionChange()
                    drag += delta
                    if (zoomedAtStart) overscroll += current.pan(delta)
                }
                fingers.forEach { it.consume() }
            }
            // A pinch never turns the page, even when it ends with one finger.
            if (multiTouch) return@awaitEachGesture
            val turn = pageTurnFor(
                down.position, drag, overscroll, zoomedAtStart, size.width.toFloat(), swipeDistance, viewConfiguration.touchSlop,
            )
            if (turn != 0) current.turn(turn)
        }
    }
}
