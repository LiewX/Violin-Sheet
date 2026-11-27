package com.violinsheet.feature.viewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PageTurnTest {
    private fun turn(
        start: Offset,
        drag: Offset,
        overscroll: Offset = Offset.Zero,
        zoomed: Boolean = false,
        leftEdge: Boolean = true,
        rightEdge: Boolean = true,
        durationMillis: Long = 100,
    ) = pageTurnFor(
        start, drag, overscroll, zoomed, width = 1000f, swipeDistance = 100f, tapSlop = 10f, leftEdge, rightEdge, durationMillis,
    )

    private val middle = Offset(500f, 500f)

    @Test fun swipeLeftTurnsForward() = assertEquals(1, turn(middle, Offset(-300f, 20f)))

    @Test fun swipeRightTurnsBack() = assertEquals(-1, turn(middle, Offset(300f, -20f)))

    @Test fun shortSwipeDoesNothing() = assertEquals(0, turn(middle, Offset(-50f, 0f)))

    @Test fun verticalDragDoesNothing() = assertEquals(0, turn(middle, Offset(-100f, 500f)))

    @Test fun rightEdgeTapTurnsForward() = assertEquals(1, turn(Offset(950f, 500f), Offset(2f, 1f)))

    @Test fun leftEdgeTapTurnsBack() = assertEquals(-1, turn(Offset(50f, 500f), Offset.Zero))

    @Test fun middleTapDoesNothing() = assertEquals(0, turn(middle, Offset.Zero))

    @Test fun edgeDragBelowSwipeDistanceDoesNothing() = assertEquals(0, turn(Offset(950f, 500f), Offset(-50f, 0f)))

    @Test fun zoomedSwipeThatOnlyPansDoesNothing() =
        assertEquals(0, turn(middle, Offset(-300f, 0f), overscroll = Offset(-20f, 0f), zoomed = true))

    @Test fun zoomedSwipeStartingAtTheEdgeAndGoingWellPastItTurns() =
        assertEquals(1, turn(middle, Offset(-400f, 0f), overscroll = Offset(-250f, 0f), zoomed = true))

    @Test fun zoomedSwipeOnlySlightlyPastTheEdgeDoesNothing() =
        assertEquals(0, turn(middle, Offset(-400f, 0f), overscroll = Offset(-150f, 0f), zoomed = true))

    @Test fun zoomedPanThatReachesTheEdgeOnTheWayDoesNothing() =
        assertEquals(0, turn(middle, Offset(-900f, 0f), overscroll = Offset(-400f, 0f), zoomed = true, rightEdge = false))

    @Test fun zoomedSwipeBackNeedsTheLeftEdgeAtTheStart() {
        assertEquals(0, turn(middle, Offset(900f, 0f), overscroll = Offset(400f, 0f), zoomed = true, leftEdge = false))
        assertEquals(-1, turn(middle, Offset(900f, 0f), overscroll = Offset(400f, 0f), zoomed = true, rightEdge = false))
    }

    @Test fun zoomedEdgeTapTurns() = assertEquals(-1, turn(Offset(50f, 500f), Offset.Zero, zoomed = true))

    @Test fun quickEdgeTapTurns() = assertEquals(1, turn(Offset(950f, 500f), Offset.Zero, durationMillis = 200))

    @Test fun heldEdgeTouchDoesNothing() = assertEquals(0, turn(Offset(950f, 500f), Offset.Zero, durationMillis = 300))

    @Test fun quickTapLimitIs250Millis() {
        assertEquals(true, isQuickTap(250))
        assertEquals(false, isQuickTap(251))
    }

    @Test fun flickTurns() = assertEquals(1, turn(middle, Offset(-300f, 0f), durationMillis = 350))

    @Test fun slowDragDoesNothing() = assertEquals(0, turn(middle, Offset(-300f, 0f), durationMillis = 500))

    @Test fun flickLimitIs400Millis() {
        assertEquals(1, turn(middle, Offset(-300f, 0f), durationMillis = 400))
        assertEquals(0, turn(middle, Offset(-300f, 0f), durationMillis = 401))
    }

    @Test fun heldZoomedEdgeTouchDoesNothing() =
        assertEquals(0, turn(Offset(50f, 500f), Offset.Zero, zoomed = true, durationMillis = 300))

    @Test fun liftIsNotACancel() = assertFalse(isCancel(listOf(fingerUp(consumed = false))))

    @Test fun cancelledTouchIsACancel() = assertTrue(isCancel(listOf(fingerUp(consumed = true))))

    /** A finger going up, as Compose sends it: a lift unconsumed, a cancel already consumed. */
    private fun fingerUp(consumed: Boolean) = PointerInputChange(
        id = PointerId(1), uptimeMillis = 1_000, position = Offset(950f, 500f), pressed = false,
        previousUptimeMillis = 1_000, previousPosition = Offset(950f, 500f), previousPressed = true,
        isInitiallyConsumed = consumed,
    )

    @Test fun zoomedFlickPastTheEdgeTurns() =
        assertEquals(1, turn(middle, Offset(-400f, 0f), overscroll = Offset(-250f, 0f), zoomed = true, durationMillis = 350))

    @Test fun zoomedSlowDragPastTheEdgeDoesNothing() =
        assertEquals(0, turn(middle, Offset(-400f, 0f), overscroll = Offset(-250f, 0f), zoomed = true, durationMillis = 500))
}
