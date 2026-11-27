package com.violinsheet.core.ink

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesView

/**
 * What InkLayer's pen input, its drawing and the low-latency view share. The inputs are set on every composition, as
 * rememberUpdatedState would, so the gesture code and the view's listener always see the latest.
 */
internal class InkLayerState(
    placements: List<PagePlacement>,
    tool: InkTool,
    eraser: Eraser,
    onStrokes: (List<InkStroke>) -> Unit,
    erase: EraseInput,
    selection: Selection?,
    lasso: LassoInput,
) {
    var placements by mutableStateOf(placements)
    var tool by mutableStateOf(tool)
    var eraser by mutableStateOf(eraser)
    var onStrokes by mutableStateOf(onStrokes)
    var erase by mutableStateOf(erase)
    var selection by mutableStateOf(selection)
    var lasso by mutableStateOf(lasso)

    /** The ids of the strokes InkLayer was given. */
    var ids by mutableStateOf(emptySet<Long>())

    var view by mutableStateOf<InProgressStrokesView?>(null)

    // Strokes handed to onStrokes but still shown by the low-latency view, with their ids. Removed from the view only
    // once all its parts are in the strokes list and drawn there, so a finished stroke never blinks out.
    val handedOver = mutableMapOf<InProgressStrokeId, List<Long>>()
    val wetStrokes = mutableMapOf<InProgressStrokeId, WetStroke>()

    // The eraser circle on screen (centre, radius) while erasing.
    var eraserCircle by mutableStateOf<Pair<Offset, Float>?>(null)

    // The lasso being drawn: its Page and its points in that Page's coordinates, so a zoom mid-lasso keeps it in place.
    var lassoPage by mutableStateOf<Int?>(null)
    val lassoPoints = mutableStateListOf<Offset>()

    fun placementOf(pageIndex: Int) = placements.firstOrNull { it.pageIndex == pageIndex }

    /** The Page whose box holds [screen], if any. */
    fun pageAt(screen: Offset) = placements.firstOrNull { it.holds(screen) }

    /** The Page nearest to [screen]: the one holding it, as [pageAt] says, if any. */
    fun nearestPage(screen: Offset) = placements.minByOrNull {
        val box = it.screenRect
        val dx = maxOf(box.left - screen.x, 0f, screen.x - box.right)
        val dy = maxOf(box.top - screen.y, 0f, screen.y - box.bottom)
        dx * dx + dy * dy
    }
}
