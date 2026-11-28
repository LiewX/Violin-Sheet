package com.violinsheet.feature.annotation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinsheet.core.data.ink.InkRepository
import com.violinsheet.core.data.settings.SettingsStore
import com.violinsheet.core.data.symbol.Symbol
import com.violinsheet.core.data.symbol.SymbolRepository
import com.violinsheet.core.ink.DragKind
import com.violinsheet.core.ink.EraseInput
import com.violinsheet.core.ink.Eraser
import com.violinsheet.core.ink.InkStroke
import com.violinsheet.core.ink.LassoInput
import com.violinsheet.core.ink.Selection
import com.violinsheet.core.ink.SelectionFrame
import com.violinsheet.core.ink.strokeIds
import com.violinsheet.feature.annotation.edit.EditInput
import com.violinsheet.feature.annotation.edit.EditTools
import com.violinsheet.feature.annotation.edit.Mode
import com.violinsheet.feature.annotation.edit.symbol.SymbolEditing
import com.violinsheet.feature.annotation.edit.symbol.SymbolSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val symbolRepository: SymbolRepository,
    private val settings: SettingsStore,
    private val eraserMemory: EraserMemory,
    /** Gives the id of a placed Symbol, a pasted stroke, or a piece that can't keep its stroke's place (pieceIds). */
    private val newStrokeId: () -> Long = strokeIds::next,
) : ViewModel(), EraseInput, LassoInput {
    private val _strokes = MutableStateFlow(emptyList<InkStroke>())
    val strokes: StateFlow<List<InkStroke>> = _strokes.asStateFlow()

    private val history = UndoStack()
    val canUndo: StateFlow<Boolean> = history.canUndo
    val canRedo: StateFlow<Boolean> = history.canRedo

    private val toolsState = InkToolsState(settings, eraserMemory, viewModelScope)

    /** The toolbar's choices: see InkToolsState for which last only while this Sheet is open. */
    val tools: StateFlow<InkTools> = toolsState.tools

    private val _saveFailed = MutableStateFlow(false)

    /** A change couldn't be saved: the screen shows it, but reopening the Sheet wouldn't. */
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    /** Ids of strokes drawn while this Sheet is open: for those, memory wins over what the database load returns. */
    private val drawnHere = HashSet<Long>()

    private val lasso = LassoSelection(
        _strokes, history, tools, { remove, add -> watch(ink.replace(sheetId, remove, add)) },
        { strokes, frame -> SwapStrokes(out = strokes, into = emptyList(), selectOnRevert = frame) },
        newStrokeId, drawnHere,
    )
    val selection: StateFlow<Selection?> = lasso.selection

    /** Something has been copied, so Paste has something to do. */
    val canPaste: StateFlow<Boolean> = lasso.canPaste

    private val inkErasing = InkErasing(_strokes, newStrokeId, drawnHere) { out, into, places, cancelled ->
        val command = SwapStrokes(out, into, places)
        if (cancelled) {
            command.revertOnScreen()
        } else {
            command.save(out.mapTo(HashSet()) { it.id }, into)
            history.record(command)
        }
    }

    private val _mode = MutableStateFlow(Mode.Ink)

    /** What the pen does; a Sheet always opens in Ink mode (Decision 7). */
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    private val _editTools = MutableStateFlow(EditTools())

    /** Edit mode's toolbar choices, kept only while this Sheet is open. */
    val editTools: StateFlow<EditTools> = _editTools.asStateFlow()

    private val symbolEditing = SymbolEditing(
        history, mode, editTools, { remove, add -> watch(symbolRepository.replace(sheetId, remove, add)) }, newStrokeId,
    )

    /** The Symbols on screen, sorted by id (the last on top); Symbols of a kind this version doesn't know are left out. */
    val symbols: StateFlow<List<Symbol>> = symbolEditing.symbols
    val symbolSelection: StateFlow<SymbolSelection?> = symbolEditing.selection

    /** The Symbol being placed (pen down, not lifted yet): drawn on top, not saved. */
    val symbolPlacing: StateFlow<Symbol?> = symbolEditing.placing

    /**
     * Edit mode's pen input. A separate object rather than methods of this ViewModel: its dragTo, endErase,
     * deleteSelection and clearSelection would clash with LassoInput's and EraseInput's.
     */
    val edit: EditInput = symbolEditing

    init {
        viewModelScope.launch {
            // Strokes drawn before the saved ones load are kept, after them. One drawn here is left out of the load
            // even if already saved: it may have been undone since the load read it.
            val saved = ink.strokes(sheetId).filter { it.id !in drawnHere }
            _strokes.update { drawn -> saved + drawn }
            inkErasing.loaded(saved)
        }
        // Likewise for Symbols (placedHere in SymbolEditing).
        viewModelScope.launch { symbolEditing.loaded(symbolRepository.symbols(sheetId)) }
    }

    /** Ink, Edit or Text mode: either mode's selection ends. */
    fun setMode(mode: Mode) {
        clearSelection()
        _mode.value = mode
    }

    /** Edit mode's toolbar now shows [tools]; another tool ends the Symbol selection. */
    fun setEditTools(tools: EditTools) {
        if (tools.chosen != _editTools.value.chosen) symbolEditing.clearSelection()
        _editTools.value = tools
    }

    /** A pen gesture is in progress that is not in the history yet: undo, redo and paste wait for the lift. */
    private val gestureRunning: Boolean get() = inkErasing.active || lasso.dragging || symbolEditing.busy

    /** The toolbar now shows [tools]; with [save] off (a preset slider mid-drag) its app-wide changes wait for the release. */
    fun setTools(tools: InkTools, save: Boolean = true) {
        if (tools.chosen != this.tools.value.chosen) clearSelection()
        toolsState.set(tools, save)
    }

    /** The Pages on screen are now [pages], each with its size in Page points (a page turn): a selection on a Page no longer shown ends. */
    fun onVisiblePages(pages: Map<Int, Size>) {
        lasso.onVisiblePages(pages)
        symbolEditing.onVisiblePages(pages)
    }

    /** The pen went down on Page [pageIndex], with any tool: Paste goes there. */
    fun onPenDown(pageIndex: Int) = lasso.onPenDown(pageIndex)

    fun copySelection() = lasso.copySelection()

    fun paste() {
        // Like undo: a paste waits for the pen to lift, as an erase or a drag in progress is not in the history yet.
        if (gestureRunning) return
        lasso.paste()
    }

    override fun lassoDone(pageIndex: Int, pagePoints: List<Offset>) = lasso.lassoDone(pageIndex, pagePoints)

    override fun tapAt(pageIndex: Int, pagePoint: Offset, slop: Float) = lasso.tapAt(pageIndex, pagePoint, slop)

    /** Selects the strokes [ids] on Page [pageIndex], framed by their ink box; clears the selection if none is there. */
    fun select(pageIndex: Int, ids: Collection<Long>) = lasso.select(pageIndex, ids)

    override fun beginDrag(kind: DragKind, pagePoint: Offset, pageSize: Size) = lasso.beginDrag(kind, pagePoint, pageSize)

    override fun dragTo(pagePoint: Offset) = lasso.dragTo(pagePoint)

    override fun endDrag(cancelled: Boolean) = lasso.endDrag(cancelled)

    // Each mode has its own selection, and setMode clears both, so at most one of them is there.
    override fun deleteSelection() {
        lasso.deleteSelection()
        symbolEditing.deleteSelection()
    }

    /** The Edit selection's Symbols take the colour [argb], as one undo step (none if they all have it). #14 adds the swatches. */
    fun recolourSymbols(argb: Int) = symbolEditing.recolourSelection(argb)

    override fun clearSelection() {
        lasso.clearSelection()
        symbolEditing.clearSelection()
    }

    /**
     * A finger tapped the Sheet (palm rules already applied by the viewer): a tap between the edges ends the
     * selection, Ink lasso's or Edit's. An edge tap ([atEdge]) only turns the Page, and the turn clears a selection only
     * when its Page leaves the screen ([onVisiblePages]). Not while a pen gesture runs, as clearing would drop its drag,
     * placement or erase midway.
     */
    fun onFingerTap(atEdge: Boolean = false) {
        if (atEdge || gestureRunning) return
        clearSelection()
    }

    /** One drawn stroke, as [parts]: one per Page it ran over (InkStroke.splitAtPages). One undo step takes them all. */
    fun addStrokes(parts: List<InkStroke>) {
        if (parts.isEmpty()) return
        parts.mapTo(drawnHere) { it.id }
        // A stroke finished (another pointer, or a lift lost) while an erase runs goes on top of the list the erase
        // started from too, so the erase's places stay right.
        inkErasing.drawn(parts)
        history.perform(SwapStrokes(out = emptyList(), into = parts))
    }

    // Undo and redo wait for the pen to lift: an erase or a drag in progress is not in the history yet. Each clears the
    // selection; a lasso action's command selects its strokes again (reselect).
    fun undo() {
        if (gestureRunning) return
        clearSelection()
        history.undo()
    }

    fun redo() {
        if (gestureRunning) return
        clearSelection()
        history.redo()
    }

    override fun beginErase(eraser: Eraser) {
        // The side button may erase selected strokes, which would leave the selection pointing at nothing.
        clearSelection()
        inkErasing.begin(eraser)
    }

    override fun eraseAt(pageIndex: Int, at: Offset) = inkErasing.eraseAt(pageIndex, at)

    override fun eraserOffPage() = inkErasing.offPage()

    override fun endErase(cancelled: Boolean) = inkErasing.end(cancelled)

    /** Flags [saveFailed] if the queued write fails. The write itself runs on even after this ViewModel is gone. */
    private fun watch(write: Deferred<Unit>) {
        write.invokeOnCompletion { e -> if (e != null && e !is CancellationException) _saveFailed.value = true }
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
            selectOnRevert?.let { lasso.reselect(out.first().pageIndex, out.map { it.id }, it) }
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
