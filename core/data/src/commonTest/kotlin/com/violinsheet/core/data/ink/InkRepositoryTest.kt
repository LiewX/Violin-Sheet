package com.violinsheet.core.data.ink

import com.violinsheet.core.ink.InkPoint
import com.violinsheet.core.ink.InkStroke
import com.violinsheet.core.ink.StrokeTool
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InkRepositoryTest {
    /** In-memory stand-in for the Room DAO, matching its queries (insert replaces). */
    private class FakeInkDao : InkDao {
        val rows = mutableListOf<InkStrokeRow>()
        override suspend fun forSheet(sheetId: Long) = rows.filter { it.sheetId == sheetId }.sortedBy { it.id }
        override suspend fun insert(rows: List<InkStrokeRow>) {
            delay(10) // slower than a read, so a load not queued behind it would miss it
            val ids = rows.map { it.id }.toSet()
            this.rows.removeAll { it.id in ids } // REPLACE
            this.rows += rows
        }
        override suspend fun delete(ids: List<Long>) {
            rows.removeAll { it.id in ids }
        }

        override suspend fun idsOnOtherSheets(ids: List<Long>, sheetId: Long) =
            rows.filter { it.id in ids && it.sheetId != sheetId }.map { it.id }
    }

    private val dao = FakeInkDao()

    private fun stroke(id: Long, tool: StrokeTool = StrokeTool.Pen) =
        InkStroke(id, 0, 0xFF000000.toInt(), 1.5f, listOf(InkPoint(1f, 2f, 0.5f, 0)), tool)

    private fun TestScope.repository() = InkRepository(dao, backgroundScope)

    private fun InkRepository.add(sheetId: Long, stroke: InkStroke) = replace(sheetId, emptyList(), listOf(stroke))

    private fun InkRepository.remove(strokeId: Long) = replace(0, listOf(strokeId), emptyList())

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

    @Test fun replaceDeletesThenSavesSoAPieceCanKeepTheOriginalsId() = runTest {
        val repository = repository()
        repository.add(1, stroke(1))
        val piece = stroke(1).copy(points = listOf(InkPoint(5f, 5f, 0.5f, 0)))
        repository.replace(1, listOf(1), listOf(piece, stroke(2)))
        assertEquals(listOf(piece, stroke(2)), repository.strokes(1))
    }

    @Test fun pieceIdTakenByAnotherSheetFailsInsteadOfReplacingIt() = runTest {
        val repository = repository()
        repository.add(2, stroke(5)).await()
        val write = repository.replace(1, emptyList(), listOf(stroke(5)))
        assertTrue(runCatching { write.await() }.isFailure)
        assertEquals(listOf(5L), repository.strokes(2).map { it.id })
        assertEquals(emptyList(), repository.strokes(1))
    }

    @Test fun highlighterStaysAHighlighter() = runTest {
        val repository = repository()
        repository.add(1, stroke(1, StrokeTool.Highlighter))
        assertEquals(StrokeTool.Highlighter, repository.strokes(1).single().tool)
    }
}
