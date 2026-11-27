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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
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

// The selection's handles are sized on screen, whatever the zoom.
private val HANDLE_DRAWN_RADIUS = 6.dp
private val HANDLE_HIT_RADIUS = 24.dp
private val ROTATE_HANDLE_DISTANCE = 32.dp
private val INSIDE_MARGIN = 8.dp

// The frame is shown at least this long on each side, so a straight line's corners stay apart.
private val FRAME_MIN_SIDE = 24.dp

// Lasso points closer than this on screen to the last one add nothing but work for lassoSelects.
private val LASSO_STEP = 2.dp

// A Lasso pen-down that stays within this of where it started is a tap; it picks a stroke whose ink is this near.
private val TAP_SLOP = 8.dp
private val TAP_REACH = 4.dp

/** Sets this matrix to [transform] on Page coordinates, for drawing the selected strokes where a drag takes them. */
private fun Matrix.setTo(transform: StrokeTransform) = transform.run {
    setTranslate(-pivot.x, -pivot.y)
    postScale(scale, scale)
    postRotate(rotationDegrees) // clockwise on screen, as StrokeTransform turns
    postTranslate(pivot.x + translation.x, pivot.y + translation.y)
}

/** [screen] in this Page's coordinates, kept within the Page. */
private fun PagePlacement.toPageWithin(screen: Offset): Offset {
    val page = toPage(screen)
    return Offset(page.x.coerceIn(0f, pageSize.width), page.y.coerceIn(0f, pageSize.height))
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
    selection: Selection?,
    lasso: LassoInput,
    selectionColour: Color,
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
    val currentLasso by rememberUpdatedState(lasso)
    val currentSelection by rememberUpdatedState(selection)
    val selectedIds = remember(selection?.ids) { selection?.ids?.toHashSet().orEmpty() }
    // Reused by every frame of a drag or a lasso, rather than allocated per draw.
    val previewMatrix = remember { Matrix() }
    val previewToScreen = remember { Matrix() }
    val lassoPath = remember { Path() }
    val framePath = remember { Path() }
    val density = LocalDensity.current
    val lassoDash = remember(density) { with(density) { PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())) } }
    var view by remember { mutableStateOf<InProgressStrokesView?>(null) }
    // Strokes handed to onStroke but still shown by the low-latency view, with their ids. Removed from the view only
    // once they are in [strokes] and drawn here, so a finished stroke never blinks out.
    val handedOver = remember { mutableMapOf<InProgressStrokeId, Long>() }
    val wetStrokes = remember { mutableMapOf<InProgressStrokeId, WetStroke>() }
    // The eraser circle on screen (centre, radius) while erasing.
    var eraserCircle by remember { mutableStateOf<Pair<Offset, Float>?>(null) }
    // The lasso being drawn: its Page and its points in that Page's coordinates, so a zoom mid-lasso keeps it in place.
    var lassoPage by remember { mutableStateOf<Int?>(null) }
    val lassoPoints = remember { mutableStateListOf<Offset>() }

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
            // The Page a drag on the selection runs on. Each event goes through that Page's current placement, so a
            // finger zoom mid-drag keeps the drag under the pen.
            var dragPage: Int? = null
            fun placementOf(pageIndex: Int) = currentPlacements.firstOrNull { it.pageIndex == pageIndex }
            fun endDrag(cancelled: Boolean) {
                dragPage = null
                currentLasso.endDrag(cancelled)
            }
            val lassoStepSquared = LASSO_STEP.toPx().let { it * it }
            val tapSlopSquared = TAP_SLOP.toPx().let { it * it }
            // Where the lasso's pen went down (screen), and whether it has stayed near there: a tap, not a lasso.
            var lassoStart = Offset.Zero
            var lassoIsTap = false
            fun lassoTo(position: Offset) {
                val page = lassoPage?.let(::placementOf) ?: return
                if ((position - lassoStart).getDistanceSquared() > tapSlopSquared) lassoIsTap = false
                val last = lassoPoints.lastOrNull()
                if (last != null && (page.toScreen(last) - position).getDistanceSquared() < lassoStepSquared) return
                lassoPoints.add(page.toPageWithin(position))
            }
            fun endLasso(cancelled: Boolean) {
                val pageIndex = lassoPage ?: return
                val points = lassoPoints.toList()
                lassoPage = null
                lassoPoints.clear()
                if (cancelled) return
                val page = placementOf(pageIndex)
                // A pen that barely moved drew no lasso: it picks the stroke under it, or clears the selection.
                if (lassoIsTap && page != null) currentLasso.tapAt(pageIndex, points.first(), TAP_REACH.toPx() / page.scale)
                else currentLasso.lassoDone(pageIndex, points)
            }
            // Pen down with the Lasso tool: a drag if it hits the selection's frame or handles, else a new lasso on the
            // Page under the pen (none between Pages).
            fun beginLasso(position: Offset) {
                val selected = currentSelection
                val onPage = selected?.let { placementOf(it.pageIndex) }
                val hit = if (selected != null && onPage != null) {
                    hitTest(
                        selected.frame, onPage, position,
                        HANDLE_HIT_RADIUS.toPx(), ROTATE_HANDLE_DISTANCE.toPx(), INSIDE_MARGIN.toPx(), FRAME_MIN_SIDE.toPx(),
                    )
                } else {
                    FrameHit.Outside
                }
                val kind = when (hit) {
                    FrameHit.RotateHandle -> DragKind.Rotate
                    is FrameHit.Corner -> DragKind.Resize(hit.index)
                    FrameHit.Inside -> DragKind.Move
                    FrameHit.Outside -> null
                }
                if (kind != null && onPage != null) {
                    dragPage = onPage.pageIndex
                    currentLasso.beginDrag(kind, onPage.toPage(position), onPage.pageSize)
                    return
                }
                currentLasso.clearSelection()
                val page = currentPlacements.firstOrNull { it.screenRect.contains(position) } ?: return
                lassoPage = page.pageIndex
                lassoStart = position
                lassoIsTap = true
                lassoPoints.add(page.toPageWithin(position))
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
                                // A drag is kept as done so far; a half-drawn lasso is dropped, like a stroke.
                                if (dragPage != null) endDrag(cancelled = false)
                                endLasso(cancelled = true)
                                // Decided once, at pen down: pressing or releasing the side button mid-gesture changes
                                // nothing until the pen lifts.
                                val chosen = toolForGesture(currentTool, currentEraser, event.eraseHeld())
                                if (chosen is Lasso) {
                                    penPointer = change.id
                                    event.motionEvent?.let(hostView::requestUnbufferedDispatch)
                                    beginLasso(change.position)
                                    change.consume()
                                    continue
                                }
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
                            dragPage != null && change.id == penPointer && change.pressed -> {
                                // Only where the pen is now matters, so the historical positions are skipped.
                                placementOf(dragPage!!)?.let { currentLasso.dragTo(it.toPage(change.position)) }
                                change.consume()
                            }
                            dragPage != null && change.id == penPointer -> {
                                // Palm rejection or a system gesture took the pen: the selection stays where it was.
                                endDrag(cancelled = event.isCancel())
                                change.consume()
                            }
                            lassoPage != null && change.id == penPointer && change.pressed -> {
                                for (h in change.historical) lassoTo(h.position)
                                lassoTo(change.position)
                                change.consume()
                            }
                            lassoPage != null && change.id == penPointer -> {
                                endLasso(cancelled = event.isCancel())
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
                if (dragPage != null) endDrag(cancelled = false)
                endLasso(cancelled = true)
            }
        }
    }

    Box(modifier.then(penInput)) {
        Canvas(Modifier.fillMaxSize()) {
            // The selected strokes are drawn in their own places in the list, moved by the drag in progress: the
            // strokes already built are reused, so nothing is rebuilt per frame and the drawing order stays.
            val preview = selection?.preview
            if (preview != null) previewMatrix.setTo(preview)
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                for (placement in placements) {
                    // The renderer only uses the transform as a hint for detail; the canvas has to be moved itself.
                    val toScreen = placement.pageToScreen()
                    val previewing = preview != null && placement.pageIndex == selection?.pageIndex
                    if (previewing) previewToScreen.setConcat(toScreen, previewMatrix)
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
                        if (previewing && stroke.id in selectedIds) {
                            native.save()
                            native.concat(previewMatrix)
                            renderer.draw(native, inkApi, previewToScreen)
                            native.restore()
                        } else {
                            renderer.draw(native, inkApi, toScreen)
                        }
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
        // The lasso being drawn: a dashed outline, never a stroke, so it never reaches onStroke.
        Canvas(Modifier.fillMaxSize()) {
            val page = lassoPage?.let { index -> placements.firstOrNull { it.pageIndex == index } } ?: return@Canvas
            if (lassoPoints.size < 2) return@Canvas
            lassoPath.reset()
            lassoPoints.forEachIndexed { i, p ->
                val s = page.toScreen(p)
                if (i == 0) lassoPath.moveTo(s.x, s.y) else lassoPath.lineTo(s.x, s.y)
            }
            drawPath(lassoPath, Color.White, style = Outline(width = 3.dp.toPx()))
            drawPath(lassoPath, Color.DarkGray, style = Outline(width = 1.5.dp.toPx(), pathEffect = lassoDash))
        }
        // The selection's frame and handles, where the drag in progress puts them, padded as hitTest pads them.
        Canvas(Modifier.fillMaxSize()) {
            val selected = selection ?: return@Canvas
            val page = placements.firstOrNull { it.pageIndex == selected.pageIndex } ?: return@Canvas
            val frame = (selected.preview?.let(selected.frame::transformed) ?: selected.frame).shownAt(page, FRAME_MIN_SIDE.toPx())
            val corners = frame.corners().map(page::toScreen)
            framePath.reset()
            framePath.moveTo(corners[0].x, corners[0].y)
            for (i in 1..3) framePath.lineTo(corners[i].x, corners[i].y)
            framePath.close()
            drawPath(framePath, selectionColour, style = Outline(width = 1.dp.toPx()))
            val handle = rotateHandle(frame, page, ROTATE_HANDLE_DISTANCE.toPx())
            val topMiddle = Offset((corners[0].x + corners[1].x) / 2, (corners[0].y + corners[1].y) / 2)
            drawLine(selectionColour, topMiddle, handle, strokeWidth = 1.dp.toPx())
            for (c in corners) drawCircle(selectionColour, HANDLE_DRAWN_RADIUS.toPx(), c)
            drawCircle(selectionColour, HANDLE_DRAWN_RADIUS.toPx(), handle)
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
