package com.violinsheet.core.ink

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

class SelectingTest {
    /** A stroke through [xs] at height [y], 1 pt thick, on Page [page]. */
    private fun stroke(id: Long, xs: List<Float>, y: Float = 50f, page: Int = 0, size: Float = 1f) =
        InkStroke(id, page, 0, size, xs.mapIndexed { i, x -> InkPoint(x, y, 0.5f, i.toLong()) }, StrokeTool.Pen)

    /** A straight stroke from x = 0 to 100 at height [y], a point every 1 pt. */
    private fun line(id: Long, y: Float = 50f, page: Int = 0) = stroke(id, List(101) { it.toFloat() }, y, page)

    private fun rect(left: Float, top: Float, right: Float, bottom: Float) =
        listOf(Offset(left, top), Offset(right, top), Offset(right, bottom), Offset(left, bottom))

    @Test fun onlyAStrokeWhollyInsideIsSelectedByDefault() {
        // 95% inside: not selected, unless partly enclosed strokes are included.
        assertEquals(emptyList(), lassoSelects(listOf(line(1)), 0, rect(5f, 0f, 110f, 100f), includePartlyEnclosed = false))
        assertEquals(listOf(1L), lassoSelects(listOf(line(1)), 0, rect(5f, 0f, 110f, 100f), includePartlyEnclosed = true))
        // Wholly inside: selected either way.
        assertEquals(listOf(1L), lassoSelects(listOf(line(1)), 0, rect(-10f, 0f, 110f, 100f), includePartlyEnclosed = false))
        assertEquals(listOf(1L), lassoSelects(listOf(line(1)), 0, rect(-10f, 0f, 110f, 100f), includePartlyEnclosed = true))
    }

    @Test fun includingPartlyEnclosedSelectsAStrokeMostlyInsideAndNotOneMostlyOutside() {
        assertEquals(listOf(1L), lassoSelects(listOf(line(1)), 0, rect(-10f, 0f, 60f, 100f), includePartlyEnclosed = true))
        assertEquals(emptyList(), lassoSelects(listOf(line(1)), 0, rect(-10f, 0f, 40f, 100f), includePartlyEnclosed = true))
    }

    @Test fun crowdedPointsDoNotCountForMore() {
        // 45 pt outside, drawn slowly (a point every 0.1 pt), then 55 pt inside in one segment.
        val xs = List(451) { it / 10f } + 100f
        assertEquals(listOf(1L), lassoSelects(listOf(stroke(1, xs)), 0, rect(44f, 0f, 110f, 100f), includePartlyEnclosed = true))
        assertEquals(emptyList(), lassoSelects(listOf(stroke(1, xs)), 0, rect(-10f, 0f, 46f, 100f), includePartlyEnclosed = true))
    }

    @Test fun aTapPicksTheTopmostStrokeWithinReach() {
        val lower = line(1, y = 50f)
        val upper = line(2, y = 52f)
        val other = line(3, y = 50f, page = 1)
        // Both within reach (half the 1 pt size plus 2 pt): the later one, drawn on top, wins.
        assertEquals(2L, strokeAt(listOf(lower, upper, other), 0, Offset(30f, 51f), slop = 2f))
        // Only the lower one within reach.
        assertEquals(1L, strokeAt(listOf(lower, upper, other), 0, Offset(30f, 48f), slop = 2f))
        // Empty space, and a stroke on another Page.
        assertEquals(null, strokeAt(listOf(lower, upper), 0, Offset(30f, 80f), slop = 2f))
        assertEquals(null, strokeAt(listOf(other), 0, Offset(30f, 50f), slop = 2f))
    }

    @Test fun dotInsideIsSelected() {
        val dots = listOf(stroke(1, listOf(30f)), stroke(2, listOf(80f)))
        assertEquals(listOf(1L), lassoSelects(dots, 0, rect(0f, 0f, 50f, 100f), includePartlyEnclosed = false))
    }

    @Test fun highlighterStrokeIsSelectedLikeAPenStroke() {
        val highlighter = line(1).copy(tool = StrokeTool.Highlighter, size = 12f)
        assertEquals(listOf(1L), lassoSelects(listOf(highlighter), 0, rect(-10f, 0f, 110f, 100f), includePartlyEnclosed = false))
    }

    @Test fun strokesOnAnotherPageAreSkipped() {
        assertEquals(listOf(1L), lassoSelects(listOf(line(1), line(2, page = 1)), 0, rect(-10f, 0f, 110f, 100f), includePartlyEnclosed = false))
    }

