package com.violinsheet.feature.annotation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinsheet.core.data.ink.InkRepository
import com.violinsheet.core.data.settings.SettingsStore
import com.violinsheet.core.ink.DrawTool
import com.violinsheet.core.ink.EraseInput
import com.violinsheet.core.ink.EraseMode
import com.violinsheet.core.ink.Eraser
import com.violinsheet.core.ink.InkStroke
import com.violinsheet.core.ink.StrokeTool
import com.violinsheet.core.ink.cutBy
import com.violinsheet.core.ink.eraserPath
import com.violinsheet.core.ink.mayReach
import com.violinsheet.core.ink.pieceIds
import com.violinsheet.core.ink.pointBounds
import com.violinsheet.core.ink.strokeIds
import com.violinsheet.core.ink.touchedBy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Eraser positions are checked at most this fraction of the eraser's radius apart, so it sweeps a solid band. */
private const val ERASER_STEP = 0.5f

/**
 * One open Sheet's Annotations, its undo history and the toolbar's current choices. Strokes are held in memory while
 * the Sheet is open and each change is saved at once (autosave) by [InkRepository], whose queue outlives the screen.
 * The history goes with this ViewModel when the Sheet closes (SPECS: Layers, undo and saving).
 */
class AnnotationViewModel(
    private val sheetId: Long,
    private val ink: InkRepository,
    settings: SettingsStore,
    private val eraserMemory: EraserMemory,
    /** Gives the id of a piece that can't keep its stroke's place (pieceIds). */
    private val newStrokeId: () -> Long = strokeIds::next,
) : ViewModel(), EraseInput {
    private val _strokes = MutableStateFlow(emptyList<InkStroke>())
    val strokes: StateFlow<List<InkStroke>> = _strokes.asStateFlow()

    private val history = UndoStack()
    val canUndo: StateFlow<Boolean> = history.canUndo
    val canRedo: StateFlow<Boolean> = history.canRedo

    private val _tools = MutableStateFlow(
        InkTools(
            ToolChoice.Pen,
            DrawTool(StrokeTool.Pen, InkSettings.penColour.default, InkSettings.penSize.default),
            DrawTool(StrokeTool.Highlighter, InkSettings.highlighterColour.default, InkSettings.highlighterSize.default),
            eraserMemory.eraser,
        ),
    )

    /**
     * Pen and highlighter start from the Ink Settings, and a toolbar change lasts only while this Sheet is open (SPECS:
     * Settings). The eraser has no Setting: its mode and size carry over to the next Sheet while the app runs.
     */
    val tools: StateFlow<InkTools> = _tools.asStateFlow()
    private var penChosen = false
    private var highlighterChosen = false

    private val _saveFailed = MutableStateFlow(false)

    /** A change couldn't be saved: the screen shows it, but reopening the Sheet wouldn't. */
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    /** Ids of strokes drawn while this Sheet is open: for those, memory wins over what the database load returns. */
    private val drawnHere = HashSet<Long>()

    private var erasing: EraseGesture? = null

    /** Each stroke's point box, for a cheap reject before the exact erase test. Kept only for the very stroke it is of. */
    private val bounds = HashMap<Long, Pair<InkStroke, Rect>>()

    private fun InkStroke.mayBeReachedBy(center: Offset, radius: Float): Boolean {
        val box = bounds[id]?.takeIf { it.first === this }?.second ?: pointBounds().also { bounds[id] = this to it }
        return mayReach(box, size, center, radius)
    }

    init {
        viewModelScope.launch {
            // Strokes drawn before the saved ones load are kept, after them. One drawn here is left out of the load
            // even if already saved: it may have been undone since the load read it.
            val saved = ink.strokes(sheetId).filter { it.id !in drawnHere }
            _strokes.update { drawn -> saved + drawn }
            erasing?.let { it.before = saved + it.before }
        }
        viewModelScope.launch {
            val pen = DrawTool(StrokeTool.Pen, settings.observe(InkSettings.penColour).first(), settings.observe(InkSettings.penSize).first())
            val highlighter = DrawTool(
                StrokeTool.Highlighter,
                settings.observe(InkSettings.highlighterColour).first(),
                settings.observe(InkSettings.highlighterSize).first(),
            )
            _tools.update {
                it.copy(pen = if (penChosen) it.pen else pen, highlighter = if (highlighterChosen) it.highlighter else highlighter)
            }
        }
    }

    fun setTools(tools: InkTools) {
        val before = _tools.value
        if (tools.pen != before.pen) penChosen = true
        if (tools.highlighter != before.highlighter) highlighterChosen = true
        eraserMemory.eraser = tools.eraser
        _tools.value = tools
    }

    fun addStroke(stroke: InkStroke) {
        drawnHere += stroke.id
        // A stroke finished (another pointer, or a lift lost) while an erase runs goes on top of the list the erase
        // started from too, so the erase's places stay right.
        erasing?.let { it.before = it.before + stroke }
        history.perform(SwapStrokes(out = emptyList(), into = listOf(stroke)))
    }

    // Undo and redo wait for the pen to lift: an erase gesture in progress is not in the history yet.
    fun undo() {
        if (erasing == null) history.undo()
    }

    fun redo() {
        if (erasing == null) history.redo()
    }

    override fun beginErase(eraser: Eraser) {
        erasing = EraseGesture(eraser, _strokes.value)
    }

    override fun eraseAt(pageIndex: Int, at: Offset) {
        val gesture = erasing ?: return
        val step = (gesture.eraser.size / 2 * ERASER_STEP).coerceAtLeast(0.5f)
        val path = eraserPath(gesture.last?.takeIf { gesture.lastPage == pageIndex }, at, step)
        gesture.last = at
        gesture.lastPage = pageIndex
        var list = _strokes.value
        for (center in path) list = gesture.erase(list, pageIndex, center) ?: continue
        if (list !== _strokes.value) _strokes.value = list
    }

    override fun eraserOffPage() {
        erasing?.last = null
    }

    override fun endErase(cancelled: Boolean) {
        val gesture = erasing ?: return
        erasing = null
        bounds.keys.retainAll(_strokes.value.mapTo(HashSet()) { it.id })
        if (gesture.originals.isEmpty()) return
        val out = gesture.originals.values.toList()
        val outIds = gesture.originals.keys
        val into = _strokes.value.filter { gesture.made[it.id] === it }
        val places = Places(gesture.before.withIndex().filter { it.value.id in outIds }, gesture.before.size - outIds.size)
        val command = SwapStrokes(out, into, places)
        if (cancelled) {
            command.revertOnScreen()
        } else {
            command.save(outIds, into)
            history.record(command)
        }
    }

    /** Flags [saveFailed] if the queued write fails. The write itself runs on even after this ViewModel is gone. */
    private fun watch(write: Deferred<Unit>) {
        write.invokeOnCompletion { e -> if (e != null && e !is CancellationException) _saveFailed.value = true }
    }

    /** One erase gesture, pen down to up, applied to the strokes as the eraser moves. */
    private inner class EraseGesture(val eraser: Eraser, var before: List<InkStroke>) {
        /** Strokes from before the gesture that it took out (whole, or cut into pieces), by id. */
        val originals = LinkedHashMap<Long, InkStroke>()

        /** Pieces the gesture made that are still there, by id. */
        val made = HashMap<Long, InkStroke>()
        var last: Offset? = null
        var lastPage = -1

        /** [list] with what the eraser at [center] on Page [page] takes away; null if it takes nothing. */
        fun erase(list: List<InkStroke>, page: Int, center: Offset): List<InkStroke>? {
            val radius = eraser.size / 2
            var used: MutableSet<Long>? = null
            var result: MutableList<InkStroke>? = null
            for ((i, stroke) in list.withIndex()) {
                val left = if (stroke.pageIndex != page || !stroke.mayBeReachedBy(center, radius)) {
                    null
                } else {
                    when (eraser.mode) {
                        EraseMode.Stroke -> if (stroke.touchedBy(center, radius)) emptyList() else null
                        EraseMode.Area -> stroke.cutBy(center, radius)?.let { pieces ->
                            val ids = used ?: list.mapTo(HashSet()) { it.id }.also { used = it }
                            pieces.zip(pieceIds(stroke.id, pieces.size, ids, newStrokeId)) { points, id ->
                                stroke.copy(id = id, points = points)
                            }
                        }
                    }
                }
                if (left == null) {
                    result?.add(stroke)
                    continue
                }
                // Pieces go where the stroke was, so they keep its place in the drawing order.
                val taken = result ?: list.subList(0, i).toMutableList().also { result = it }
                if (made.remove(stroke.id) == null) originals[stroke.id] = stroke
                for (piece in left) {
                    made[piece.id] = piece
                    drawnHere += piece.id
                    used?.add(piece.id)
                    taken += piece
                }
            }
            return result
        }
    }

    /**
     * Swaps the strokes [out] for [into] on the Ink layer: drawing a stroke swaps nothing for it, an erase swaps the
     * strokes it touched for the pieces it left. Undo swaps back, each stroke at its place, so overlap order is kept.
     */
    private inner class SwapStrokes(
        private val out: List<InkStroke>,
        private val into: List<InkStroke>,
        /** Where [out] were before they were taken out; given for an erase, which was applied as it happened. */
        private var outPlaces: Places<InkStroke>? = null,
    ) : Command {
        private val outIds = out.mapTo(HashSet()) { it.id }
        private val intoIds = into.mapTo(HashSet()) { it.id }
        private var intoPlaces: Places<InkStroke>? = null

        override fun apply() {
            val (without, places) = _strokes.value.takeOut { it.id in outIds }
            outPlaces = places
            _strokes.value = intoPlaces?.let { without.putBack(it) } ?: (without + into)
            save(outIds, into)
        }

        override fun revert() {
            revertOnScreen()
            save(intoIds, out)
        }

        /** Swaps back in memory only: for an erase cancelled before anything of it was saved. */
        fun revertOnScreen() {
            val (without, places) = _strokes.value.takeOut { it.id in intoIds }
            intoPlaces = places
            _strokes.value = without.putBack(checkNotNull(outPlaces) { "reverted before applied" })
        }

        fun save(removeIds: Set<Long>, add: List<InkStroke>) = watch(ink.replace(sheetId, removeIds.toList(), add))
    }
}

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
