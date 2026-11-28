package com.violinsheet.feature.annotation

/** Items taken out of a list, each with its place in the list it was taken from (in list order). */
internal class Places<T>(val items: List<IndexedValue<T>>, val sizeWithout: Int)

/** This list without the items matching [taken], and where those were. */
internal fun <T> List<T>.takeOut(taken: (T) -> Boolean): Pair<List<T>, Places<T>> {
    val found = withIndex().filter { taken(it.value) }
    return filterNot(taken) to Places(found, size - found.size)
}

/**
 * Puts the [places] items back where they were. Assumes the only change to the list since they were taken out is
 * saved strokes loading in front, so every place shifts by how much the list grew.
 */
internal fun <T> List<T>.putBack(places: Places<T>): List<T> {
    val shift = size - places.sizeWithout
    return toMutableList().apply {
        for ((place, item) in places.items) add((place + shift).coerceIn(0, size), item)
    }
}
