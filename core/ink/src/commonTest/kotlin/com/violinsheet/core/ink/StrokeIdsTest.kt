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
}
