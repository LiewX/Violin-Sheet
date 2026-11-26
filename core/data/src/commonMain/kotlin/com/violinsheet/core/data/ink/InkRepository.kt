package com.violinsheet.core.data.ink

import com.violinsheet.core.ink.InkStroke
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
            runCatching { InkStroke(row.id, row.pageIndex, row.colour, row.size, decodeInkPoints(row.points)) }
                .onFailure(onUnreadable)
                .getOrNull()
        }
    }.await()

    /** Queues saving [stroke]; the result completes when it is saved, or with the error. Saving it again is harmless. */
    fun add(sheetId: Long, stroke: InkStroke): Deferred<Unit> = inOrder {
        dao.insert(InkStrokeRow(stroke.id, sheetId, stroke.pageIndex, stroke.colour, stroke.size, encodeInkPoints(stroke.points)))
    }

    /** Queues deleting a stroke; the result completes when it is gone, or with the error. */
    fun remove(strokeId: Long): Deferred<Unit> = inOrder { dao.delete(strokeId) }
}
