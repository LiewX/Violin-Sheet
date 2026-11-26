package com.violinsheet.core.ink

/**
 * Gives stroke ids that increase in drawing order. They must increase: strokes load ordered by id, and their order
 * decides which overlaps which. Time-based (1000 ids per millisecond), so ids keep increasing across app restarts;
 * within a process each is above the last even when the clock stalls or steps back. Use from one thread.
 */
class StrokeIds(private val nowMillis: () -> Long) {
    private var last = Long.MIN_VALUE

    fun next(): Long = maxOf(nowMillis() * 1000, last + 1).also { last = it }
}
