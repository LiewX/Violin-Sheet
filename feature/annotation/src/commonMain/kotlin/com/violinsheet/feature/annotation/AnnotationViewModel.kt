package com.violinsheet.feature.annotation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinsheet.core.data.ink.InkRepository
import com.violinsheet.core.data.settings.SettingsStore
import com.violinsheet.core.ink.DragKind
import com.violinsheet.core.ink.DrawTool
import com.violinsheet.core.ink.EraseInput
import com.violinsheet.core.ink.EraseMode
import com.violinsheet.core.ink.Eraser
import com.violinsheet.core.ink.InkStroke
import com.violinsheet.core.ink.LassoInput
import com.violinsheet.core.ink.Selection
import com.violinsheet.core.ink.SelectionFrame
import com.violinsheet.core.ink.StrokeTool
import com.violinsheet.core.ink.cutBy
import com.violinsheet.core.ink.eraserPath
import com.violinsheet.core.ink.inkBounds
import com.violinsheet.core.ink.lassoSelects
import com.violinsheet.core.ink.mayReach
import com.violinsheet.core.ink.move
import com.violinsheet.core.ink.pieceIds
import com.violinsheet.core.ink.pointBounds
import com.violinsheet.core.ink.resize
import com.violinsheet.core.ink.rotate
import com.violinsheet.core.ink.strokeAt
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
    private val settings: SettingsStore,
    private val eraserMemory: EraserMemory,
    /** Gives the id of a piece that can't keep its stroke's place (pieceIds). */
    private val newStrokeId: () -> Long = strokeIds::next,
) : ViewModel(), EraseInput, LassoInput {
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
    private var partlyChosen = false

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
            val partly = settings.observe(InkSettings.includePartlyEnclosed).first()
            _tools.update {
                it.copy(
                    pen = if (penChosen) it.pen else pen,
                    highlighter = if (highlighterChosen) it.highlighter else highlighter,
                    includePartlyEnclosed = if (partlyChosen) it.includePartlyEnclosed else partly,
                )
            }
        }
    }

    fun setTools(tools: InkTools) {
        val before = _tools.value
        if (tools.pen != before.pen) penChosen = true
        if (tools.highlighter != before.highlighter) highlighterChosen = true
        if (tools.includePartlyEnclosed != before.includePartlyEnclosed) {
            // The lasso's rule is app-wide, unlike the toolbar's colours and sizes: saved as the Setting at once.
            partlyChosen = true
            settings.set(InkSettings.includePartlyEnclosed, tools.includePartlyEnclosed)
        }
        eraserMemory.eraser = tools.eraser
        if (tools.chosen != before.chosen) clearSelection()
        _tools.value = tools
    }

    private val _selection = MutableStateFlow<Selection?>(null)
    val selection: StateFlow<Selection?> = _selection.asStateFlow()

    private var dragging: Drag? = null

    /** The Pages on screen: a selection lasts only while its Page is shown. */
    private var visiblePages = emptySet<Int>()

    /** The Pages on screen are now [pages] (a page turn): a selection on a Page no longer shown ends. */
    fun onVisiblePages(pages: Set<Int>) {
        visiblePages = pages
        val selected = _selection.value ?: return
        if (selected.pageIndex !in pages) clearSelection()
    }

    override fun lassoDone(pageIndex: Int, pagePoints: List<Offset>) =
        select(pageIndex, lassoSelects(_strokes.value, pageIndex, pagePoints, _tools.value.includePartlyEnclosed))

    override fun tapAt(pageIndex: Int, pagePoint: Offset, slop: Float) =
        select(pageIndex, listOfNotNull(strokeAt(_strokes.value, pageIndex, pagePoint, slop)))

    /** Selects the strokes [ids] on Page [pageIndex], framed by their ink box; clears the selection if none is there. */
    fun select(pageIndex: Int, ids: Collection<Long>) {
        dragging = null
        val wanted = ids.toHashSet()
        val strokes = _strokes.value.filter { it.id in wanted && it.pageIndex == pageIndex }
        _selection.value = if (strokes.isEmpty()) null else Selection(pageIndex, strokes.map { it.id }, SelectionFrame.around(strokes), null)
    }

    override fun beginDrag(kind: DragKind, pagePoint: Offset, pageSize: Size) {
        val selection = _selection.value ?: return
        val ids = selection.ids.toHashSet()
        val strokes = _strokes.value.filter { it.id in ids }
        if (strokes.isEmpty()) return
        dragging = Drag(kind, pagePoint, Rect(Offset.Zero, pageSize), strokes, inkBounds(strokes), selection.frame)
    }

    override fun dragTo(pagePoint: Offset) {
        val drag = dragging ?: return
        val transform = when (val kind = drag.kind) {
            DragKind.Move -> move(drag.box, drag.page, pagePoint - drag.start)
            // The pen may grab the handle off the corner itself (the frame is shown padded, and the handle has a
            // reach): the corner follows the pen's movement, not the pen, so the size doesn't jump at the first move.
            is DragKind.Resize -> resize(drag.frame, drag.box, drag.page, kind.corner, drag.frame.corners()[kind.corner] + (pagePoint - drag.start))
            DragKind.Rotate -> rotate(drag.frame, drag.strokes, drag.page, drag.start, pagePoint)
        }
        _selection.update { it?.copy(preview = transform) }
    }

    override fun endDrag(cancelled: Boolean) {
        val drag = dragging ?: return
        dragging = null
        val transform = _selection.value?.preview
        _selection.update { it?.copy(preview = null) }
        if (cancelled || transform == null || transform.isIdentity) return
        history.perform(TransformStrokes(drag.strokes, drag.strokes.map(transform::apply), drag.frame, drag.frame.transformed(transform)))
    }

    override fun deleteSelection() {
        val selection = _selection.value ?: return
        val ids = selection.ids.toHashSet()
        val strokes = _strokes.value.filter { it.id in ids }
        clearSelection()
        if (strokes.isNotEmpty()) history.perform(SwapStrokes(out = strokes, into = emptyList(), selectOnRevert = selection.frame))
    }

    override fun clearSelection() {
        dragging = null
        _selection.value = null
    }

    /**
     * Undo and redo put a selection back only while the Lasso tool is chosen, so the user can try again at once, and
     * only on a Page on screen: one out of sight would be deleted by the next Delete unseen.
     */
    private fun reselect(pageIndex: Int, ids: List<Long>, frame: SelectionFrame) {
        if (_tools.value.chosen != ToolChoice.Lasso || pageIndex !in visiblePages) return
        _selection.value = Selection(pageIndex, ids, frame, null)
    }

    /** One drag on the selection, pen down to up: what it started from. */
    private class Drag(
        val kind: DragKind,
        val start: Offset,
        val page: Rect,
        val strokes: List<InkStroke>,
        /** The ink box of [strokes]. */
        val box: Rect,
        val frame: SelectionFrame,
    )

    fun addStroke(stroke: InkStroke) {
        drawnHere += stroke.id
        // A stroke finished (another pointer, or a lift lost) while an erase runs goes on top of the list the erase
        // started from too, so the erase's places stay right.
        erasing?.let { it.before = it.before + stroke }
        history.perform(SwapStrokes(out = emptyList(), into = listOf(stroke)))
    }

    // Undo and redo wait for the pen to lift: an erase or a drag in progress is not in the history yet. Each clears the
    // selection; a lasso action's command selects its strokes again (reselect).
    fun undo() {
        if (erasing != null || dragging != null) return
        clearSelection()
        history.undo()
    }

    fun redo() {
        if (erasing != null || dragging != null) return
        clearSelection()
        history.redo()
    }

    override fun beginErase(eraser: Eraser) {
        // The side button may erase selected strokes, which would leave the selection pointing at nothing.
        clearSelection()
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
        /** A lasso delete: undoing it selects [out] again, in this frame (which keeps the angle a rotate gave it). */
        private val selectOnRevert: SelectionFrame? = null,
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
            selectOnRevert?.let { reselect(out.first().pageIndex, out.map { it.id }, it) }
        }

        /** Swaps back in memory only: for an erase cancelled before anything of it was saved. */
        fun revertOnScreen() {
            val (without, places) = _strokes.value.takeOut { it.id in intoIds }
            intoPlaces = places
            _strokes.value = without.putBack(checkNotNull(outPlaces) { "reverted before applied" })
        }

        fun save(removeIds: Set<Long>, add: List<InkStroke>) = watch(ink.replace(sheetId, removeIds.toList(), add))
    }

    /**
     * A lasso move, resize or rotate: [before] become [after], the same ids with the transform baked into their points.
     * Each is replaced where it is in the list, so the drawing order stays (SwapStrokes would put them on top).
     */
    private inner class TransformStrokes(
        private val before: List<InkStroke>,
        private val after: List<InkStroke>,
        private val frameBefore: SelectionFrame,
        private val frameAfter: SelectionFrame,
    ) : Command {
        private val ids = before.map { it.id }

        override fun apply() = put(after, frameAfter)

        override fun revert() = put(before, frameBefore)

        private fun put(strokes: List<InkStroke>, frame: SelectionFrame) {
            val byId = strokes.associateBy { it.id }
            _strokes.value = _strokes.value.map { byId[it.id] ?: it }
            watch(ink.replace(sheetId, ids, strokes))
            reselect(strokes.first().pageIndex, ids, frame)
        }
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
