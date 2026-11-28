package com.violinsheet.core.data.symbol

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SymbolRepositoryTest {
    /** In-memory stand-in for the Room DAO, matching its queries (insert replaces, Sheet delete cascades). */
    private class FakeSymbolDao : SymbolDao {
        val rows = mutableListOf<SymbolRow>()
        override suspend fun forSheet(sheetId: Long) = rows.filter { it.sheetId == sheetId }.sortedBy { it.id }
        override suspend fun insert(rows: List<SymbolRow>) {
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

        /** What the foreign key's ON DELETE CASCADE does when the Sheet row goes. */
        fun deleteSheet(sheetId: Long) {
            rows.removeAll { it.sheetId == sheetId }
        }
    }

    private val dao = FakeSymbolDao()

    private fun symbol(id: Long, kind: String = "fingering.3") =
        Symbol(id, pageIndex = 1, kind = kind, x = 120.5f, y = 340.25f, colour = 0xFF000000.toInt())

    private fun TestScope.repository() = SymbolRepository(dao, backgroundScope)

    private fun SymbolRepository.add(sheetId: Long, symbol: Symbol) = replace(sheetId, emptyList(), listOf(symbol))

    @Test fun symbolRoundTripsWithItsLaterFieldsNull() = runTest {
        val repository = repository()
        repository.add(1, symbol(1)).await()
        val loaded = repository.symbols(1).single()
        assertEquals(Symbol(1, 1, "fingering.3", 120.5f, 340.25f, 1f, 0xFF000000.toInt(), null, null, null), loaded)
    }

    @Test fun symbolRoundTripsWithItsLaterFieldsSet() = runTest {
        val repository = repository()
        val slur = Symbol(7, 2, "slur", 10f, 20f, 1.5f, 0xFF112233.toInt(), endX = 90f, endY = 25f, bend = -12f)
        repository.add(1, slur)
        assertEquals(listOf(slur), repository.symbols(1))
    }

    @Test fun replaceDeletesAndInsertsInOneWrite() = runTest {
        val repository = repository()
        repository.add(1, symbol(1))
        repository.add(1, symbol(2))
        repository.replace(1, listOf(1), listOf(symbol(3))).await()
        assertEquals(listOf(2L, 3L), repository.symbols(1).map { it.id })
    }

    @Test fun savingTheSameSymbolAgainKeepsOneRow() = runTest {
        val repository = repository()
        repository.add(1, symbol(1))
        repository.add(1, symbol(1)).await()
        assertEquals(1, dao.rows.size)
    }

    @Test fun writesQueuedInQuickSuccessionLandInOrder() = runTest {
        val repository = repository()
        repository.add(1, symbol(1))
        repository.replace(1, listOf(1), emptyList())
        repository.add(1, symbol(1, "fingering.4"))
        assertEquals(listOf("fingering.4"), repository.symbols(1).map { it.kind })
    }

    @Test fun deletingTheSheetDeletesItsSymbols() = runTest {
        val repository = repository()
        repository.add(1, symbol(1))
        repository.add(2, symbol(2)).await()
        dao.deleteSheet(1)
        assertEquals(emptyList(), repository.symbols(1))
        assertEquals(listOf(2L), repository.symbols(2).map { it.id })
    }

    @Test fun unknownKindIsReturnedUnchanged() = runTest {
        val repository = repository()
        repository.add(1, symbol(1, "from.a.later.version"))
        assertEquals("from.a.later.version", repository.symbols(1).single().kind)
    }

    @Test fun idTakenByAnotherSheetFailsInsteadOfReplacingIt() = runTest {
        val repository = repository()
        repository.add(2, symbol(5)).await()
        val write = repository.replace(1, emptyList(), listOf(symbol(5, "fingering.1")))
        assertTrue(runCatching { write.await() }.isFailure)
        assertEquals(listOf("fingering.3"), repository.symbols(2).map { it.kind })
        assertEquals(emptyList(), repository.symbols(1))
    }
}
