package com.violinsheet.feature.annotation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinsheet.core.data.ink.InkRepository
import com.violinsheet.core.data.settings.SettingsStore
import com.violinsheet.core.ink.InkStroke
import com.violinsheet.core.ink.Pen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One open Sheet's Annotations, its undo history and the toolbar's current choices. Strokes are held in memory while
 * the Sheet is open and each change is saved at once (autosave) by [InkRepository], whose queue outlives the screen.
 * The history goes with this ViewModel when the Sheet closes (SPECS: Layers, undo and saving).
 */
class AnnotationViewModel(
    private val sheetId: Long,
    private val ink: InkRepository,
    settings: SettingsStore,
) : ViewModel() {
    private val _strokes = MutableStateFlow(emptyList<InkStroke>())
    val strokes: StateFlow<List<InkStroke>> = _strokes.asStateFlow()

    private val history = UndoStack()
    val canUndo: StateFlow<Boolean> = history.canUndo
    val canRedo: StateFlow<Boolean> = history.canRedo

    private val _pen = MutableStateFlow(Pen(InkSettings.penColour.default, InkSettings.penSize.default))

    /** Starts from the Ink Settings; a toolbar change lasts only while this Sheet is open (SPECS: Settings). */
    val pen: StateFlow<Pen> = _pen.asStateFlow()
    private var penChosen = false

    private val _saveFailed = MutableStateFlow(false)

    /** A change couldn't be saved: the screen shows it, but reopening the Sheet wouldn't. */
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    /** Ids of strokes drawn while this Sheet is open: for those, memory wins over what the database load returns. */
    private val drawnHere = HashSet<Long>()

    init {
        viewModelScope.launch {
            // Strokes drawn before the saved ones load are kept, after them. One drawn here is left out of the load
            // even if already saved: it may have been undone since the load read it.
            val saved = ink.strokes(sheetId)
            _strokes.update { drawn -> saved.filter { it.id !in drawnHere } + drawn }
        }
        viewModelScope.launch {
            val colour = settings.observe(InkSettings.penColour).first()
            val size = settings.observe(InkSettings.penSize).first()
            if (!penChosen) _pen.value = Pen(colour, size)
        }
    }

    fun setPen(pen: Pen) {
        penChosen = true
        _pen.value = pen
    }

    fun addStroke(stroke: InkStroke) {
        drawnHere += stroke.id
        history.perform(AddStrokes(listOf(stroke)))
    }

    fun undo() = history.undo()

    fun redo() = history.redo()

    /** Flags [saveFailed] if the queued write fails. The write itself runs on even after this ViewModel is gone. */
    private fun watch(write: Deferred<Unit>) {
        write.invokeOnCompletion { e -> if (e != null && e !is CancellationException) _saveFailed.value = true }
    }

    /** Adds strokes to the Ink layer. Undo takes them out; redo puts them back where they were, keeping overlap order. */
    private inner class AddStrokes(private val added: List<InkStroke>) : Command {
        private val ids = added.mapTo(HashSet()) { it.id }
        private var removed: List<IndexedValue<InkStroke>>? = null
        private var sizeWithout = 0

        override fun apply() {
            val at = removed
            _strokes.update { list -> if (at == null) list + added else list.putBack(at, sizeWithout) }
            for (stroke in added) watch(ink.add(sheetId, stroke))
        }

        override fun revert() {
            val list = _strokes.value
            val found = list.withIndex().filter { it.value.id in ids }
            removed = found
            sizeWithout = list.size - found.size
            _strokes.value = list.filter { it.id !in ids }
            for (stroke in added) watch(ink.remove(stroke.id))
        }
    }
}

/**
 * Puts [removed] items (each with its place in the list it was taken from, in list order) back at those places.
 * [sizeWithout] is this list's size right after they were taken out. Assumes the only change to the list since then is
 * saved strokes loading in front, so every place shifts by how much the list grew.
 */
internal fun <T> List<T>.putBack(removed: List<IndexedValue<T>>, sizeWithout: Int): List<T> {
    val shift = size - sizeWithout
    return toMutableList().apply {
        for ((place, item) in removed) add((place + shift).coerceIn(0, size), item)
    }
}
