package com.violinsheet.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SheetRepository(private val dao: SheetDao) {
    // Picking a free name and inserting must not interleave, or two imports could both get "(2)".
    private val addLock = Mutex()

    val sheets: Flow<List<Sheet>> = dao.observeAll()

    suspend fun add(fileName: String, fileHash: String, pdfFile: String): Sheet = addLock.withLock {
        val sheet = Sheet(name = sheetNameFor(fileName, dao.names()), fileHash = fileHash, pdfFile = pdfFile)
        sheet.copy(id = dao.insert(sheet))
    }
}
