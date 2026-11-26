package com.violinsheet.core.ink

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrokeIdsTest {
    @Test fun rapidCallsGiveUniqueIncreasingIds() {
        val ids = StrokeIds { 1_700_000_000_000 } // clock doesn't move between calls
        val drawn = List(5_000) { ids.next() }
        assertEquals(drawn.size, drawn.toSet().size)
        assertEquals(drawn.sorted(), drawn)
    }

    @Test fun idsKeepIncreasingWhenClockStepsBack() {
        var now = 2_000L
        val ids = StrokeIds { now }
        val first = ids.next()
        now = 1_000
        assertTrue(ids.next() > first)
    }

    @Test fun laterTimeGivesLargerId() {
        var now = 1_000L
        val ids = StrokeIds { now }
        val first = ids.next()
        now = 1_001
        assertTrue(ids.next() > first)
    }

    @Test fun piecesKeepTheOriginalsPlaceInTheOrder() {
        // Strokes 1000 and 2000; 1000 is cut in three.
        assertEquals(listOf(1000L, 1001L, 1002L), pieceIds(1000, 3, used = setOf(1000, 2000)) { error("no fallback") })
    }

    @Test fun cuttingAPieceAgainSkipsItsSiblings() {
        // 1000 was cut into 1000 and 1001; cutting 1000 again puts its new piece just above the siblings.
        assertEquals(listOf(1000L, 1002L), pieceIds(1000, 2, used = setOf(1000, 1001, 2000)) { error("no fallback") })
        // Cutting 1001 likewise.
        assertEquals(listOf(1001L, 1002L), pieceIds(1001, 2, used = setOf(1000, 1001, 2000)) { error("no fallback") })
    }

    @Test fun pieceGoesOnTopWhenNoIdIsFreeNearTheOriginal() {
        val used = (1000L until 2000L).toSet()
        assertEquals(listOf(1000L, 5000L), pieceIds(1000, 2, used) { 5000 })
        // Nor past the largest id.
        assertEquals(listOf(Long.MAX_VALUE, 7L), pieceIds(Long.MAX_VALUE, 2, setOf(Long.MAX_VALUE)) { 7 })
    }

    @Test fun oneOrNoPieceNeedsNoNewId() {
        assertEquals(listOf(1000L), pieceIds(1000, 1, setOf(1000)) { error("no fallback") })
        assertEquals(emptyList(), pieceIds(1000, 0, setOf(1000)) { error("no fallback") })
    }
}
