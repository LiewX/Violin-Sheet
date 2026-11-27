package com.violinsheet.core.ink

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PageSplitTest {
    // A two-Page spread at half size: each 600 × 800 Page is 300 × 400 on screen, 20 px apart.
    private val pageSize = Size(600f, 800f)
    private val left = PagePlacement(0, pageSize, 0.5f, Offset(100f, 50f))
    private val right = PagePlacement(1, pageSize, 0.5f, Offset(420f, 50f))
    private val spread = listOf(left, right)

    private var lastId = 100L
    private fun nextId() = ++lastId

    /** A pen stroke drawn through [screen] points, in [reference]'s Page coordinates, a point every 10 ms. */
    private fun drawn(screen: List<Offset>, reference: PagePlacement = left) = InkStroke(
        7, reference.pageIndex, 0xFF000000.toInt(), 2f,
        screen.mapIndexed { i, s -> reference.toPage(s).let { InkPoint(it.x, it.y, 0.5f, i * 10L) } },
        StrokeTool.Pen,
    )

    private fun split(stroke: InkStroke, reference: PagePlacement = left) = stroke.splitAtPages(reference, spread, ::nextId)

    @Test fun strokeInsideOnePageIsLeftAlone() {
        val stroke = drawn(listOf(Offset(150f, 100f), Offset(250f, 300f), Offset(380f, 420f)))
        assertSame(stroke, split(stroke).single())
    }

    @Test fun strokeAcrossTheGapGivesOnePartPerPage() {
        // Left Page's middle to the right Page's middle, along screen y = 250 (Page y = 400).
        val parts = split(drawn(listOf(Offset(250f, 250f), Offset(570f, 250f))))
        assertEquals(listOf(0, 1), parts.map { it.pageIndex })
        assertEquals(listOf(7L, 101L), parts.map { it.id })
        val (onLeft, onRight) = parts
        // Each part in its own Page's coordinates, cut at the Page's edge.
        assertEquals(listOf(300f, 600f), onLeft.points.map { it.x })
        assertEquals(listOf(0f, 300f), onRight.points.map { it.x })
        for (p in onLeft.points + onRight.points) assertEquals(400f, p.y)
        assertEquals(StrokeTool.Pen, onRight.tool)
        assertEquals(2f, onRight.size)
        assertEquals(0xFF000000.toInt(), onRight.colour)
    }

    @Test fun pressureAndTimeAreInterpolatedAtTheCut() {
        // 10 ms from x = 300 to x = 500 on screen: the left edge (400) is halfway, the right Page's (420) 60 % along.
        val stroke = drawn(listOf(Offset(300f, 250f), Offset(500f, 250f))).let {
            it.copy(points = listOf(it.points[0].copy(pressure = 0.2f), it.points[1].copy(pressure = 0.6f)))
        }
        val (onLeft, onRight) = split(stroke)
        assertEquals(0.4f, onLeft.points.last().pressure, 0.001f)
        assertEquals(5L, onLeft.points.last().elapsedMillis)
        assertEquals(0.44f, onRight.points.first().pressure, 0.001f)
        assertEquals(6L, onRight.points.first().elapsedMillis)
    }

    @Test fun strokeCrossingBackAndForthGivesThreeParts() {
        val parts = split(drawn(listOf(Offset(250f, 150f), Offset(570f, 150f), Offset(250f, 350f))))
        assertEquals(listOf(0, 1, 0), parts.map { it.pageIndex })
        assertEquals(listOf(7L, 101L, 102L), parts.map { it.id })
        // Back onto the left Page over its right edge.
        assertEquals(600f, parts[2].points.first().x)
    }

    @Test fun pointsOnTheStartPageAreKeptExactly() {
        val stroke = drawn(listOf(Offset(150.3f, 100.7f), Offset(250.1f, 250.9f), Offset(570f, 250f)))
        val onLeft = split(stroke).first()
        // The stroke's own points as drawn, not mapped to screen and back; only the cut point is new.
        assertEquals(stroke.points.take(2), onLeft.points.take(2))
        assertEquals(3, onLeft.points.size)
    }

    @Test fun startPageGoneFromTheScreenStillGetsItsPart() {
        // By lift the left Page has scrolled out of the placements; its part is still kept.
        val parts = drawn(listOf(Offset(250f, 250f), Offset(570f, 250f))).splitAtPages(left, listOf(right), ::nextId)
        assertEquals(listOf(0, 1), parts.map { it.pageIndex })
        assertEquals(600f, parts[0].points.last().x)
    }

    @Test fun highlighterKeepsItsToolOnEveryPartWithinItsPage() {
        val stroke = drawn(listOf(Offset(90f, 30f), Offset(250f, 250f), Offset(570f, 250f), Offset(730f, 470f)))
            .copy(tool = StrokeTool.Highlighter)
        val parts = split(stroke)
        assertEquals(listOf(0, 1), parts.map { it.pageIndex })
        for (part in parts) {
            assertEquals(StrokeTool.Highlighter, part.tool)
            for (p in part.points) {
                assertTrue(p.x in 0f..pageSize.width && p.y in 0f..pageSize.height, "$p outside the Page")
            }
        }
    }

    @Test fun leavingIntoTheSurroundAndComingBackGivesTwoPartsOnOnePage() {
        // Out over the top edge (screen y = 50) and back in.
        val parts = split(drawn(listOf(Offset(150f, 100f), Offset(200f, 0f), Offset(250f, 100f))))
        assertEquals(listOf(0, 0), parts.map { it.pageIndex })
        assertEquals(0f, parts[0].points.last().y)
        assertEquals(0f, parts[1].points.first().y)
    }

    @Test fun startingInTheGapDropsTheGapPart() {
        // Starts between the Pages (x = 410); its reference is the nearest Page.
        val parts = split(drawn(listOf(Offset(410f, 250f), Offset(500f, 250f))))
        assertEquals(1, parts.size)
        assertEquals(7L, parts[0].id)
        assertEquals(1, parts[0].pageIndex)
        assertEquals(listOf(0f, 160f), parts[0].points.map { it.x })
    }

    @Test fun strokeOffEveryPageLeavesNothing() {
        assertEquals(emptyList(), split(drawn(listOf(Offset(10f, 10f), Offset(90f, 600f)))))
        assertEquals(emptyList(), split(drawn(listOf(Offset(405f, 100f), Offset(415f, 300f)))))
    }

    @Test fun strokeAlongTheEdgeStaysOnItsPage() {
        val stroke = drawn(listOf(Offset(400f, 100f), Offset(400f, 300f)))
        assertSame(stroke, split(stroke).single())
    }

    @Test fun strokeTouchingTheEdgeFromOutsideLeavesTheOutsideAlone() {
        // From the gap to the left Page's edge and back: only a point touches the Page, too short to keep.
        assertEquals(emptyList(), split(drawn(listOf(Offset(410f, 100f), Offset(400f, 150f), Offset(410f, 200f)))))
    }

    @Test fun singlePointDotStaysOnItsPage() {
        val dot = drawn(listOf(Offset(150f, 100f)))
        assertSame(dot, split(dot).single())
        // A dot off every Page goes.
        assertEquals(emptyList(), split(drawn(listOf(Offset(410f, 100f)))))
    }

    @Test fun sizeIsConvertedWhenThePagesHaveDifferentScales() {
        val bigRight = PagePlacement(1, pageSize, 1f, Offset(420f, 50f))
        val parts = drawn(listOf(Offset(250f, 250f), Offset(570f, 250f))).splitAtPages(left, listOf(left, bigRight), ::nextId)
        // 2 Page points at half size are 1 px on screen: 1 Page point on a Page at full size.
        assertEquals(listOf(2f, 1f), parts.map { it.size })
        assertEquals(listOf(0f, 150f), parts[1].points.map { it.x })
    }
}
