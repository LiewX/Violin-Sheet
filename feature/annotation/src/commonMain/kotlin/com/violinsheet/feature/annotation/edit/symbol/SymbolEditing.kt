package com.violinsheet.feature.annotation.edit.symbol

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.violinsheet.core.data.symbol.Symbol
import com.violinsheet.core.ink.FrameHit
import com.violinsheet.core.ink.SelectionFrame
import com.violinsheet.core.ink.keepInside
import com.violinsheet.core.ink.move
import com.violinsheet.core.ink.resize
import com.violinsheet.feature.annotation.UndoStack
import com.violinsheet.feature.annotation.edit.EditInput
import com.violinsheet.feature.annotation.edit.EditTool
import com.violinsheet.feature.annotation.edit.EditTools
import com.violinsheet.feature.annotation.edit.Mode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The colour of a placed Symbol until #14 gives Edit mode a palette. */
private const val SYMBOL_BLACK = 0xFF000000.toInt()

/**
 * One open Sheet's Symbols and Edit mode's gestures on them: placing, selecting (one, or several by a loop), moving,
 * resizing (one only), recolouring, deleting and erasing, each one undo step ([ReplaceSymbols]) in the ViewModel's
 * [history], shared with Ink. Owned by AnnotationViewModel, like LassoSelection. Only Symbols of a known kind are
 * held: an unknown one is never shown, so nothing here can delete it.
 */
