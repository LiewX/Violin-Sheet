package com.violinsheet.feature.viewer

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

class PageTurnTest {
    private fun turn(
        start: Offset,
        drag: Offset,
        overscroll: Offset = Offset.Zero,
        zoomed: Boolean = false,
        leftEdge: Boolean = true,
        rightEdge: Boolean = true,
    ) = pageTurnFor(start, drag, overscroll, zoomed, width = 1000f, swipeDistance = 100f, tapSlop = 10f, leftEdge, rightEdge)

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
}
