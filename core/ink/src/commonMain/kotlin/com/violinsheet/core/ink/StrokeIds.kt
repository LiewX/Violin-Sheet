package com.violinsheet.core.ink

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Gives stroke ids that increase in drawing order. They must increase: strokes load ordered by id, and their order
 * decides which overlaps which. Time-based ([PER_MILLISECOND] ids per millisecond), so ids keep increasing across app
 * restarts; within a process each is above the last even when the clock stalls or steps back. Use from one thread.
 */
class StrokeIds(private val nowMillis: () -> Long) {
    private var last = Long.MIN_VALUE

    fun next(): Long = maxOf(nowMillis() * PER_MILLISECOND, last + 1).also { last = it }

    companion object {
        const val PER_MILLISECOND = 1000L
    }
}

/**
 * The app's one source of new stroke ids, for drawn strokes and erased pieces alike: two time-based sources would give
 * the same id within a millisecond. Use from the main thread only.
 */
@OptIn(ExperimentalTime::class)
val strokeIds = StrokeIds { Clock.System.now().toEpochMilliseconds() }

/**
 * Ids for the [count] pieces a cut leaves of stroke [originalId], so they keep its place in the drawing order: the
 * first piece takes the original's id, each further one the smallest id above it that [used] (the ids of the Sheet's
 * strokes) doesn't hold. Ids just above a stroke's are usually only its own earlier pieces, since strokes drawn apart
 * are [StrokeIds.PER_MILLISECOND] apart, so skipping them keeps the order. The known exception: strokes pasted together
 * by the lasso get consecutive ids, so a piece may land above a pasted neighbour. If no id within
 * [StrokeIds.PER_MILLISECOND] of the original is free, the search may have passed a later stroke, so the piece gets
 * [fallback]'s id, on top, instead.
 *
 * Stroke ids are unique across all Sheets, but [used] holds only this Sheet's: saving (InkDao.replace) refuses an id
 * another Sheet holds rather than overwrite it.
 */
fun pieceIds(originalId: Long, count: Int, used: Set<Long>, fallback: () -> Long): List<Long> {
    val ids = ArrayList<Long>(count)
    if (count > 0) ids += originalId
    val highest = if (originalId > Long.MAX_VALUE - StrokeIds.PER_MILLISECOND) Long.MAX_VALUE else originalId + StrokeIds.PER_MILLISECOND - 1
    var candidate = originalId
    repeat(count - 1) {
        var id: Long? = null
        while (id == null && candidate < highest) {
            candidate++
            if (candidate !in used) id = candidate
        }
        ids += id ?: fallback()
    }
    return ids
}
