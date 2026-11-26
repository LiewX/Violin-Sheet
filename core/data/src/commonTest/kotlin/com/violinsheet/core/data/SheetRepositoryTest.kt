package com.violinsheet.core.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SheetRepositoryTest {
    /** In-memory stand-in for the Room DAO, matching its queries. */
    private class FakeSheetDao : SheetDao {
        val rows = MutableStateFlow(emptyList<Sheet>())
        private var nextId = 1L

        override fun observeAll(): Flow<List<Sheet>> = MutableStateFlow(rows.value.filter { it.deletedAt == null })
        override suspend fun names(): List<String> = rows.value.map { it.name }.also { yield() }
        override suspend fun insert(sheet: Sheet): Long = nextId++.also { rows.value += sheet.copy(id = it) }
        override suspend fun rename(id: Long, name: String) = update(id) { it.copy(name = name) }
        override suspend fun get(id: Long) = rows.value.firstOrNull { it.id == id }
        override fun observe(id: Long): Flow<Sheet?> = MutableStateFlow(rows.value.firstOrNull { it.id == id })
        override suspend fun setAdvanceByOne(id: Long, advanceByOne: Boolean) = update(id) { it.copy(advanceByOne = advanceByOne) }
        override suspend fun setDeletedAt(id: Long, deletedAt: Long) = update(id) { it.copy(deletedAt = deletedAt) }
        override suspend fun undoDelete(id: Long): Int {
            if (get(id)?.deletedAt == null) return 0
            update(id) { it.copy(deletedAt = null) }
            return 1
        }
        override suspend fun deletedBefore(cutoff: Long) = rows.value.filter { (it.deletedAt ?: Long.MAX_VALUE) <= cutoff }
        override suspend fun delete(id: Long) {
            rows.value = rows.value.filter { it.id != id }
        }
        override suspend fun countUsing(pdfFile: String) = rows.value.count { it.pdfFile == pdfFile }
        override suspend fun withHash(fileHash: String) = rows.value.filter { it.fileHash == fileHash && it.deletedAt == null }
        override suspend fun setPdfFile(ids: List<Long>, pdfFile: String) {
            for (id in ids) update(id) { it.copy(pdfFile = pdfFile) }
        }

        private fun update(id: Long, change: (Sheet) -> Sheet) {
            rows.value = rows.value.map { if (it.id == id) change(it) else it }
        }

        fun sheet(id: Long) = rows.value.single { it.id == id }
    }

    private class FakePdfStore(vararg files: String) : SheetPdfStore {
        val files = files.toMutableSet()
        override fun exists(pdfFile: String) = pdfFile in files
        override fun delete(pdfFile: String) {
            files -= pdfFile
        }
    }

    private val dao = FakeSheetDao()
    private val pdfs = FakePdfStore()
    private var clock = 1_000_000L
    private val repository = SheetRepository(dao, pdfs, now = { clock })

    /** Imports a new copy [pdfFile] of a file with contents [hash]; the copy exists, as the importer wrote it. */
    private suspend fun import(fileName: String, hash: String, pdfFile: String): ImportResult {
        pdfs.files += pdfFile
        return repository.import(fileName, hash, pdfFile)
    }

    private suspend fun added(fileName: String, hash: String, pdfFile: String) =
        assertIs<ImportResult.Added>(import(fileName, hash, pdfFile)).sheet

    @Test fun importingSameFileTwiceKeepsHashAndNumbersName() = runTest {
        val first = added("Etude.pdf", "abc", "1.pdf")
        val second = added("Etude.pdf", "abc", "2.pdf")
        assertEquals("Etude" to "abc", first.name to first.fileHash)
        assertEquals("Etude (2)" to "abc", second.name to second.fileHash)
    }

    @Test fun concurrentImportsGetDistinctNames() = runTest {
        // names() yields mid-import, so without the lock both imports would read the same taken names.
        val names = List(3) { async { added("Etude.pdf", "abc", "$it.pdf").name } }.awaitAll()
        assertEquals(setOf("Etude", "Etude (2)", "Etude (3)"), names.toSet())
    }

    @Test fun importReattachesEverySheetMissingThatFile() = runTest {
        val a = added("Etude.pdf", "abc", "1.pdf")
        val b = added("Etude.pdf", "abc", "2.pdf")
        val other = added("Scales.pdf", "xyz", "3.pdf")
        pdfs.files.clear() // a restore brings back the records but not the PDFs

        val result = assertIs<ImportResult.Reattached>(import("renamed.pdf", "abc", "4.pdf"))

        assertEquals(setOf(a.id, b.id), result.sheets.map { it.id }.toSet())
        assertEquals("4.pdf" to "4.pdf", dao.sheet(a.id).pdfFile to dao.sheet(b.id).pdfFile)
        assertEquals("3.pdf", dao.sheet(other.id).pdfFile)
        assertEquals(3, dao.rows.value.size) // no new Sheet
    }

    @Test fun importOfFileWhosePdfIsPresentAddsSheet() = runTest {
        added("Etude.pdf", "abc", "1.pdf")
        assertEquals("Etude (2)", added("Etude.pdf", "abc", "2.pdf").name)
    }

    @Test fun importIgnoresDeletedSheetsMissingThatFile() = runTest {
        val sheet = added("Etude.pdf", "abc", "1.pdf")
        repository.delete(sheet)
        pdfs.files.clear()
        assertIs<ImportResult.Added>(import("Etude.pdf", "abc", "2.pdf"))
    }

    @Test fun reattachFromPromptFixesOnlyThatSheet() = runTest {
        val a = added("Etude.pdf", "abc", "1.pdf")
        val b = added("Etude.pdf", "abc", "2.pdf")
        pdfs.files.clear()

        repository.reattach(a.id, "abc", "3.pdf")

        assertEquals("3.pdf" to "2.pdf", dao.sheet(a.id).pdfFile to dao.sheet(b.id).pdfFile)
    }

    @Test fun reattachRejectsSheetThatHasItsPdfOrWasDeleted() = runTest {
        val sheet = added("Etude.pdf", "abc", "1.pdf")
        assertFailsWith<SheetException> { repository.reattach(sheet.id, "abc", "2.pdf") }
        repository.delete(sheet)
        pdfs.files.clear()
        assertFailsWith<SheetException> { repository.reattach(sheet.id, "abc", "3.pdf") }
        assertEquals("1.pdf", dao.sheet(sheet.id).pdfFile)
    }

    @Test fun reattachRejectsDifferentFile() = runTest {
        val sheet = added("Etude.pdf", "abc", "1.pdf")
        pdfs.files.clear()

        assertFailsWith<SheetException> { repository.reattach(sheet.id, "xyz", "2.pdf") }
        assertEquals("1.pdf", dao.sheet(sheet.id).pdfFile)
    }

    @Test fun renameTrimsAndIgnoresBlank() = runTest {
        val sheet = added("Etude.pdf", "abc", "1.pdf")
        repository.rename(sheet, "  Bach  ")
        assertEquals("Bach", dao.sheet(sheet.id).name)
        repository.rename(sheet, "   ")
        assertEquals("Bach", dao.sheet(sheet.id).name)
    }

    @Test fun deletedSheetLeavesListAndUndoBringsItBack() = runTest {
        val sheet = added("Etude.pdf", "abc", "1.pdf")
        repository.delete(sheet)
        assertEquals(emptyList(), dao.observeAll().first())
        repository.undoDelete(sheet)
        assertEquals(listOf(sheet.id), dao.observeAll().first().map { it.id })
    }

    @Test fun undoTimeLeftCountsFromTheDelete() = runTest {
        val deleted = repository.delete(added("Etude.pdf", "abc", "1.pdf"))
        clock += 4_000
        assertEquals(UNDO_DELETE_MILLIS - 4_000, repository.undoTimeLeft(deleted))
    }

    @Test fun undoAfterPurgeFailsWithMessage() = runTest {
        val sheet = added("Etude.pdf", "abc", "1.pdf")
        repository.delete(sheet)
        clock += UNDO_DELETE_MILLIS
        repository.purgeDeleted()
        assertFailsWith<SheetException> { repository.undoDelete(sheet) }
    }

    @Test fun purgeWaitsForUndoWindowThenRemovesSheetAndPdf() = runTest {
        val sheet = added("Etude.pdf", "abc", "1.pdf")
        repository.delete(sheet)

        clock += UNDO_DELETE_MILLIS - 1
        repository.purgeDeleted()
        assertEquals(1, dao.rows.value.size)

        clock += 1
        repository.purgeDeleted()
        assertEquals(emptyList(), dao.rows.value)
        assertTrue("1.pdf" !in pdfs.files)
    }

    @Test fun purgeKeepsPdfAnotherSheetStillUses() = runTest {
        val a = added("Etude.pdf", "abc", "1.pdf")
        val b = added("Etude.pdf", "abc", "2.pdf")
        pdfs.files.clear()
        import("Etude.pdf", "abc", "3.pdf") // both Sheets now share 3.pdf
        repository.delete(a)
        clock += UNDO_DELETE_MILLIS

        repository.purgeDeleted()

        assertEquals(listOf(b.id), dao.rows.value.map { it.id })
        assertTrue("3.pdf" in pdfs.files)
    }
}