internal class SymbolEditing(
    private val history: UndoStack,
    private val mode: StateFlow<Mode>,
    private val editTools: StateFlow<EditTools>,
    /** Saves [add] in place of the Symbols with the ids [removeIds]. */
    private val save: (removeIds: List<Long>, add: List<Symbol>) -> Unit,
    /** Gives a placed Symbol's id (the app's shared stroke ids). */
    private val newId: () -> Long,
) : EditInput {
    private val _symbols = MutableStateFlow(emptyList<Symbol>())

    /** The Symbols on screen, sorted by id (= placing order, so the last is on top). */
    val symbols: StateFlow<List<Symbol>> = _symbols.asStateFlow()

    private val _selection = MutableStateFlow<SymbolSelection?>(null)
    val selection: StateFlow<SymbolSelection?> = _selection.asStateFlow()

    /** Ids of Symbols placed while this Sheet is open: for those, memory wins over what the database load returns. */
    private val placedHere = HashSet<Long>()

    private val _placing = MutableStateFlow<Symbol?>(null)

    /** The Symbol under the pen while it is being placed: drawn on top, not in [symbols], not saved, not in the history. */
    val placing: StateFlow<Symbol?> = _placing.asStateFlow()

    private var visiblePages = emptyMap<Int, Size>()
    private var drag: Drag? = null
    private var placement: Placement? = null
    private val erasing = SymbolErasing(_symbols, history, save) { taken -> ReplaceSymbols(taken, emptyList(), this) }

    /** A pen gesture is in progress (a drag, a placement or an erase): it is not in the history yet. */
    val busy: Boolean get() = drag != null || placement != null || erasing.busy

    /** The saved Symbols have loaded: they go under any placed before the load landed, by id. */
    fun loaded(saved: List<Symbol>) {
        val shown = saved.filter { it.id !in placedHere && SymbolKind.fromStored(it.kind) != null }
        _symbols.update { placed -> (shown + placed).sortedBy { it.id } }
    }

    /** The Pages on screen are now [pages] (a page turn): a selection on a Page no longer shown ends. */
    fun onVisiblePages(pages: Map<Int, Size>) {
        visiblePages = pages
        val selected = _selection.value ?: return
        if (selected.pageIndex !in pages) clearSelection()
    }

    override fun penDown(pageIndex: Int, point: Offset, pageSize: Size, reach: Float, frameHit: FrameHit, frameMinSide: Float): Boolean {
        drag = null
        dropPlacement()
        val tool = editTools.value.chosen
        if (mode.value != Mode.Edit || (tool != EditTool.Symbol && tool != EditTool.Lasso)) return false
        val page = Rect(Offset.Zero, pageSize)
        val selection = _selection.value?.takeIf { it.pageIndex == pageIndex }
        val selected = selection?.let { s -> _symbols.value.filter { it.id in s.ids } }.orEmpty()
        if (selection != null && selected.isNotEmpty()) {
            when {
                // Several only move: their corners are no handles (the pen layer sends Inside there anyway).
                frameHit is FrameHit.Corner && selected.size == 1 -> {
                    startDrag(selected, selection.frame, page, point, frameHit.index, frameMinSide)
                    return false
                }
                // A still lift on empty space inside the frame changes nothing (7a.9, as the Ink lasso).
                frameHit is FrameHit.Corner || frameHit == FrameHit.Inside -> {
                    startDrag(selected, selection.frame, page, point, null)
                    return false
                }
                else -> Unit
            }
        }
        val hit = symbolAt(pageIndex, point, reach)
        // Decision 4: one gesture selects it and moves it; a still lift only selects.
        if (hit != null) startDrag(listOf(hit), select(hit).frame, page, point, null)
        // Decision 3: empty Page places even while a Symbol is selected; the new one shows at once (#27).
        else if (tool == EditTool.Symbol) startPlacement(pageIndex, point, page)
        // The Lasso: the selection ends and a loop starts (#18: an unselected Cover will start one too).
        else {
            clearSelection()
            return true
        }
        return false
    }

    override fun dragTo(point: Offset) {
        placement?.let { return showPlacement(it, point) }
        val drag = drag ?: return
        val transform = when (val corner = drag.corner) {
            null -> move(drag.box, drag.page, point - drag.start)
            else -> resize(drag.frame, drag.box, drag.page, corner, resizeTarget(drag, corner, point))
        }
        _selection.update { it?.copy(preview = transform) }
    }

    override fun penUp(upPoint: Offset, moved: Boolean, cancelled: Boolean) {
        drag?.let { drag ->
            this.drag = null
            val transform = _selection.value?.preview
            _selection.update { it?.copy(preview = null) }
            if (cancelled || !moved || transform == null || transform.isIdentity) return
            val after = drag.symbols.map { s ->
                val centre = transform.apply(Offset(s.x, s.y))
                s.copy(x = centre.x, y = centre.y, scale = s.scale * transform.scale)
            }
            history.perform(ReplaceSymbols(drag.symbols, after, this))
            return
        }
        val at = placement ?: return
        val symbol = _placing.value
        dropPlacement()
        if (cancelled || symbol == null) return
        // The kind chosen at pen down decides what the gesture from empty Page means (design §4, "Room for #16/#17").
        when (at.kind.shape) {
            // One glyph: placed where it was shown, after a tap or a drag (a lost lift too: the last shown position).
            SymbolShape.Glyph -> place(symbol)
            // #16/#17 add SymbolShape.TwoEndpoint here: a drag places from the down point to upPoint, and a tap is
            // the first of two taps.
        }
    }

    override fun beginErase() {
        if (mode.value != Mode.Edit) return
        clearSelection()
        erasing.begin()
    }

    override fun eraseAt(pageIndex: Int, point: Offset, reach: Float) = erasing.eraseAt(pageIndex, point, reach)

    override fun eraserOffPage() = erasing.offPage()

    override fun endErase(cancelled: Boolean) = erasing.end(cancelled)

    override fun deleteSelection() {
        val selected = selectedSymbols()
        clearSelection()
        if (selected.isNotEmpty()) history.perform(ReplaceSymbols(selected, emptyList(), this))
    }

    /**
     * The selected Symbols take the colour [argb], as one undo step; they stay selected. None changing (all already
     * [argb]) is no step. Not during a pen gesture: a drag holds the Symbols as they were at pen down.
     */
    fun recolourSelection(argb: Int) {
        if (busy) return
        val selected = selectedSymbols()
        if (selected.none { it.colour != argb }) return
        history.perform(ReplaceSymbols(selected, selected.map { it.copy(colour = argb) }, this))
    }

    override fun loopDone(pageIndex: Int, loop: List<Offset>) {
        if (mode.value != Mode.Edit) return
        val caught = symbolsInLoop(_symbols.value, pageIndex, loop)
        _selection.value = if (caught.isEmpty()) null else selectionOf(caught)
    }

    /** Also drops a placement in progress (a tool or mode switch mid-placement): nothing is placed, saved or undoable. */
    override fun clearSelection() {
        drag = null
        dropPlacement()
        _selection.value = null
    }

    /** Takes [out] out of the list and adds [into], keeping it sorted by id, and saves the change. */
    fun swap(out: List<Symbol>, into: List<Symbol>) {
        val outIds = out.mapTo(HashSet()) { it.id }
        _symbols.update { list -> (list.filter { it.id !in outIds } + into).sortedBy { it.id } }
        save(outIds.toList(), into)
    }

    /**
     * After an undo step: [symbols] are selected again (none clears the selection) only while the Symbol or Lasso
     * tool is chosen in Edit mode and they are all on one Page on screen (Decision 5, the lasso's rule; 7a.5).
     */
    fun reselect(symbols: List<Symbol>) {
        val tool = editTools.value.chosen
        val shows = mode.value == Mode.Edit && (tool == EditTool.Symbol || tool == EditTool.Lasso)
        val page = symbols.firstOrNull()?.pageIndex
        val onePage = page != null && page in visiblePages && symbols.all { it.pageIndex == page }
        _selection.value = if (shows && onePage) selectionOf(symbols) else null
    }

    /** Pen down on empty Page with the Symbol tool: the selection ends and the chosen kind shows under the pen. */
    private fun startPlacement(pageIndex: Int, point: Offset, page: Rect) {
        clearSelection()
        val kind = editTools.value.kind
        val at = Placement(kind, page)
        placement = at
        _placing.value = Symbol(newId(), pageIndex, kind.stored, point.x, point.y, 1f, SYMBOL_BLACK)
        showPlacement(at, point)
    }

    /** The placement centred on [point], kept inside its start Page (design 7a.3: it never moves to another Page). */
    private fun showPlacement(at: Placement, point: Offset) {
        val shift = keepInside(at.kind.boxOnPage(point.x, point.y, 1f), at.page)
        _placing.update { it?.copy(x = point.x + shift.x, y = point.y + shift.y) }
    }

    private fun dropPlacement() {
        placement = null
        _placing.value = null
    }

    private fun place(symbol: Symbol) {
        placedHere += symbol.id
        history.perform(ReplaceSymbols(emptyList(), listOf(symbol), this))
    }

    private fun startDrag(symbols: List<Symbol>, frame: SelectionFrame, page: Rect, start: Offset, corner: Int?, frameMinSide: Float = 0f) {
        val half = frameMinSide / 2
        val shown = frame.copy(halfSize = Size(maxOf(frame.halfSize.width, half), maxOf(frame.halfSize.height, half)))
        drag = Drag(symbols, frame, shown, boxAround(symbols), page, start, corner)
    }

    private fun selectedSymbols(): List<Symbol> {
        val ids = _selection.value?.ids ?: return emptyList()
        return _symbols.value.filter { it.id in ids }
    }

    /**
     * Where [corner] of the real frame goes with the pen at [point]: the pen's movement is put on the frame as shown
     * (its corner follows the pen there), and the scale that gives is applied to the real frame. resize then keeps the
     * Page-edge and 2 pt limits. On a tiny glyph this keeps the gain to the shown frame's, not the glyph's.
     */
    private fun resizeTarget(drag: Drag, corner: Int, point: Offset): Offset {
        val shown = drag.shown.corners()
        val shownAnchor = shown[(corner + 2) % 4]
        val shownDiagonal = shown[corner] - shownAnchor
        val pen = shown[corner] + (point - drag.start) - shownAnchor
        val lengthSquared = shownDiagonal.getDistanceSquared()
        val scale = if (lengthSquared == 0f) 1f else (pen.x * shownDiagonal.x + pen.y * shownDiagonal.y) / lengthSquared
        val real = drag.frame.corners()
        val anchor = real[(corner + 2) % 4]
        return anchor + (real[corner] - anchor) * scale
    }

    private fun select(symbol: Symbol): SymbolSelection = selectionOf(symbol).also { _selection.value = it }

    /** The topmost (highest id) Symbol on Page [pageIndex] whose box, widened by [reach], holds [point]. */
    private fun symbolAt(pageIndex: Int, point: Offset, reach: Float): Symbol? =
        _symbols.value.lastOrNull { it.pageIndex == pageIndex && boxOf(it).inflate(reach).holds(point) }

    /**
     * A move of [symbols] ([corner] null) or a resize from [corner] of [frame] (one Symbol only), from [start]; [shown]
     * is [frame] as drawn (at least the minimum side), [box] the union of their glyph boxes, so a move stops them all
     * as a group at the Page edge.
     */
    private class Drag(
        val symbols: List<Symbol>,
        val frame: SelectionFrame,
        val shown: SelectionFrame,
        val box: Rect,
        val page: Rect,
        val start: Offset,
        val corner: Int?,
    )

    /** A placement in progress: the [kind] chosen at pen down, on the start Page's bounds [page]. */
    private class Placement(val kind: SymbolKind, val page: Rect)
}
