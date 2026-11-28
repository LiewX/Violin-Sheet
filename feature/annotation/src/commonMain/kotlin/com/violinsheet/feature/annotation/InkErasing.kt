package com.violinsheet.feature.annotation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.violinsheet.core.ink.EraseMode
import com.violinsheet.core.ink.Eraser
import com.violinsheet.core.ink.InkStroke
import com.violinsheet.core.ink.cutBy
import com.violinsheet.core.ink.eraserPath
import com.violinsheet.core.ink.mayReach
import com.violinsheet.core.ink.pieceIds
import com.violinsheet.core.ink.pointBounds
import com.violinsheet.core.ink.touchedBy
import kotlinx.coroutines.flow.MutableStateFlow

/** Eraser positions are checked at most this fraction of the eraser's radius apart, so it sweeps a solid band. */
private const val ERASER_STEP = 0.5f

/**
 * The Eraser's gesture on one open Sheet's strokes, applied as the eraser moves. Owned by [AnnotationViewModel], whose
 * strokes it shares. At the end of a gesture that took something, [finish] gets what was taken out, what was left in
 * its place and where the taken strokes were, and whether the gesture was cancelled: the ViewModel makes the undo step.
 */
internal class InkErasing(
    private val strokes: MutableStateFlow<List<InkStroke>>,
    private val newStrokeId: () -> Long,
    private val drawnHere: MutableSet<Long>,
    private val finish: (out: List<InkStroke>, into: List<InkStroke>, places: Places<InkStroke>, cancelled: Boolean) -> Unit,
) {
    private var erasing: EraseGesture? = null

    /** An erase is in progress, so it is not in the history yet. */
    val active: Boolean get() = erasing != null

    /** Each stroke's point box, for a cheap reject before the exact erase test. Kept only for the very stroke it is of. */
    private val bounds = HashMap<Long, Pair<InkStroke, Rect>>()

    private fun InkStroke.mayBeReachedBy(center: Offset, radius: Float): Boolean {
        val box = bounds[id]?.takeIf { it.first === this }?.second ?: pointBounds().also { bounds[id] = this to it }
        return mayReach(box, size, center, radius)
    }

    /** The saved strokes [saved] have loaded: an erase in progress started from the list without them. */
    fun loaded(saved: List<InkStroke>) {
        erasing?.let { it.before = saved + it.before }
    }

    /** Strokes [parts] were drawn: a stroke finished while an erase runs goes on top of the list the erase started from. */
    fun drawn(parts: List<InkStroke>) {
        erasing?.let { it.before = it.before + parts }
    }

    fun begin(eraser: Eraser) {
        erasing = EraseGesture(eraser, strokes.value)
    }

    fun eraseAt(pageIndex: Int, at: Offset) {
        val gesture = erasing ?: return
        val step = (gesture.eraser.size / 2 * ERASER_STEP).coerceAtLeast(0.5f)
        val path = eraserPath(gesture.last?.takeIf { gesture.lastPage == pageIndex }, at, step)
        gesture.last = at
        gesture.lastPage = pageIndex
        var list = strokes.value
        for (center in path) list = gesture.erase(list, pageIndex, center) ?: continue
        if (list !== strokes.value) strokes.value = list
    }

    fun offPage() {
        erasing?.last = null
    }

    fun end(cancelled: Boolean) {
        val gesture = erasing ?: return
        erasing = null
        bounds.keys.retainAll(strokes.value.mapTo(HashSet()) { it.id })
        if (gesture.originals.isEmpty()) return
        val out = gesture.originals.values.toList()
        val outIds = gesture.originals.keys
        val into = strokes.value.filter { gesture.made[it.id] === it }
        val places = Places(gesture.before.withIndex().filter { it.value.id in outIds }, gesture.before.size - outIds.size)
        finish(out, into, places, cancelled)
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
}
