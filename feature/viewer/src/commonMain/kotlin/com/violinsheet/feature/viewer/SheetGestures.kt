package com.violinsheet.feature.viewer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp

/** What finger gestures on the Sheet do. The pen is ignored: it draws (CLAUDE.md: input split). */
internal interface SheetGestureHandler {
    val zoomed: Boolean

    /** Whether the content's left / right edge is on screen now. */
    val atLeftEdge: Boolean
    val atRightEdge: Boolean

    /** A finger gesture begins: remember the view, in case it turns out to be a palm. */
    fun beginGesture()

    /** The gesture was a palm: put the view back as it was when the gesture began. */
    fun revertGesture()

    /** Two fingers: zoom by [factor] around [centroid] and move by [pan]. */
    fun transform(centroid: Offset, pan: Offset, factor: Float)

    /** One finger while zoomed. Returns the overscroll: the part of [delta] stopped at the content's edge. */
    fun pan(delta: Offset): Offset

    fun turn(direction: Int)

    /**
     * One finger tapped (quick, within the touch slop, not a palm), [atEdge] in a Page-turn edge or not. Comes just
     * before the edge tap's [turn].
     */
    fun tapped(atEdge: Boolean)
}

/** Two or more fingers now: their [centroid] and [spread], and the [pan] and [zoom] factor since the last event. */
private class Pinch(val centroid: Offset, val spread: Float, val pan: Offset, val zoom: Float)

private fun pinchOf(fingers: List<PointerInputChange>): Pinch {
    val now = fingers.map { it.position }
    val before = fingers.map { it.previousPosition }
    val centre = centroidOf(now)
    val spread = spreadOf(now, centre)
    val previousCentre = centroidOf(before)
    val previousSpread = spreadOf(before, previousCentre)
    val zoom = if (previousSpread > 0f) spread / previousSpread else 1f
    return Pinch(centre, spread, centre - previousCentre, zoom)
}

/**
 * Whether [changes], the event that ended a finger gesture, is a cancel (the system took the touch away, e.g. its own
 * palm rejection) rather than a lift. Compose sends a cancel as every pressed pointer going up already consumed
 * (`SuspendingPointerInputModifierNodeImpl.onCancelPointerInput`); a real finger lift arrives unconsumed, since
 * nothing under the Sheet consumes fingers (the Ink canvas takes only the pen).
 */
internal fun isCancel(changes: List<PointerInputChange>): Boolean =
    changes.any { it.type == PointerType.Touch && it.previousPressed && !it.pressed && it.isConsumed }

/**
 * Pinch zooms; one finger pans when zoomed; swipes (past the edge when zoomed) and edge taps turn the page. A finger
 * that is really a palm resting while writing does nothing: see [PalmGuard].
 */
@Composable
internal fun Modifier.sheetGestures(handler: SheetGestureHandler): Modifier {
    val current by rememberUpdatedState(handler)
    val palms = remember { PalmGuard() }
    // Watches every event for the pen, hovering or touching, before anything consumes it (Initial pass).
    val penWatch = pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.firstOrNull { it.type == PointerType.Stylus }?.let { palms.penSeen(it.uptimeMillis) }
            }
        }
    }
    return penWatch.pointerInput(Unit) {
        val swipeDistance = 48.dp.toPx()
        val pinchCentreSlop = PINCH_CENTRE_DP.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.type != PointerType.Touch) return@awaitEachGesture
            // The pen is near: this touch is the writing hand. Sit the gesture out (awaitEachGesture waits for the lift).
            if (palms.isPalm(down.uptimeMillis)) return@awaitEachGesture
            current.beginGesture()
            val zoomedAtStart = current.zoomed
            val leftEdgeAtStart = current.atLeftEdge
            val rightEdgeAtStart = current.atRightEdge
            var drag = Offset.Zero
            var overscroll = Offset.Zero
            var multiTouch = false
            val gate = PinchGate(pinchCentreSlop, viewConfiguration.touchSlop)
            while (true) {
                val event = awaitPointerEvent()
                if (palms.penSeenSince(down.uptimeMillis)) {
                    // The pen arrived after the touch: it was a palm landing first. Undo what it moved, then sit out.
                    // shortcut: a pen that lands without hovering first starts its stroke before this revert, so that
                    // stroke is placed against the moved view. Hover normally comes first; revisit if strokes jump.
                    current.revertGesture()
                    return@awaitEachGesture
                }
                // Fingers only: the pen's changes are left unconsumed for the Ink canvas (#6).
                val fingers = event.changes.filter { it.type == PointerType.Touch && it.pressed }
                if (fingers.isEmpty()) {
                    // A pinch never turns the page, even when it ends with one finger; nor does a cancelled touch.
                    if (multiTouch || isCancel(event.changes)) return@awaitEachGesture
                    val liftMillis = event.changes.maxOf { it.uptimeMillis }
                    val tap = fingerTapOf(
                        down.position, drag, size.width.toFloat(), viewConfiguration.touchSlop, liftMillis - down.uptimeMillis,
                    )
                    // Reported before the turn, so a selection is cleared before its Page leaves the screen.
                    if (tap != null) current.tapped(atEdge = tap is FingerTap.Edge)
                    val turn = pageTurnFor(
                        down.position, drag, overscroll, zoomedAtStart, size.width.toFloat(), swipeDistance,
                        viewConfiguration.touchSlop, leftEdgeAtStart, rightEdgeAtStart, liftMillis - down.uptimeMillis,
                    )
                    if (turn != 0) current.turn(turn)
                    return@awaitEachGesture
                }
                if (fingers.size > 1) {
                    multiTouch = true
                    val pinch = pinchOf(fingers)
                    // A hand resting with two fingers that barely move does nothing: see PinchGate.
                    if (gate.shouldApply(fingers.map { it.id.value }.toSet(), pinch.spread, pinch.centroid)) {
                        current.transform(pinch.centroid, pinch.pan, pinch.zoom)
                    }
                } else if (!multiTouch) {
                    val delta = fingers.first().positionChange()
                    drag += delta
                    if (zoomedAtStart) overscroll += current.pan(delta)
                }
                fingers.forEach { it.consume() }
            }
        }
    }
}
