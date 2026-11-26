package com.violinsheet.core.data.ink

import com.violinsheet.core.ink.InkPoint
import com.violinsheet.core.ink.InkStroke
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InkRepositoryTest {
    /** In-memory stand-in for the Room DAO, matching its queries (insert replaces). */
    private class FakeInkDao : InkDao {
        val rows = mutableListOf<InkStrokeRow>()
        override suspend fun forSheet(sheetId: Long) = rows.filter { it.sheetId == sheetId }.sortedBy { it.id }
        override suspend fun insert(row: InkStrokeRow) {
            delay(10) // slower than a read, so a load not queued behind it would miss it
            rows.removeAll { it.id == row.id } // REPLACE
            rows += row
        }
        override suspend fun delete(id: Long) {
            rows.removeAll { it.id == id }
        }
    }

    private val dao = FakeInkDao()

    private fun stroke(id: Long) = InkStroke(id, 0, 0xFF000000.toInt(), 1.5f, listOf(InkPoint(1f, 2f, 0.5f, 0)))

    private fun TestScope.repository() = InkRepository(dao, backgroundScope)

    @Test fun removeDeletesOnlyThatStroke() = runTest {
        val repository = repository()
        repository.add(1, stroke(1))
        repository.add(1, stroke(2))
        repository.add(2, stroke(3))

        repository.remove(2).await()

        assertEquals(listOf(1L), repository.strokes(1).map { it.id })
        assertEquals(listOf(3L), repository.strokes(2).map { it.id })
    }

    @Test fun removedStrokeCanBeAddedAgainWithSameId() = runTest {
        val repository = repository()
        repository.add(1, stroke(1))
        repository.remove(1)
        repository.add(1, stroke(1))
        assertEquals(listOf(stroke(1)), repository.strokes(1))
    }

    @Test fun addingSameStrokeTwiceKeepsOneRow() = runTest {
        val repository = repository()
        repository.add(1, stroke(1))
        repository.add(1, stroke(1)).await()
        assertEquals(1, dao.rows.size)
    }

    @Test fun loadRunsAfterWritesQueuedBeforeIt() = runTest {
        val repository = repository()
        repository.add(1, stroke(1))
        repository.add(1, stroke(2))
        repository.remove(1)
        assertEquals(listOf(2L), repository.strokes(1).map { it.id })
    }
}
