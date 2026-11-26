package com.violinsheet.core.ink

import android.graphics.Matrix
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput

/** Smallest distance the brush tells apart, in Page points. */
private const val EPSILON = 0.05f

/** Far longer than the strokes list takes to show a handed-over stroke (a frame), short enough for an undone one. */
private const val UNDONE_WET_STROKE_MILLIS = 300L

// Strokes finish on the main thread only, as StrokeIds needs.
private val strokeIds = StrokeIds(System::currentTimeMillis)

private fun brush(colour: Int, size: Float) = Brush.createWithColorIntArgb(StockBrushes.pressurePen(), colour, size, EPSILON)

private fun PagePlacement.pageToScreen() = Matrix().apply {
    setScale(scale, scale)
    postTranslate(offset.x, offset.y)
}

/** Rebuilds an Ink API stroke from the shared format, for drawing. */
private fun InkStroke.toInkApi(): Stroke {
    val inputs = MutableStrokeInputBatch()
    for (p in points) inputs.add(InputToolType.STYLUS, p.x, p.y, p.elapsedMillis, pressure = p.pressure)
    return Stroke(brush(colour, size), inputs)
}

/** Reads an Ink API stroke (in Page coordinates) into the shared format. */
private fun Stroke.toInkStroke(pageIndex: Int): InkStroke {
    val input = StrokeInput()
    val points = List(inputs.size) { i ->
        inputs.populate(i, input)
        InkPoint(input.x, input.y, input.pressure, input.elapsedTimeMillis)
    }
    return InkStroke(strokeIds.next(), pageIndex, brush.colorIntArgb, brush.size, points)
}

/**
 * Compose turns a cancelled gesture into an ordinary lift. The MotionEvent behind it tells them apart: a cancel, a
 * pointer the system flagged as cancelled (palm rejection), or no event at all (Compose's own synthetic cancel).
 */
private fun PointerEvent.isCancel(): Boolean {
    val event = motionEvent ?: return true
    return event.actionMasked == MotionEvent.ACTION_CANCEL || event.flags and MotionEvent.FLAG_CANCELED != 0
}

