package com.violinsheet.core.data.symbol

import com.violinsheet.core.data.OrderedWork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred

/**
 * A Sheet's Symbols. Every read and write runs one at a time, in the order asked, in [scope], which outlives any
 * screen (as in InkRepository). The stored kind is passed through unchanged, known or not.
 */
class SymbolRepository(private val dao: SymbolDao, scope: CoroutineScope) {
    private val work = OrderedWork(scope)

    suspend fun symbols(sheetId: Long): List<Symbol> = work.inOrder {
        dao.forSheet(sheetId).map { Symbol(it.id, it.pageIndex, it.kind, it.x, it.y, it.scale, it.colour, it.endX, it.endY, it.bend) }
    }.await()

    /**
     * Queues one write that deletes the Symbols [removeIds], then saves [add] (replacing any with the same id), all or
     * nothing. The result completes when it is done, or with the error. Saving the same Symbols again is harmless.
     */
    fun replace(sheetId: Long, removeIds: List<Long>, add: List<Symbol>): Deferred<Unit> {
        val rows = add.map { SymbolRow(it.id, sheetId, it.pageIndex, it.kind, it.x, it.y, it.scale, it.colour, it.endX, it.endY, it.bend) }
        return work.inOrder { dao.replace(removeIds, rows) }
    }
}
