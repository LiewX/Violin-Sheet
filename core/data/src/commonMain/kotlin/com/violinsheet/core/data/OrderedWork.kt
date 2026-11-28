package com.violinsheet.core.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.launch

/**
 * Runs work one at a time, in the order asked, in [scope]. A channel with a single consumer keeps the order even across
 * suspension points, which a single-thread dispatcher would not. Never closed: it lives as long as [scope].
 */
internal class OrderedWork(scope: CoroutineScope) {
    private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch { for (work in queue) work() }
    }

    /** Queues [work]; the result completes with its value, or with its error. */
    fun <T> inOrder(work: suspend () -> T): Deferred<T> {
        val result = CompletableDeferred<T>()
        queue.trySend { result.completeWith(runCatching { work() }) }
        return result
    }
}
