package com.violinsheet.feature.viewer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Share of the width on each side where a tap turns the page. */
private const val EDGE_FRACTION = 0.2f

/**
 * Page turn for one finger gesture from [start] to [end] on a view [width] wide:
 * +1 forward, -1 back, 0 none. A horizontal swipe past [swipeDistance] turns the page,
 * and so does a tap (moving less than [tapSlop]) near the left or right edge.
 */
internal fun pageTurnFor(start: Offset, end: Offset, width: Float, swipeDistance: Float, tapSlop: Float): Int {
    val dx = end.x - start.x
    val dy = end.y - start.y
    return when {
        abs(dx) > swipeDistance && abs(dx) > abs(dy) -> if (dx < 0) 1 else -1
        (end - start).getDistance() > tapSlop -> 0
        start.x < width * EDGE_FRACTION -> -1
        start.x > width * (1 - EDGE_FRACTION) -> 1
        else -> 0
    }
}

/** Finger swipes and edge taps turn the page. The pen is ignored: it draws (CLAUDE.md: input split). */
internal fun Modifier.pageTurnGestures(onTurn: (Int) -> Unit): Modifier = pointerInput(onTurn) {
    val swipeDistance = 48.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.type != PointerType.Touch) return@awaitEachGesture
        var end = down.position
        var multiTouch = false
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.size > 1) multiTouch = true
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            end = change.position
            if (!change.pressed) break
        }
        // Two fingers will be pinch-zoom (#4), not a page turn.
        if (multiTouch) return@awaitEachGesture
        val turn = pageTurnFor(down.position, end, size.width.toFloat(), swipeDistance, viewConfiguration.touchSlop)
        if (turn != 0) onTurn(turn)
    }
}
