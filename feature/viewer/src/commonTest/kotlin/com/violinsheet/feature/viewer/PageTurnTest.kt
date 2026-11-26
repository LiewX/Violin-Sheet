package com.violinsheet.feature.viewer

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

class PageTurnTest {
    private fun turn(start: Offset, end: Offset) =
        pageTurnFor(start, end, width = 1000f, swipeDistance = 100f, tapSlop = 10f)

    @Test fun swipeLeftTurnsForward() = assertEquals(1, turn(Offset(600f, 500f), Offset(300f, 520f)))

    @Test fun swipeRightTurnsBack() = assertEquals(-1, turn(Offset(300f, 500f), Offset(600f, 480f)))

    @Test fun shortSwipeDoesNothing() = assertEquals(0, turn(Offset(500f, 500f), Offset(450f, 500f)))

    @Test fun verticalDragDoesNothing() = assertEquals(0, turn(Offset(500f, 200f), Offset(400f, 700f)))

    @Test fun rightEdgeTapTurnsForward() = assertEquals(1, turn(Offset(950f, 500f), Offset(952f, 501f)))

    @Test fun leftEdgeTapTurnsBack() = assertEquals(-1, turn(Offset(50f, 500f), Offset(50f, 500f)))

    @Test fun middleTapDoesNothing() = assertEquals(0, turn(Offset(500f, 500f), Offset(500f, 500f)))

    @Test fun edgeDragBelowSwipeDistanceDoesNothing() = assertEquals(0, turn(Offset(950f, 500f), Offset(900f, 500f)))
}
