package com.violinsheet.feature.annotation.edit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.violinsheet.core.ink.FrameHit
import com.violinsheet.core.ink.PagePlacement
import com.violinsheet.core.ink.SelectionFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditPenLayerTest {
    // Page shown at 2 px per point, offset by (10, 20).
    private val placement = PagePlacement(0, Size(600f, 800f), scale = 2f, offset = Offset(10f, 20f))

    @Test fun aPenWithinTheSlopTapped() {
        assertFalse(beyondSlop(Offset(100f, 100f), Offset(105f, 106f), slopPx = 8f))
        assertFalse(beyondSlop(Offset(100f, 100f), Offset(108f, 100f), slopPx = 8f))
        assertTrue(beyondSlop(Offset(100f, 100f), Offset(106f, 106f), slopPx = 8f))
    }

    @Test fun theSideButtonHeldAtPenDownErasesWithEveryTool() {
        for (tool in EditTool.entries) {
            assertEquals(EditTool.Eraser, editToolForGesture(tool, eraseHeld = true), "$tool, button held")
            assertEquals(tool, editToolForGesture(tool, eraseHeld = false), "$tool, no button")
        }
    }

    @Test fun aScreenReachIsPagePointsAtTheZoom() {
        assertEquals(4f, placement.pagePoints(8f))
        assertEquals(8f, placement.copy(scale = 0.5f).pagePoints(4f))
    }

    // A fingering-sized frame, 4 × 5 pt around (100, 100): 8 × 10 px, shown 24 px a side; screen centre (210, 220).
    private val small = SelectionFrame(Offset(100f, 100f), Size(2f, 2.5f), 0f)

    private fun hit(x: Float, y: Float, frame: SelectionFrame = small) =
        symbolFrameHit(frame, placement, Offset(x, y), handleRadiusPx = 24f, insideMarginPx = 8f, minSidePx = 24f)

    @Test fun aSmallFramesMiddleMovesRatherThanResizes() {
        // Every corner's hit circle covers the centre; the centre is nearer, so it is a move.
        assertEquals(FrameHit.Inside, hit(210f, 220f))
        assertEquals(FrameHit.Inside, hit(214f, 223f))
    }

    @Test fun aSmallFramesCornerStillResizes() {
        // Shown corners at (198, 208) … (222, 232).
        assertEquals(FrameHit.Corner(2), hit(222f, 232f))
        assertEquals(FrameHit.Corner(2), hit(226f, 236f))
        assertEquals(FrameHit.Corner(0), hit(197f, 206f))
    }

    @Test fun aSymbolFrameHasNoRotateHandle() {
        // Where a stroke selection's handle would be (32 px above the top edge's middle): outside.
        assertEquals(FrameHit.Outside, hit(210f, 176f))
        assertEquals(FrameHit.Outside, hit(400f, 400f))
    }
}
