package com.violinsheet.core.ink

import android.graphics.BlendMode
import android.graphics.Matrix
import android.graphics.Paint
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.brush.SelfOverlap
import androidx.ink.brush.StockBrushes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import androidx.compose.ui.graphics.drawscope.Stroke as Outline

/** Smallest distance the brush tells apart, in Page points. */
private const val EPSILON = 0.05f

/** Far longer than the strokes list takes to show a handed-over stroke (a frame), short enough for an undone one. */
private const val UNDONE_WET_STROKE_MILLIS = 300L

// Overlaps within one highlighter stroke don't darken it, as with a real highlighter.
private val highlighterFamily = StockBrushes.highlighter(SelfOverlap.DISCARD)

private fun brush(tool: StrokeTool, colour: Int, size: Float) = Brush.createWithColorIntArgb(
    when (tool) {
        StrokeTool.Pen -> StockBrushes.pressurePen()
        StrokeTool.Highlighter -> highlighterFamily
    },
    colour,
    size,
    EPSILON,
)

/**
 * Highlighter strokes are drawn into a layer that is multiplied onto the Page: white paper takes the colour, black
 * notes stay black and sharp. A plain translucent stroke would wash over the notes instead.
 */
private val multiply = Paint().apply { blendMode = BlendMode.MULTIPLY }

/**
 * Whether the S Pen side button is held. Android reports it as the stylus primary button; some devices report a stylus
 * button as the secondary (mouse) button instead, which Compose's isSecondaryPressed also allows for.
 */
private fun PointerEvent.eraseHeld(): Boolean {
    val buttons = motionEvent?.buttonState ?: return false
    return buttons and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_SECONDARY) != 0
}

/** A wet stroke's Page and tool, which the Ink API stroke doesn't carry. */
private class WetStroke(val pageIndex: Int, val tool: StrokeTool)

private fun PagePlacement.pageToScreen() = Matrix().apply {
    setScale(scale, scale)
    postTranslate(offset.x, offset.y)
}

/** Rebuilds an Ink API stroke from the shared format, for drawing. */
private fun InkStroke.toInkApi(): Stroke {
    val inputs = MutableStrokeInputBatch()
    for (p in points) inputs.add(InputToolType.STYLUS, p.x, p.y, p.elapsedMillis, pressure = p.pressure)
    return Stroke(brush(tool, colour, size), inputs)
}

