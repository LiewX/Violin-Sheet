package com.violinsheet.core.data.ink

import com.violinsheet.core.ink.InkStroke
import com.violinsheet.core.ink.StrokeTool
import com.violinsheet.core.ink.decodeInkPoints
import com.violinsheet.core.ink.encodeInkPoints
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.launch

/**
 * A Sheet's Ink strokes, in the shared stroke format. Every read and write runs one at a time, in the order asked, in
 * [scope], which outlives any screen: a draw, undo, redo in quick succession can't land out of order, and a Sheet
 * reopened at once loads only after the writes its last opening queued.
 */
class InkRepository(private val dao: InkDao, scope: CoroutineScope) {
    // A channel with a single consumer keeps the order even across suspension points, which a single-thread
    // dispatcher would not. Never closed: it lives as long as the app.
    private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch { for (work in queue) work() }
    }

    private fun <T> inOrder(work: suspend () -> T): Deferred<T> {
        val result = CompletableDeferred<T>()
        queue.trySend { result.completeWith(runCatching { work() }) }
        return result
    }

    /** The Sheet's strokes. A row that can't be read is skipped (and reported), so it can't hide the rest. */
    suspend fun strokes(sheetId: Long, onUnreadable: (Throwable) -> Unit = {}): List<InkStroke> = inOrder {
        dao.forSheet(sheetId).mapNotNull { row ->
            runCatching {
                InkStroke(row.id, row.pageIndex, row.colour, row.size, decodeInkPoints(row.points), StrokeTool.fromStored(row.tool))
            }
                .onFailure(onUnreadable)
                .getOrNull()
        }
    }.await()

    /**
     * Queues one write that deletes the strokes [removeIds], then saves [add] (replacing any with the same id), all or
     * nothing. The result completes when it is done, or with the error. Saving the same strokes again is harmless.
     */
    fun replace(sheetId: Long, removeIds: List<Long>, add: List<InkStroke>): Deferred<Unit> {
        val rows = add.map {
            InkStrokeRow(it.id, sheetId, it.pageIndex, it.colour, it.size, encodeInkPoints(it.points), it.tool.stored)
        }
        return inOrder { dao.replace(removeIds, rows) }
    }
}