@Composable
actual fun InkLayer(
    placements: List<PagePlacement>,
    strokes: List<InkStroke>,
    pen: Pen,
    onStroke: (InkStroke) -> Unit,
    modifier: Modifier,
) {
    val renderer = remember { CanvasStrokeRenderer.create() }
    // Ink API strokes by id, built on first draw: building a stroke's shape costs a little, so it happens once per
    // stroke and only for Pages on screen, not for the whole Sheet on every change.
    val built = remember { HashMap<Long, Stroke>() }
    val ids = remember(strokes) { strokes.mapTo(HashSet()) { it.id }.also { built.keys.retainAll(it) } }
    val currentIds by rememberUpdatedState(ids)
    val currentPlacements by rememberUpdatedState(placements)
    val currentPen by rememberUpdatedState(pen)
    val currentOnStroke by rememberUpdatedState(onStroke)
    var view by remember { mutableStateOf<InProgressStrokesView?>(null) }
    // Strokes handed to onStroke but still shown by the low-latency view, with their ids. Removed from the view only
    // once they are in [strokes] and drawn here, so a finished stroke never blinks out.
    val handedOver = remember { mutableMapOf<InProgressStrokeId, Long>() }
    // The Page each wet stroke belongs to.
    val pageOf = remember { mutableMapOf<InProgressStrokeId, Int>() }

    val hostView = LocalView.current

    // Pen input goes through Compose rather than a View touch listener: Compose sees every pointer on its own, so the
    // pen draws even inside a gesture a resting palm started (navigation's palm guard deals with the palm itself).
    val penInput = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            var stroke: InProgressStrokeId? = null
            var penPointer: PointerId? = null
            var placement: PagePlacement? = null
            var startMillis = 0L
            fun input(change: PointerInputChange, position: Offset, atMillis: Long): StrokeInput {
                val page = placement!!.toPage(position)
                return StrokeInput.create(page.x, page.y, atMillis - startMillis, InputToolType.STYLUS, pressure = change.pressure)
            }
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val strokesView = view ?: continue
                    for (change in event.changes) {
                        if (change.type != PointerType.Stylus) continue
                        val current = stroke
                        when {
                            change.pressed && !change.previousPressed -> {
                                // A stroke still open means its lift was lost: drop it rather than block the pen.
                                current?.let {
                                    strokesView.cancelStroke(it)
                                    pageOf.remove(it)
                                    stroke = null
                                }
                                // shortcut: the side button just doesn't draw for now; #8 makes holding it erase.
                                if (event.buttons.isSecondaryPressed) continue
                                // The stroke belongs to the Page the pen starts on, in that Page's coordinates.
                                val page = currentPlacements.firstOrNull { it.screenRect.contains(change.position) } ?: continue
                                placement = page
                                startMillis = change.uptimeMillis
                                penPointer = change.id
                                // This stroke's events as they come, not batched per frame (Ink API advice).
                                event.motionEvent?.let(hostView::requestUnbufferedDispatch)
                                val chosen = currentPen
                                stroke = strokesView.startStroke(
                                    input(change, change.position, change.uptimeMillis),
                                    brush(chosen.colour, chosen.size),
                                    page.pageToScreen(),
                                ).also { pageOf[it] = page.pageIndex }
                                change.consume()
                            }
                            current != null && change.id == penPointer && change.pressed -> {
                                val batch = MutableStrokeInputBatch()
                                for (h in change.historical) batch.add(input(change, h.position, h.uptimeMillis))
                                batch.add(input(change, change.position, change.uptimeMillis))
                                strokesView.addToStroke(batch, current)
                                change.consume()
                            }
                            current != null && change.id == penPointer -> {
                                if (event.isCancel()) {
                                    // Palm rejection or a system gesture took the pen: drop the stroke, no stray mark.
                                    strokesView.cancelStroke(current)
                                    pageOf.remove(current)
                                } else {
                                    strokesView.finishStroke(input(change, change.position, change.uptimeMillis), current)
                                }
                                stroke = null
                                change.consume()
                            }
                        }
                    }
                }
            } finally {
                // Leaving the screen mid-stroke: drop the unfinished stroke rather than keep half of it.
                stroke?.let {
                    view?.cancelStroke(it)
                    pageOf.remove(it)
                }
            }
        }
    }

    Box(modifier.then(penInput)) {
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas { canvas ->
                for (placement in placements) {
                    // The renderer only uses the transform as a hint for detail; the canvas has to be moved itself.
                    val toScreen = placement.pageToScreen()
                    canvas.nativeCanvas.save()
                    canvas.nativeCanvas.concat(toScreen)
                    for (stroke in strokes) {
                        if (stroke.pageIndex != placement.pageIndex) continue
                        renderer.draw(canvas.nativeCanvas, built.getOrPut(stroke.id) { stroke.toInkApi() }, toScreen)
                    }
                    canvas.nativeCanvas.restore()
                }
            }
            val drawnNow = handedOver.filterValues { it in ids }.keys
            if (drawnNow.isNotEmpty()) {
                handedOver.keys.removeAll(drawnNow)
                view?.post { view?.removeFinishedStrokes(drawnNow) }
            }
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
                                    val page = pageOf.remove(id)
                                    if (page == null) {
                                        removeFinishedStrokes(setOf(id)) // not ours to keep; don't leave it on screen
                                        continue
                                    }
                                    val inkStroke = stroke.toInkStroke(page)
                                    handedOver[id] = inkStroke.id
                                    currentOnStroke(inkStroke)
                                    // Undone before [strokes] ever showed it (undo tapped within a frame of the lift),
                                    // the draw below never sees it to take it out: drop it here once [strokes] has
                                    // surely caught up.
                                    postDelayed({
                                        if (handedOver[id]?.let { it !in currentIds } == true) {
                                            handedOver.remove(id)
                                            removeFinishedStrokes(setOf(id))
                                        }
                                    }, UNDONE_WET_STROKE_MILLIS)
                                }
                            }
                        },
                    )
                    view = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