/** Reads an Ink API stroke (in Page coordinates) into the shared format. */
private fun Stroke.toInkStroke(wet: WetStroke): InkStroke {
    val input = StrokeInput()
    val points = List(inputs.size) { i ->
        inputs.populate(i, input)
        InkPoint(input.x, input.y, input.pressure, input.elapsedTimeMillis)
    }
    // Strokes finish on the main thread only, as strokeIds needs.
    return InkStroke(strokeIds.next(), wet.pageIndex, brush.colorIntArgb, brush.size, points, wet.tool)
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
    tool: InkTool,
    eraser: Eraser,
    onStroke: (InkStroke) -> Unit,
    erase: EraseInput,
    modifier: Modifier,
) {
    val renderer = remember { CanvasStrokeRenderer.create() }
    // Ink API strokes by id, built on first draw: building a stroke's shape costs a little, so it happens once per
    // stroke and only for Pages on screen, not for the whole Sheet on every change. A piece an erase cut keeps its
    // stroke's id but not its points, so an entry is only good for the very stroke it was built from.
    val built = remember { HashMap<Long, Pair<InkStroke, Stroke>>() }
    val ids = remember(strokes) { strokes.mapTo(HashSet()) { it.id }.also { built.keys.retainAll(it) } }
    val currentIds by rememberUpdatedState(ids)
    val currentPlacements by rememberUpdatedState(placements)
    val currentTool by rememberUpdatedState(tool)
    val currentEraser by rememberUpdatedState(eraser)
    val currentOnStroke by rememberUpdatedState(onStroke)
    val currentErase by rememberUpdatedState(erase)
    var view by remember { mutableStateOf<InProgressStrokesView?>(null) }
    // Strokes handed to onStroke but still shown by the low-latency view, with their ids. Removed from the view only
    // once they are in [strokes] and drawn here, so a finished stroke never blinks out.
    val handedOver = remember { mutableMapOf<InProgressStrokeId, Long>() }
    val wetStrokes = remember { mutableMapOf<InProgressStrokeId, WetStroke>() }
    // The eraser circle on screen (centre, radius) while erasing.
    var eraserCircle by remember { mutableStateOf<Pair<Offset, Float>?>(null) }

    val hostView = LocalView.current

    // Pen input goes through Compose rather than a View touch listener: Compose sees every pointer on its own, so the
    // pen draws even inside a gesture a resting palm started (navigation's palm guard deals with the palm itself).
    val penInput = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            var stroke: InProgressStrokeId? = null
            var erasing: Eraser? = null
            var penPointer: PointerId? = null
            var placement: PagePlacement? = null
            var startMillis = 0L
            fun input(change: PointerInputChange, position: Offset, atMillis: Long): StrokeInput {
                val page = placement!!.toPage(position)
                return StrokeInput.create(page.x, page.y, atMillis - startMillis, InputToolType.STYLUS, pressure = change.pressure)
            }
            // The eraser erases on whichever Page it is over; between Pages it erases nothing.
            fun eraseAt(position: Offset) {
                val page = currentPlacements.firstOrNull { it.screenRect.contains(position) }
                eraserCircle = page?.let { position to erasing!!.size / 2 * it.scale }
                if (page != null) currentErase.eraseAt(page.pageIndex, page.toPage(position)) else currentErase.eraserOffPage()
            }
            fun endErase(cancelled: Boolean) {
                erasing = null
                eraserCircle = null
                currentErase.endErase(cancelled)
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
                                // A gesture still open means its lift was lost: drop a stroke rather than block the
                                // pen; keep what an erase did.
                                current?.let {
                                    strokesView.cancelStroke(it)
                                    wetStrokes.remove(it)
                                    stroke = null
                                }
                                if (erasing != null) endErase(cancelled = false)
                                // Decided once, at pen down: pressing or releasing the side button mid-gesture changes
                                // nothing until the pen lifts.
                                val chosen = toolForGesture(currentTool, currentEraser, event.eraseHeld())
                                if (chosen is Eraser) {
                                    erasing = chosen
                                    penPointer = change.id
                                    event.motionEvent?.let(hostView::requestUnbufferedDispatch)
                                    currentErase.beginErase(chosen)
                                    eraseAt(change.position)
                                    change.consume()
                                    continue
                                }
                                val draw = chosen as DrawTool
                                // The stroke belongs to the Page the pen starts on, in that Page's coordinates.
                                val page = currentPlacements.firstOrNull { it.screenRect.contains(change.position) } ?: continue
                                placement = page
                                startMillis = change.uptimeMillis
                                penPointer = change.id
                                // This stroke's events as they come, not batched per frame (Ink API advice).
                                event.motionEvent?.let(hostView::requestUnbufferedDispatch)
                                stroke = strokesView.startStroke(
                                    input(change, change.position, change.uptimeMillis),
                                    brush(draw.tool, draw.colour, draw.size),
                                    page.pageToScreen(),
                                ).also { wetStrokes[it] = WetStroke(page.pageIndex, draw.tool) }
                                change.consume()
                            }
                            erasing != null && change.id == penPointer && change.pressed -> {
                                for (h in change.historical) eraseAt(h.position)
                                eraseAt(change.position)
                                change.consume()
                            }
                            erasing != null && change.id == penPointer -> {
                                // Palm rejection or a system gesture took the pen: take the erasing back too.
                                endErase(cancelled = event.isCancel())
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
                                    wetStrokes.remove(current)
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
                // Leaving the screen mid-stroke: drop the unfinished stroke rather than keep half of it. Mid-erase:
                // keep what was erased, as one undo step.
                stroke?.let {
                    view?.cancelStroke(it)
                    wetStrokes.remove(it)
                }
                if (erasing != null) endErase(cancelled = false)
            }
        }
    }

    Box(modifier.then(penInput)) {
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                for (placement in placements) {
                    // The renderer only uses the transform as a hint for detail; the canvas has to be moved itself.
                    val toScreen = placement.pageToScreen()
                    native.save()
                    native.concat(toScreen)
                    var multiplying = false
                    for (stroke in strokes) {
                        if (stroke.pageIndex != placement.pageIndex) continue
                        // Consecutive highlighter strokes share one multiplied layer, clipped to the Page.
                        val highlighter = stroke.tool == StrokeTool.Highlighter
                        if (highlighter != multiplying) {
                            if (highlighter) native.saveLayer(0f, 0f, placement.pageSize.width, placement.pageSize.height, multiply)
                            else native.restore()
                            multiplying = highlighter
                        }
                        val inkApi = built[stroke.id]?.takeIf { it.first === stroke }?.second
                            ?: stroke.toInkApi().also { built[stroke.id] = stroke to it }
                        renderer.draw(native, inkApi, toScreen)
                    }
                    if (multiplying) native.restore()
                    native.restore()
                }
            }
            val drawnNow = handedOver.filterValues { it in ids }.keys
            if (drawnNow.isNotEmpty()) {
                handedOver.keys.removeAll(drawnNow)
                view?.post { view?.removeFinishedStrokes(drawnNow) }
            }
        }
        // Its own Canvas, so moving the circle doesn't redraw the strokes.
        Canvas(Modifier.fillMaxSize()) {
            eraserCircle?.let { (center, radius) ->
                drawCircle(Color.White, radius, center, style = Outline(width = 3f))
                drawCircle(Color.DarkGray, radius, center, style = Outline(width = 1.5f))
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
                                    val wet = wetStrokes.remove(id)
                                    if (wet == null) {
                                        removeFinishedStrokes(setOf(id)) // not ours to keep; don't leave it on screen
                                        continue
                                    }
                                    val inkStroke = stroke.toInkStroke(wet)
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
