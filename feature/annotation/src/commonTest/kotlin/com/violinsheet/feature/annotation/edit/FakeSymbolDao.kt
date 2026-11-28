package com.violinsheet.feature.annotation.edit

import com.violinsheet.core.data.symbol.SymbolDao
import com.violinsheet.core.data.symbol.SymbolRow
import kotlinx.coroutines.delay

/** In-memory stand-in for the Room DAO (insert replaces). */
internal class FakeSymbolDao : SymbolDao {
    val rows = mutableListOf<SymbolRow>()

    /** The load reads the rows after this long. */
    var readAfterMillis = 0L
    var failWrites = false
    var writes = 0

    override suspend fun forSheet(sheetId: Long): List<SymbolRow> {
        delay(readAfterMillis)
        return rows.filter { it.sheetId == sheetId }.sortedBy { it.id }
    }

    override suspend fun insert(rows: List<SymbolRow>) {
        writes++
        check(!failWrites) { "disk full" }
        val ids = rows.map { it.id }.toSet()
        this.rows.removeAll { it.id in ids }
        this.rows += rows
    }

    override suspend fun delete(ids: List<Long>) {
        writes++
        check(!failWrites) { "disk full" }
        rows.removeAll { it.id in ids }
    }

    override suspend fun idsOnOtherSheets(ids: List<Long>, sheetId: Long) =
        rows.filter { it.id in ids && it.sheetId != sheetId }.map { it.id }
}
