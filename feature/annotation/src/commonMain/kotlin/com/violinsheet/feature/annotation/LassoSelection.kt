package com.violinsheet.feature.annotation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.violinsheet.core.ink.DragKind
import com.violinsheet.core.ink.InkStroke
import com.violinsheet.core.ink.LassoInput
import com.violinsheet.core.ink.Selection
import com.violinsheet.core.ink.SelectionFrame
import com.violinsheet.core.ink.inkBounds
import com.violinsheet.core.ink.lassoSelects
import com.violinsheet.core.ink.move
import com.violinsheet.core.ink.resize
import com.violinsheet.core.ink.rotate
import com.violinsheet.core.ink.strokeAt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The Lasso tool's selection on one open Sheet, and its move, resize, rotate and delete as undo steps. Owned by
 * [AnnotationViewModel], whose [strokes] and [history] it shares.
 */
internal class LassoSelection(
    private val strokes: MutableStateFlow<List<InkStroke>>,
    private val history: UndoStack,
    private val tools: StateFlow<InkTools>,
    /** Saves [add] in place of the strokes with the ids [removeIds]. */
    private val save: (removeIds: List<Long>, add: List<InkStroke>) -> Unit,
    /** The command that deletes [strokes] and, undone, selects them again in [frame] (reselect). */
    private val deleteCommand: (strokes: List<InkStroke>, frame: SelectionFrame) -> Command,
) : LassoInput {
    private val _selection = MutableStateFlow<Selection?>(null)
    val selection: StateFlow<Selection?> = _selection.asStateFlow()

    private var drag: Drag? = null

    /** A drag on the selection is in progress: it is not in the history yet. */
    val dragging: Boolean get() = drag != null

    /** The Pages on screen: a selection lasts only while its Page is shown. */
    private var visiblePages = emptySet<Int>()

    /** The Pages on screen are now [pages] (a page turn): a selection on a Page no longer shown ends. */
    fun onVisiblePages(pages: Set<Int>) {
        visiblePages = pages
        val selected = _selection.value ?: return
        if (selected.pageIndex !in pages) clearSelection()
    }

    override fun lassoDone(pageIndex: Int, pagePoints: List<Offset>) =
        select(pageIndex, lassoSelects(strokes.value, pageIndex, pagePoints, tools.value.includePartlyEnclosed))

    override fun tapAt(pageIndex: Int, pagePoint: Offset, slop: Float) =
        select(pageIndex, listOfNotNull(strokeAt(strokes.value, pageIndex, pagePoint, slop)))

    /** Selects the strokes [ids] on Page [pageIndex], framed by their ink box; clears the selection if none is there. */
    fun select(pageIndex: Int, ids: Collection<Long>) {
        drag = null
        val wanted = ids.toHashSet()
        val picked = strokes.value.filter { it.id in wanted && it.pageIndex == pageIndex }
        _selection.value = if (picked.isEmpty()) null else Selection(pageIndex, picked.map { it.id }, SelectionFrame.around(picked), null)
    }

    override fun beginDrag(kind: DragKind, pagePoint: Offset, pageSize: Size) {
        val selection = _selection.value ?: return
        val ids = selection.ids.toHashSet()
        val picked = strokes.value.filter { it.id in ids }
        if (picked.isEmpty()) return
        drag = Drag(kind, pagePoint, Rect(Offset.Zero, pageSize), picked, inkBounds(picked), selection.frame)
    }

    override fun dragTo(pagePoint: Offset) {
        val drag = drag ?: return
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
        val drag = drag ?: return
        this.drag = null
        val transform = _selection.value?.preview
        _selection.update { it?.copy(preview = null) }
        if (cancelled || transform == null || transform.isIdentity) return
        history.perform(TransformStrokes(drag.strokes, drag.strokes.map(transform::apply), drag.frame, drag.frame.transformed(transform)))
    }

    override fun deleteSelection() {
        val selection = _selection.value ?: return
        val ids = selection.ids.toHashSet()
        val picked = strokes.value.filter { it.id in ids }
        clearSelection()
        if (picked.isNotEmpty()) history.perform(deleteCommand(picked, selection.frame))
    }

    override fun clearSelection() {
        drag = null
        _selection.value = null
    }

    /**
     * Undo and redo put a selection back only while the Lasso tool is chosen, so the user can try again at once, and
     * only on a Page on screen: one out of sight would be deleted by the next Delete unseen.
     */
    fun reselect(pageIndex: Int, ids: List<Long>, frame: SelectionFrame) {
        if (tools.value.chosen != ToolChoice.Lasso || pageIndex !in visiblePages) return
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

        private fun put(changed: List<InkStroke>, frame: SelectionFrame) {
            val byId = changed.associateBy { it.id }
            strokes.value = strokes.value.map { byId[it.id] ?: it }
            save(ids, changed)
            reselect(changed.first().pageIndex, ids, frame)
        }
    }
}
