package com.violinsheet.feature.annotation.edit.symbol

import androidx.compose.ui.geometry.Offset
import com.violinsheet.core.data.symbol.Symbol
import com.violinsheet.core.ink.eraserPath
import com.violinsheet.feature.annotation.Command
import com.violinsheet.feature.annotation.UndoStack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Eraser positions are never checked closer than this (Page points), whatever the reach. */
private const val MIN_ERASE_STEP = 0.5f

/**
 * The Eraser's gesture on [symbols] (SymbolEditing's list): the Symbols it takes out leave the list at once and, at
 * the end, are saved and recorded as one undo step ([removal] makes the step's Command for the Symbols taken).
 */
internal class SymbolErasing(
    private val symbols: MutableStateFlow<List<Symbol>>,
    private val history: UndoStack,
    /** Saves [add] in place of the Symbols with the ids [removeIds]. */
    private val save: (removeIds: List<Long>, add: List<Symbol>) -> Unit,
    private val removal: (taken: List<Symbol>) -> Command,
) {
    private var erase: Erase? = null

    /** An erase gesture is in progress: it is not in the history yet. */
    val busy: Boolean get() = erase != null

    fun begin() {
        erase = Erase()
    }

    fun eraseAt(pageIndex: Int, point: Offset, reach: Float) {
        val erase = erase ?: return
        val path = eraserPath(erase.last?.takeIf { erase.lastPage == pageIndex }, point, reach.coerceAtLeast(MIN_ERASE_STEP))
        erase.last = point
        erase.lastPage = pageIndex
        val hit = symbols.value.filter { s -> s.pageIndex == pageIndex && boxOf(s).inflate(reach).let { box -> path.any { box.holds(it) } } }
        if (hit.isEmpty()) return
        erase.taken += hit
        val gone = hit.mapTo(HashSet()) { it.id }
        symbols.update { list -> list.filter { it.id !in gone } }
    }

    fun offPage() {
        erase?.last = null
    }

    fun end(cancelled: Boolean) {
        val erase = erase ?: return
        this.erase = null
        if (erase.taken.isEmpty()) return
        val taken = erase.taken.sortedBy { it.id }
        if (cancelled) {
            // Nothing of it was saved yet: put them back on screen only.
            symbols.update { (it + taken).sortedBy { s -> s.id } }
            return
        }
        save(taken.map { it.id }, emptyList())
        history.record(removal(taken))
    }

    /** One erase gesture: the Symbols it took out so far, and where the eraser last was. */
    private class Erase {
        val taken = mutableListOf<Symbol>()
        var last: Offset? = null
        var lastPage = -1
    }
}
