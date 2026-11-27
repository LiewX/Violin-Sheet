package com.violinsheet.core.ink

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import androidx.compose.ui.graphics.drawscope.Stroke as Outline

/** Far longer than the strokes list takes to show a handed-over stroke (a frame), short enough for an undone one. */
private const val UNDONE_WET_STROKE_MILLIS = 300L

/**
 * Reads an Ink API stroke (in its wet stroke's Page coordinates) into the shared format, one stroke per Page it ran
 * over in [placements] (splitAtPages); none if it never touched a Page.
 */
internal fun Stroke.toInkStrokes(wet: WetStroke, placements: List<PagePlacement>): List<InkStroke> {
    val input = StrokeInput()
    val points = List(inputs.size) { i ->
        inputs.populate(i, input)
        InkPoint(input.x, input.y, input.pressure, input.elapsedTimeMillis)
    }
    val pageIndex = wet.start.pageIndex
    // Strokes finish on the main thread only, as strokeIds needs.
    val stroke = InkStroke(strokeIds.next(), pageIndex, brush.colorIntArgb, brush.size, points, wet.tool)
    // Cut where the Pages are now, as the wet stroke was drawn through the latest placement too.
    val reference = placements.firstOrNull { it.pageIndex == pageIndex } ?: wet.start
    return stroke.splitAtPages(reference, placements, strokeIds::next)
}

@Composable
actual fun InkLayer(
    placements: List<PagePlacement>,
    strokes: List<InkStroke>,
    tool: InkTool,
    eraser: Eraser,
    onStrokes: (List<InkStroke>) -> Unit,
    erase: EraseInput,
    selection: Selection?,
    lasso: LassoInput,
    selectionColour: Color,
    modifier: Modifier,
) {
    val painter = remember { StrokePainter() }
    val state = remember { InkLayerState(placements, tool, eraser, onStrokes, erase, selection, lasso) }
    val ids = remember(strokes) { strokes.mapTo(HashSet()) { it.id }.also(painter::keepOnly) }
    state.placements = placements
    state.tool = tool
    state.eraser = eraser
    state.onStrokes = onStrokes
    state.erase = erase
    state.selection = selection
    state.lasso = lasso
    state.ids = ids
    val selectedIds = remember(selection?.ids) { selection?.ids?.toHashSet().orEmpty() }
    // Reused by every frame of a lasso or a drag, rather than allocated per draw.
    val lassoPath = remember { Path() }
    val framePath = remember { Path() }
    val density = LocalDensity.current
    val lassoDash = remember(density) { with(density) { PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())) } }
    val hostView = LocalView.current

    Box(modifier.penInput(state, hostView)) {
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas { painter.draw(it.nativeCanvas, placements, strokes, selection, selectedIds) }
            // Until then the low-latency view shows the wet stroke as drawn, untrimmed and uncut. It goes once all its
            // parts are drawn here, so a part trimmed off every Page disappears only then.
            val drawnNow = state.handedOver.filterValues { parts -> parts.all { it in ids } }.keys
            if (drawnNow.isNotEmpty()) {
                state.handedOver.keys.removeAll(drawnNow)
                state.view?.post { state.view?.removeFinishedStrokes(drawnNow) }
            }
        }
        // Its own Canvas, so moving the circle doesn't redraw the strokes.
        Canvas(Modifier.fillMaxSize()) {
            state.eraserCircle?.let { (center, radius) ->
                drawCircle(Color.White, radius, center, style = Outline(width = 3f))
                drawCircle(Color.DarkGray, radius, center, style = Outline(width = 1.5f))
            }
        }
        // The lasso being drawn: a dashed outline, never a stroke, so it never reaches onStrokes.
        Canvas(Modifier.fillMaxSize()) {
            val page = state.lassoPage?.let { index -> placements.firstOrNull { it.pageIndex == index } } ?: return@Canvas
            drawLasso(page, state.lassoPoints, lassoPath, lassoDash)
        }
        Canvas(Modifier.fillMaxSize()) {
            val selected = selection ?: return@Canvas
            val page = placements.firstOrNull { it.pageIndex == selected.pageIndex } ?: return@Canvas
            drawSelectionFrame(selected, page, selectionColour, framePath)
        }
        // Renders wet strokes only. It handles no touches, so every pointer reaches the Compose handlers.
        AndroidView(
            factory = { context ->
                InProgressStrokesView(context).apply {
                    eagerInit()
                    addFinishedStrokesListener(
                        object : InProgressStrokesFinishedListener {
                            override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                                for ((id, stroke) in strokes) {
                                    val wet = state.wetStrokes.remove(id)
                                    if (wet == null) {
                                        removeFinishedStrokes(setOf(id)) // not ours to keep; don't leave it on screen
                                        continue
                                    }
                                    val parts = stroke.toInkStrokes(wet, state.placements)
                                    if (parts.isEmpty()) {
                                        removeFinishedStrokes(setOf(id)) // drawn off every Page: nothing to keep
                                        continue
                                    }
                                    state.handedOver[id] = parts.map { it.id }
                                    state.onStrokes(parts)
                                    // Undone before the strokes list ever showed it (undo tapped within a frame of the
                                    // lift), the draw above never sees it to take it out: drop it here once the list
                                    // has surely caught up.
                                    postDelayed({
                                        if (state.handedOver[id]?.let { parts -> !parts.all { it in state.ids } } == true) {
                                            state.handedOver.remove(id)
                                            removeFinishedStrokes(setOf(id))
                                        }
                                    }, UNDONE_WET_STROKE_MILLIS)
                                }
                            }
                        },
                    )
                    state.view = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
