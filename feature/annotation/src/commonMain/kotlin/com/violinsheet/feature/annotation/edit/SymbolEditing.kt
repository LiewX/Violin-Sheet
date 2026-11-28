package com.violinsheet.feature.annotation.edit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.violinsheet.core.data.symbol.Symbol
import com.violinsheet.core.ink.FrameHit
import com.violinsheet.core.ink.SelectionFrame
import com.violinsheet.core.ink.StrokeTransform
import com.violinsheet.core.ink.eraserPath
import com.violinsheet.core.ink.keepInside
import com.violinsheet.core.ink.move
import com.violinsheet.core.ink.resize
import com.violinsheet.feature.annotation.UndoStack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The colour of a placed Symbol until #14 gives Edit mode a palette. */
private const val SYMBOL_BLACK = 0xFF000000.toInt()

/** Eraser positions are never checked closer than this (Page points), whatever the reach. */
private const val MIN_ERASE_STEP = 0.5f

/**
 * The selected Symbol: its [frame] (the glyph box, unturned) and, during a move or resize, the [preview] transform
 * not yet baked in.
 */
data class SymbolSelection(val id: Long, val pageIndex: Int, val frame: SelectionFrame, val preview: StrokeTransform?)

/**
 * One open Sheet's Symbols and Edit mode's gestures on them: placing, selecting, moving, resizing, deleting and
 * erasing, each one undo step ([ReplaceSymbols]) in the ViewModel's [history], shared with Ink. Owned by
 * AnnotationViewModel, like LassoSelection. Only Symbols of a known kind are held: an unknown one is never shown, so
 * nothing here can delete it.
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

    private var visiblePages = emptyMap<Int, Size>()
    private var drag: Drag? = null
    private var pending: Pending? = null
    private var erase: Erase? = null

    /** A pen gesture is in progress (a drag, a pending placement or an erase): it is not in the history yet. */
    val busy: Boolean get() = drag != null || pending != null || erase != null

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

    override fun penDown(pageIndex: Int, point: Offset, pageSize: Size, reach: Float, frameHit: FrameHit, frameMinSide: Float) {
        drag = null
        pending = null
        val tool = editTools.value.chosen
        if (mode.value != Mode.Edit || (tool != EditTool.Symbol && tool != EditTool.Select)) return
        val page = Rect(Offset.Zero, pageSize)
        val selection = _selection.value?.takeIf { it.pageIndex == pageIndex }
        val selected = selection?.let { s -> _symbols.value.find { it.id == s.id } }
        if (selection != null && selected != null) {
            when (frameHit) {
                is FrameHit.Corner -> return startDrag(selected, selection.frame, page, point, frameHit.index, frameMinSide)
                FrameHit.Inside -> return startDrag(selected, selection.frame, page, point, null)
                else -> Unit
            }
        }
        val hit = symbolAt(pageIndex, point, reach)
        when {
            // Decision 4: one gesture selects it and moves it; a still lift only selects.
            hit != null -> startDrag(hit, select(hit).frame, page, point, null)
            // Decision 3: a tap on empty Page places even while a Symbol is selected.
            tool == EditTool.Symbol -> pending = Pending(pageIndex, point, page)
            else -> clearSelection()
        }
    }

    override fun dragTo(point: Offset) {
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
            val s = drag.symbol
            val centre = transform.apply(Offset(s.x, s.y))
            history.perform(ReplaceSymbols(listOf(s), listOf(s.copy(x = centre.x, y = centre.y, scale = s.scale * transform.scale)), this))
            return
        }
        val placing = pending ?: return
        pending = null
        if (cancelled) return
        val kind = editTools.value.kind
        // The chosen kind decides what the gesture from empty Page means (design §4, "Room for #16/#17").
        when (kind.shape) {
            // One glyph: a tap places it, a drag does nothing.
            SymbolShape.Glyph -> if (!moved) place(kind, placing)
            // #16/#17 add SymbolShape.TwoEndpoint here: a drag places from placing.down to upPoint, and a tap is the
            // first of two taps.
        }
    }

    override fun beginErase() {
        if (mode.value != Mode.Edit) return
        clearSelection()
        pending = null
        erase = Erase()
    }

    override fun eraseAt(pageIndex: Int, point: Offset, reach: Float) {
        val erase = erase ?: return
        val path = eraserPath(erase.last?.takeIf { erase.lastPage == pageIndex }, point, reach.coerceAtLeast(MIN_ERASE_STEP))
        erase.last = point
        erase.lastPage = pageIndex
        val hit = _symbols.value.filter { s -> s.pageIndex == pageIndex && boxOf(s).inflate(reach).let { box -> path.any { box.holds(it) } } }
        if (hit.isEmpty()) return
        erase.taken += hit
        val gone = hit.mapTo(HashSet()) { it.id }
        _symbols.update { list -> list.filter { it.id !in gone } }
    }

    override fun eraserOffPage() {
        erase?.last = null
    }

    override fun endErase(cancelled: Boolean) {
        val erase = erase ?: return
        this.erase = null
        if (erase.taken.isEmpty()) return
        val taken = erase.taken.sortedBy { it.id }
        if (cancelled) {
            // Nothing of it was saved yet: put them back on screen only.
            _symbols.update { (it + taken).sortedBy { s -> s.id } }
            return
        }
        save(taken.map { it.id }, emptyList())
        history.record(ReplaceSymbols(taken, emptyList(), this))
    }

    override fun deleteSelection() {
        val id = _selection.value?.id ?: return
        val selected = _symbols.value.find { it.id == id }
        clearSelection()
        if (selected != null) history.perform(ReplaceSymbols(listOf(selected), emptyList(), this))
    }

    override fun clearSelection() {
        drag = null
        _selection.value = null
    }

    /** Takes [out] out of the list and adds [into], keeping it sorted by id, and saves the change. */
    fun swap(out: List<Symbol>, into: List<Symbol>) {
        val outIds = out.mapTo(HashSet()) { it.id }
        _symbols.update { list -> (list.filter { it.id !in outIds } + into).sortedBy { it.id } }
        save(outIds.toList(), into)
    }

    /**
     * After an undo step: [symbol] is selected again (or the selection cleared) only while the Symbol or Select tool
     * is chosen in Edit mode and its Page is on screen (Decision 5, the lasso's rule).
     */
    fun reselect(symbol: Symbol?) {
        val tool = editTools.value.chosen
        val shows = mode.value == Mode.Edit && (tool == EditTool.Symbol || tool == EditTool.Select)
        _selection.value = symbol?.takeIf { shows && it.pageIndex in visiblePages }?.let { selectionOf(it) }
    }

    private fun place(kind: SymbolKind, at: Pending) {
        val box = kind.boxOnPage(at.down.x, at.down.y, 1f)
        val shift = keepInside(box, at.page)
        val symbol = Symbol(newId(), at.pageIndex, kind.stored, at.down.x + shift.x, at.down.y + shift.y, 1f, SYMBOL_BLACK)
        placedHere += symbol.id
        history.perform(ReplaceSymbols(emptyList(), listOf(symbol), this))
    }

    private fun startDrag(symbol: Symbol, frame: SelectionFrame, page: Rect, start: Offset, corner: Int?, frameMinSide: Float = 0f) {
        val half = frameMinSide / 2
        val shown = frame.copy(halfSize = Size(maxOf(frame.halfSize.width, half), maxOf(frame.halfSize.height, half)))
        drag = Drag(symbol, frame, shown, boxOf(symbol), page, start, corner)
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

    private fun selectionOf(symbol: Symbol): SymbolSelection {
        val box = boxOf(symbol)
        return SymbolSelection(symbol.id, symbol.pageIndex, SelectionFrame(box.center, box.size / 2f, 0f), null)
    }

    /** Every Symbol held is of a known kind (loaded filters the rest out). */
    private fun boxOf(symbol: Symbol): Rect =
        checkNotNull(SymbolKind.fromStored(symbol.kind)) { "unknown kind ${symbol.kind}" }.boxOnPage(symbol.x, symbol.y, symbol.scale)

    /** Inclusive of the edges, unlike Rect.contains. */
    private fun Rect.holds(p: Offset) = p.x in left..right && p.y in top..bottom

    /**
     * A move ([corner] null) or a resize from [corner] of [frame], from [start]; [shown] is [frame] as drawn (at least
     * the minimum side), [box] the glyph box.
     */
    private class Drag(
        val symbol: Symbol,
        val frame: SelectionFrame,
        val shown: SelectionFrame,
        val box: Rect,
        val page: Rect,
        val start: Offset,
        val corner: Int?,
    )

    /** A pen down on empty Page with the Symbol tool, waiting for the lift to say what it was. */
    private class Pending(val pageIndex: Int, val down: Offset, val page: Rect)

    /** One erase gesture: the Symbols it took out so far, and where the eraser last was. */
    private class Erase {
        val taken = mutableListOf<Symbol>()
        var last: Offset? = null
        var lastPage = -1
    }
}
