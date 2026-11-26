package com.violinsheet.feature.viewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SheetLayoutTest {
    private val a4 = Size(595f, 842f)

    @Test fun onePageFitsHeightAndIsCentred() {
        val (rect) = pageRects(Size(1000f, 842f), listOf(a4))
        assertEquals(Rect(202.5f, 0f, 797.5f, 842f), rect)
    }

    @Test fun spreadPutsPagesSideBySideAtOneScale() {
        val (left, right) = pageRects(Size(2000f, 842f), listOf(a4, a4))
        assertEquals(Rect(405f, 0f, 1000f, 842f), left)
        assertEquals(Rect(1000f, 0f, 1595f, 842f), right)
    }

    @Test fun spreadOfUnevenPagesAlignsByCentre() {
        val (_, small) = pageRects(Size(2000f, 842f), listOf(a4, Size(595f, 421f)))
        assertEquals(210.5f, small.top)
    }

    @Test fun shownPagesInSpreadStopsAtLastPage() {
        assertEquals(listOf(3, 4), shownPages(3, spread = true, pageCount = 5))
        assertEquals(listOf(4), shownPages(4, spread = true, pageCount = 5))
        assertEquals(listOf(3), shownPages(3, spread = false, pageCount = 5))
    }

    @Test fun turnsStopAtTheEnds() {
        assertEquals(2, turnedPage(0, 1, step = 2, spread = true, pageCount = 5))
        assertEquals(4, turnedPage(2, 1, step = 2, spread = true, pageCount = 5))
        assertEquals(3, turnedPage(3, 1, step = 2, spread = true, pageCount = 5)) // [3, 4] already shows the last Page
        assertEquals(0, turnedPage(1, -1, step = 2, spread = true, pageCount = 5))
        assertEquals(0, turnedPage(0, -1, step = 1, spread = false, pageCount = 5))
        assertEquals(4, turnedPage(3, 1, step = 1, spread = false, pageCount = 5))
    }

    @Test fun advanceByOneStopsWhenLastPageIsShown() {
        assertEquals(3, turnedPage(2, 1, step = 1, spread = true, pageCount = 5)) // [2, 3] -> [3, 4]
        assertEquals(3, turnedPage(3, 1, step = 1, spread = true, pageCount = 5)) // [3, 4] -> [4] would show nothing new
    }

    private val viewport = Size(1000f, 1000f)
    private val content = Rect(0f, 0f, 1000f, 1000f)

    @Test fun zoomKeepsThePointUnderTheFingers() {
        val centroid = Offset(300f, 400f)
        val zoom = Zoom().zoomedBy(2f, centroid, viewport, content)
        assertEquals(2f, zoom.scale)
        // At fit, the content point under the fingers is the centroid itself; after zooming it must still be there.
        assertEquals(centroid, centroid * zoom.scale + zoom.offset)
    }

    @Test fun zoomingOutSnapsToFitJustAboveOne() =
        assertEquals(1f, Zoom(scale = 1.01f).zoomedBy(0.999f, Offset.Zero, viewport, content).scale)

    @Test fun slowPinchStepsAddUp() {
        var zoom = Zoom()
        repeat(20) { zoom = zoom.zoomedBy(1.005f, Offset.Zero, viewport, content) }
        assertTrue(zoom.scale > 1.1f, "scale ${zoom.scale}")
    }

    @Test fun zoomStaysBetweenFitAndMax() {
        assertEquals(1f, Zoom().zoomedBy(0.5f, Offset.Zero, viewport, content).scale)
        assertEquals(5f, Zoom().zoomedBy(10f, Offset.Zero, viewport, content).scale)
    }

    @Test fun panStopsAtContentEdgeAndReportsOverscroll() {
        val zoomed = Zoom(scale = 2f)
        val (panned, overscroll) = zoomed.pannedBy(Offset(-1500f, 0f), viewport, content)
        assertEquals(Offset(-1000f, 0f), panned.offset) // right edge of the content at the right of the screen
        assertEquals(Offset(-500f, 0f), overscroll)
    }

    @Test fun placementMapsPageCornersOntoTheZoomedFitRect() {
        val fit = Rect(405f, 0f, 1000f, 842f)
        val zoom = Zoom(scale = 2f, offset = Offset(-300f, -100f))
        val placement = zoom.placement(0, a4, fit)
        val onScreen = zoom.toScreen(fit)
        assertEquals(onScreen.topLeft, placement.toScreen(Offset.Zero))
        assertEquals(onScreen.bottomRight.x, placement.toScreen(Offset(a4.width, a4.height)).x, 0.01f)
        assertEquals(onScreen.bottomRight.y, placement.toScreen(Offset(a4.width, a4.height)).y, 0.01f)
    }

    @Test fun contentSmallerThanScreenStaysCentred() {
        val narrow = Rect(250f, 0f, 750f, 1000f)
        val (panned, overscroll) = Zoom().pannedBy(Offset(100f, 0f), viewport, narrow)
        assertEquals(Offset.Zero, panned.offset)
        assertTrue(overscroll.x == 100f)
    }
}
