package com.violinsheet.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** How long a deleted Sheet can be brought back with Undo. */
const val UNDO_DELETE_MILLIS = 10_000L

/** What a home-screen import did. */
sealed interface ImportResult {
    data class Added(val sheet: Sheet) : ImportResult

    /** The file matched Sheets whose PDF was missing after a restore; they use it now and no Sheet was added. */
    data class Reattached(val sheets: List<Sheet>) : ImportResult
}

/** A failure whose message is written for the user. */
class SheetException(message: String) : Exception(message)

@OptIn(ExperimentalTime::class)
class SheetRepository(
    private val dao: SheetDao,
    private val pdfs: SheetPdfStore,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    // Changes that read and then write (free names, missing PDFs, purge) must not interleave.
    private val lock = Mutex()

    val sheets: Flow<List<Sheet>> = dao.observeAll()

    fun sheet(id: Long): Flow<Sheet?> = dao.observe(id)

    suspend fun setAdvanceByOne(sheetId: Long, advanceByOne: Boolean) = dao.setAdvanceByOne(sheetId, advanceByOne)

    fun isPdfMissing(sheet: Sheet): Boolean = !pdfs.exists(sheet.pdfFile)

    /**
     * Adds a Sheet for an imported copy, unless Sheets with the same file contents are missing their PDF
     * (after a restore): then those Sheets use [pdfFile] instead.
     */
    suspend fun import(fileName: String, fileHash: String, pdfFile: String): ImportResult = lock.withLock {
        val missing = dao.withHash(fileHash).filter { isPdfMissing(it) }
        if (missing.isNotEmpty()) {
            dao.setPdfFile(missing.map { it.id }, pdfFile)
            return ImportResult.Reattached(missing.map { it.copy(pdfFile = pdfFile) })
        }
        val sheet = Sheet(name = sheetNameFor(fileName, dao.names()), fileHash = fileHash, pdfFile = pdfFile)
        ImportResult.Added(sheet.copy(id = dao.insert(sheet)))
    }

    /**
     * Gives Sheet [sheetId] the re-imported copy [pdfFile], if its contents match the file the Sheet was made from.
     * Throws [SheetException] otherwise.
     */
    suspend fun reattach(sheetId: Long, fileHash: String, pdfFile: String): Sheet = lock.withLock {
        val sheet = dao.get(sheetId)?.takeIf { it.deletedAt == null } ?: throw SheetException("That Sheet was deleted")
        if (!isPdfMissing(sheet)) throw SheetException("\"${sheet.name}\" already has its PDF")
        if (fileHash != sheet.fileHash) throw SheetException("That PDF isn't the file \"${sheet.name}\" was made from")
        dao.setPdfFile(listOf(sheet.id), pdfFile)
        sheet.copy(pdfFile = pdfFile)
    }

    /** Renames [sheet]; a blank name is ignored. */
    suspend fun rename(sheet: Sheet, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) dao.rename(sheet.id, trimmed)
    }

    /** Hides [sheet] from the list; returns it with its delete time. Purged after [UNDO_DELETE_MILLIS] unless undone. */
    suspend fun delete(sheet: Sheet): Sheet {
        val deletedAt = now()
        dao.setDeletedAt(sheet.id, deletedAt)
        return sheet.copy(deletedAt = deletedAt)
    }

    /** Milliseconds left to undo deleting [sheet] (as returned by [delete]). */
    fun undoTimeLeft(sheet: Sheet): Long = sheet.deletedAt?.let { it + UNDO_DELETE_MILLIS - now() } ?: 0

    /** Brings [sheet] back. Throws [SheetException] if it was already purged. */
    suspend fun undoDelete(sheet: Sheet) = lock.withLock {
        if (dao.undoDelete(sheet.id) == 0) throw SheetException("Too late to undo: \"${sheet.name}\" is gone")
    }

    /** Removes Sheets whose Undo window has passed, with their PDF copy unless another Sheet uses it. */
    suspend fun purgeDeleted() = lock.withLock {
        for (sheet in dao.deletedBefore(now() - UNDO_DELETE_MILLIS)) {
            dao.delete(sheet.id)
            if (dao.countUsing(sheet.pdfFile) == 0) pdfs.delete(sheet.pdfFile)
        }
    }
}