    @Test fun figureEightLassoCatchesBothLoops() {
        // A bow tie: the left and right loops wind opposite ways, and both are inside (nonzero winding).
        val lasso = listOf(Offset(0f, 0f), Offset(100f, 100f), Offset(100f, 0f), Offset(0f, 100f))
        val left = InkStroke(1, 0, 0, 1f, listOf(InkPoint(10f, 30f, 0.5f, 0), InkPoint(10f, 70f, 0.5f, 1)), StrokeTool.Pen)
        val right = InkStroke(2, 0, 0, 1f, listOf(InkPoint(90f, 30f, 0.5f, 0), InkPoint(90f, 70f, 0.5f, 1)), StrokeTool.Pen)
        val between = stroke(3, listOf(40f, 60f), y = 10f)
        assertEquals(listOf(1L, 2L), lassoSelects(listOf(left, right, between), 0, lasso, includePartlyEnclosed = false))
    }

    @Test fun openLassoIsClosedFromItsLastPointToItsFirst() {
        val u = listOf(Offset(-10f, 0f), Offset(-10f, 100f), Offset(110f, 100f), Offset(110f, 0f))
        assertEquals(listOf(1L), lassoSelects(listOf(line(1)), 0, u, includePartlyEnclosed = false))
    }

    private val page = Rect(0f, 0f, 200f, 300f)

    /** A 2 pt stroke from [from] to [to]. */
    private fun segment(from: Offset, to: Offset) =
        InkStroke(1, 0, 0, 2f, listOf(InkPoint(from.x, from.y, 0.5f, 0), InkPoint(to.x, to.y, 0.7f, 5)), StrokeTool.Pen)

