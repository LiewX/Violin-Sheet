package com.violinsheet.core.ink

import android.annotation.SuppressLint
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
import kotlin.random.Random

/** Smallest distance the brush tells apart, in Page points. */
private const val EPSILON = 0.05f

private fun brush(colour: Int, size: Float) = Brush.createWithColorIntArgb(StockBrushes.pressurePen(), colour, size, EPSILON)

private fun PagePlacement.pageToScreen() = Matrix().apply {
    setScale(scale, scale)
    postTranslate(offset.x, offset.y)
}

private fun PagePlacement.screenToPage() = Matrix().also { pageToScreen().invert(it) }

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
    return InkStroke(Random.nextLong(), pageIndex, brush.colorIntArgb, brush.size, points)
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
    val currentPlacements by rememberUpdatedState(placements)
    val currentPen by rememberUpdatedState(pen)
    val currentOnStroke by rememberUpdatedState(onStroke)
    var view by remember { mutableStateOf<InProgressStrokesView?>(null) }
    // Strokes handed to onStroke but still shown by the low-latency view, with their ids. Removed from the view only
    // once they are in [strokes] and drawn here, so a finished stroke never blinks out.
    val handedOver = remember { mutableMapOf<InProgressStrokeId, Long>() }

    Box(modifier) {
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
        AndroidView(
            factory = { context ->
                InProgressStrokesView(context).apply {
                    eagerInit()
                    val pageOf = mutableMapOf<InProgressStrokeId, Int>()
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
                                }
                            }
                        },
                    )
                    setOnTouchListener(PenInput(this, { currentPlacements }, { currentPen }, pageOf))
                    view = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Feeds pen input to the low-latency view. Fingers are refused, so they reach page navigation underneath. */
private class PenInput(
    private val view: InProgressStrokesView,
    private val placements: () -> List<PagePlacement>,
    private val pen: () -> Pen,
    private val pageOf: MutableMap<InProgressStrokeId, Int>,
) : android.view.View.OnTouchListener {
    private var stroke: InProgressStrokeId? = null
    private var pointerId = MotionEvent.INVALID_POINTER_ID

    @SuppressLint("ClickableViewAccessibility") // ink input, not a click
    override fun onTouch(v: android.view.View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) return false
                // shortcut: the side button just doesn't draw for now; #8 makes holding it erase.
                if (event.isButtonPressed(MotionEvent.BUTTON_STYLUS_PRIMARY)) return true
                stroke?.let { view.cancelStroke(it, event) } // a stroke left open by a lost UP

                // The stroke belongs to the Page the pen starts on, in that Page's coordinates.
                val placement = placements().firstOrNull { it.screenRect.contains(Offset(event.x, event.y)) } ?: return false
                v.requestUnbufferedDispatch(event)
                pointerId = event.getPointerId(0)
                val current = pen()
                stroke = view.startStroke(event, pointerId, brush(current.colour, current.size), placement.screenToPage(), Matrix())
                    .also { pageOf[it] = placement.pageIndex }
            }
            MotionEvent.ACTION_MOVE -> stroke?.let { view.addToStroke(event, pointerId, it, null) }
            MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == pointerId) finish(event)
            MotionEvent.ACTION_UP -> finish(event)
            // Palm rejection or the system took the gesture: drop the stroke.
            MotionEvent.ACTION_CANCEL -> stroke?.let {
                view.cancelStroke(it, event)
                pageOf.remove(it)
                stroke = null
            }
        }
        return true
    }

    private fun finish(event: MotionEvent) {
        stroke?.let { view.finishStroke(event, pointerId, it) }
        stroke = null
    }
}
