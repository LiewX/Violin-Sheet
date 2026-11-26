package com.violinsheet.core.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class SheetRepositoryTest {
    private class FakeSheetDao : SheetDao {
        val rows = MutableStateFlow(emptyList<Sheet>())
        override fun observeAll(): Flow<List<Sheet>> = rows
        override suspend fun names(): List<String> = rows.value.map { it.name }.also { yield() }
        override suspend fun insert(sheet: Sheet): Long {
            val id = rows.value.size + 1L
            rows.value += sheet.copy(id = id)
            return id
        }
    }

    @Test fun importingSameFileTwiceKeepsHashAndNumbersName() = runTest {
        val repository = SheetRepository(FakeSheetDao())
        val first = repository.add("Etude.pdf", "abc", "1.pdf")
        val second = repository.add("Etude.pdf", "abc", "2.pdf")
        assertEquals("Etude" to "abc", first.name to first.fileHash)
        assertEquals("Etude (2)" to "abc", second.name to second.fileHash)
    }

    @Test fun concurrentImportsGetDistinctNames() = runTest {
        val repository = SheetRepository(FakeSheetDao())
        // names() yields mid-add, so without the lock both imports would read the same taken names.
        val names = List(3) { async { repository.add("Etude.pdf", "abc", "$it.pdf").name } }.awaitAll()
        assertEquals(setOf("Etude", "Etude (2)", "Etude (3)"), names.toSet())
    }
}