    private fun assertBox(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, 0.01f, "left")
        assertEquals(expected.top, actual.top, 0.01f, "top")
        assertEquals(expected.right, actual.right, 0.01f, "right")
        assertEquals(expected.bottom, actual.bottom, 0.01f, "bottom")
    }

    private fun assertPoint(expected: Offset, actual: InkPoint) {
        assertEquals(expected.x, actual.x, 0.01f, "x")
        assertEquals(expected.y, actual.y, 0.01f, "y")
    }

    @Test fun inkBoundsWidenThePointBoxByHalfTheSize() {
        assertBox(Rect(49f, 99f, 101f, 101f), inkBounds(listOf(segment(Offset(50f, 100f), Offset(100f, 100f)))))
    }

    @Test fun moveStopsAtEachPageEdge() {
        val box = inkBounds(listOf(segment(Offset(50f, 100f), Offset(100f, 100f)))) // 49, 99, 101, 101
        assertEquals(Offset(-49f, 0f), move(box, page, Offset(-500f, 0f)).translation)
        assertEquals(Offset(99f, 0f), move(box, page, Offset(500f, 0f)).translation)
        assertEquals(Offset(0f, -99f), move(box, page, Offset(0f, -500f)).translation)
        assertEquals(Offset(0f, 199f), move(box, page, Offset(0f, 500f)).translation)
        assertEquals(Offset(20f, -30f), move(box, page, Offset(20f, -30f)).translation)
    }

    @Test fun moveNeverPushesInkAlreadyPastTheEdgeFurtherOut() {
        val box = Rect(-10f, 99f, 50f, 101f)
        assertEquals(Offset.Zero, move(box, page, Offset(-5f, 0f)).translation)
        assertEquals(Offset(5f, 0f), move(box, page, Offset(5f, 0f)).translation)
    }

    @Test fun resizeScalesAboutTheOppositeCornerAndScalesTheSize() {
        val stroke = segment(Offset(50f, 50f), Offset(100f, 100f)) // ink box 49..101 both ways
        val frame = SelectionFrame.around(listOf(stroke))
        assertBox(Rect(49f, 49f, 101f, 101f), Rect(frame.corners()[0], frame.corners()[2]))
        // Bottom-right corner dragged to twice its distance from the top-left one.
        val t = resize(frame, inkBounds(listOf(stroke)), page, corner = 2, to = Offset(153f, 153f))
        val resized = t.apply(stroke)
        assertEquals(4f, resized.size, 0.001f)
        assertPoint(Offset(51f, 51f), resized.points[0])
        assertPoint(Offset(151f, 151f), resized.points[1])
        assertEquals(0.7f, resized.points[1].pressure)
        assertEquals(5L, resized.points[1].elapsedMillis)
        assertBox(Rect(49f, 49f, 153f, 153f), inkBounds(listOf(resized)))
    }

    @Test fun resizeStopsAtThePageEdge() {
        val stroke = segment(Offset(50f, 50f), Offset(100f, 100f))
        val frame = SelectionFrame.around(listOf(stroke))
        val t = resize(frame, inkBounds(listOf(stroke)), page, corner = 2, to = Offset(400f, 400f))
        assertEquals(200f, inkBounds(listOf(t.apply(stroke))).right, 0.01f)
    }

    @Test fun resizeKeepsTheSmallestScale() {
        val stroke = segment(Offset(50f, 50f), Offset(100f, 100f))
        val frame = SelectionFrame.around(listOf(stroke))
        // Dragged onto (and past) the opposite corner: the frame's shorter side stays 2 pt.
        val t = resize(frame, inkBounds(listOf(stroke)), page, corner = 2, to = Offset(20f, 20f))
        assertEquals(2f, frame.transformed(t).halfSize.minDimension * 2, 0.001f)
    }

    @Test fun resizeDoesNotForceAThinSelectionToGrow() {
        // A 1 pt line is thinner than the smallest side: it may grow but never has to.
        val stroke = InkStroke(1, 0, 0, 1f, listOf(InkPoint(50f, 50f, 0.5f, 0), InkPoint(100f, 50f, 0.5f, 1)), StrokeTool.Pen)
        val frame = SelectionFrame.around(listOf(stroke))
        val t = resize(frame, inkBounds(listOf(stroke)), page, corner = 2, to = frame.corners()[2])
        assertEquals(1f, t.scale, 0.001f)
    }

    /** The point [radius] from [center] at [degrees] clockwise from straight up (screen y points down). */
    private fun at(center: Offset, degrees: Float, radius: Float = 75f): Offset {
        val r = degrees * PI.toFloat() / 180
        return Offset(center.x + radius * sin(r), center.y - radius * cos(r))
    }

    @Test fun rotateSnapsNearARightAngleAndIsFreeElsewhere() {
        val stroke = segment(Offset(50f, 75f), Offset(100f, 75f))
        val frame = SelectionFrame.around(listOf(stroke))
        val c = frame.center

        val snapped = rotate(frame, listOf(stroke), page, from = at(c, 0f), to = at(c, 3f))
        assertEquals(0f, frame.transformed(snapped).angleDegrees, 0.001f)
        assertPoint(Offset(100f, 75f), snapped.apply(stroke).points[1])

        val free = rotate(frame, listOf(stroke), page, from = at(c, 0f), to = at(c, 10f))
        assertEquals(10f, frame.transformed(free).angleDegrees, 0.01f)
        // Clockwise on screen: the right end goes down.
        assertPoint(Offset(75f + 25f * cos(10f * PI.toFloat() / 180), 75f + 25f * sin(10f * PI.toFloat() / 180)), free.apply(stroke).points[1])
    }

    @Test fun rotateAddsUpTheAngleAcrossGestures() {
        val stroke = segment(Offset(50f, 75f), Offset(100f, 75f))
        val first = SelectionFrame.around(listOf(stroke))
        val t1 = rotate(first, listOf(stroke), page, from = at(first.center, 0f), to = at(first.center, 50f))
        val second = first.transformed(t1)
        assertEquals(50f, second.angleDegrees, 0.01f)
        // 37° more makes 87°, within 5° of 90°: it snaps.
        val t2 = rotate(second, listOf(t1.apply(stroke)), page, from = at(second.center, 0f), to = at(second.center, 37f))
        assertEquals(90f, second.transformed(t2).angleDegrees, 0.001f)
        assertEquals(40f, t2.rotationDegrees, 0.001f)
    }

    @Test fun rotateShiftsTheInkInsideThePage() {
        // 100 pt wide at the top edge: turned upright, it would stick 46 pt out above the Page.
        val stroke = segment(Offset(50f, 5f), Offset(150f, 5f))
        val frame = SelectionFrame.around(listOf(stroke))
        val t = rotate(frame, listOf(stroke), page, from = at(frame.center, 0f), to = at(frame.center, 90f))
        assertBox(Rect(99f, 0f, 101f, 102f), inkBounds(listOf(t.apply(stroke))))
    }

    @Test fun rotateThatSnapsBackToNoneDoesNotShiftInkPastAnEdge() {
        // Sticks 10 pt out past the left edge; a 3° drag snaps back to 0°.
        val stroke = segment(Offset(-10f, 100f), Offset(50f, 100f))
        val frame = SelectionFrame.around(listOf(stroke))
        val t = rotate(frame, listOf(stroke), page, from = at(frame.center, 0f), to = at(frame.center, 3f))
        assertEquals(true, t.isIdentity)
    }

    @Test fun snapAngleWorksAtEveryRightAngle() {
        assertEquals(180f, snapAngle(178f))
        assertEquals(270f, snapAngle(268f))
        assertEquals(-90f, snapAngle(-88f))
        assertEquals(170f, snapAngle(170f))
    }

    @Test fun resizeDoesNotShrinkInkOffThePageTowardsAnAnchorPastTheEdge() {
        // A frame turned 45° against the left edge: its left corner (the anchor, bottom-left) is at x = -8.28.
        val frame = SelectionFrame(Offset(20f, 150f), Size(20f, 20f), angleDegrees = 45f)
        val anchor = frame.corners()[3]
        assertEquals(-8.28f, anchor.x, 0.01f)
        val stroke = segment(Offset(5f, 150f), Offset(35f, 150f)) // ink box from x = 4
        // The far corner (top-right) dragged right onto the anchor.
        val t = resize(frame, inkBounds(listOf(stroke)), page, corner = 1, to = anchor)
        assertEquals(0f, inkBounds(listOf(t.apply(stroke))).left, 0.01f)
    }

    @Test fun keepInsideMovesABoxInsideAndCentresOneBiggerThanThePage() {
        assertEquals(Offset.Zero, keepInside(Rect(10f, 10f, 50f, 50f), page))
        assertEquals(Offset(10f, -20f), keepInside(Rect(-10f, 280f, 50f, 320f), page))
        assertEquals(Offset(-150f, 0f), keepInside(Rect(0f, 10f, 500f, 50f), page))
    }

    // Frame 100 × 50 pt around (100, 100), shown at twice its size, offset by (10, 20).
    private val placement = PagePlacement(0, Size(200f, 300f), scale = 2f, offset = Offset(10f, 20f))
    private val wide = SelectionFrame(Offset(100f, 100f), Size(50f, 25f), angleDegrees = 0f)

    private fun hit(frame: SelectionFrame, x: Float, y: Float, handleDistance: Float = 32f, minSide: Float = 0f) =
        hitTest(frame, placement, Offset(x, y), handleRadiusPx = 12f, rotateHandleDistancePx = handleDistance, insideMarginPx = 8f, minSidePx = minSide)

    @Test fun hitTestPadsAThinFrameSoItsCornersStayApart() {
        // A straight line's frame, 1 pt high (2 px on screen), shown at least 48 px high: 12 pt each way.
        val thin = SelectionFrame(Offset(100f, 100f), Size(50f, 0.5f), angleDegrees = 0f)
        assertEquals(FrameHit.Corner(0), hit(thin, 110f, 196f, minSide = 48f))
        assertEquals(FrameHit.Corner(3), hit(thin, 110f, 244f, minSide = 48f))
        assertEquals(FrameHit.RotateHandle, hit(thin, 210f, 164f, minSide = 48f))
        assertEquals(FrameHit.Inside, hit(thin, 210f, 235f, minSide = 48f))
        // Unpadded, the bottom-left touch is outside.
        assertEquals(FrameHit.Outside, hit(thin, 110f, 244f))
        // A side already longer than the minimum stays as it is.
        assertEquals(thin.halfSize.width, thin.shownAt(placement, 48f).halfSize.width)
    }

    @Test fun hitTestFindsTheHandleTheCornersAndTheInside() {
        // Top edge's middle on screen: (210, 170); the handle 32 px above it.
        assertEquals(FrameHit.RotateHandle, hit(wide, 210f, 140f))
        assertEquals(FrameHit.Corner(0), hit(wide, 112f, 168f))
        assertEquals(FrameHit.Corner(2), hit(wide, 310f, 270f))
        assertEquals(FrameHit.Inside, hit(wide, 210f, 220f))
        // Within the 8 px margin past the right edge (x = 310), and beyond it.
        assertEquals(FrameHit.Inside, hit(wide, 316f, 220f))
        assertEquals(FrameHit.Outside, hit(wide, 330f, 220f))
    }

    @Test fun hitTestPrefersTheHandleThenACornerOverTheInside() {
        // With the handle on the top edge itself, a touch there is the handle, not the inside.
        assertEquals(FrameHit.RotateHandle, hit(wide, 210f, 172f, handleDistance = 0f))
        // A corner wins over the inside it overlaps.
        assertEquals(FrameHit.Corner(3), hit(wide, 116f, 266f))
        // A frame so small its handle and corners overlap: the handle wins.
        val tiny = SelectionFrame(Offset(100f, 100f), Size(2f, 2f), angleDegrees = 0f)
        assertEquals(FrameHit.RotateHandle, hit(tiny, 210f, 216f, handleDistance = 0f))
    }

    @Test fun hitTestFollowsTheFrameAngle() {
        val turned = wide.copy(angleDegrees = 90f)
        // Turned clockwise a quarter: the top edge faces right, its middle at Page (125, 100), screen (260, 220).
        assertEquals(FrameHit.RotateHandle, hit(turned, 292f, 220f))
        assertEquals(FrameHit.Inside, hit(turned, 210f, 300f))
        assertEquals(FrameHit.Outside, hit(turned, 300f, 220f + 60f))
    }
}
