package com.violinsheet.core.ink

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.brush.SelfOverlap
import androidx.ink.brush.StockBrushes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke

/** Smallest distance the brush tells apart, in Page points. */
private const val EPSILON = 0.05f

// Overlaps within one highlighter stroke don't darken it, as with a real highlighter.
private val highlighterFamily = StockBrushes.highlighter(SelfOverlap.DISCARD)

internal fun brush(tool: StrokeTool, colour: Int, size: Float) = Brush.createWithColorIntArgb(
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

/** Sets this matrix to [transform] on Page coordinates, for drawing the selected strokes where a drag takes them. */
private fun Matrix.setTo(transform: StrokeTransform) = transform.run {
    setTranslate(-pivot.x, -pivot.y)
    postScale(scale, scale)
    postRotate(rotationDegrees) // clockwise on screen, as StrokeTransform turns
    postTranslate(pivot.x + translation.x, pivot.y + translation.y)
}

internal fun PagePlacement.pageToScreen() = Matrix().apply {
    setScale(scale, scale)
    postTranslate(offset.x, offset.y)
}

/** Rebuilds an Ink API stroke from the shared format, for drawing. */
private fun InkStroke.toInkApi(): Stroke {
    val inputs = MutableStrokeInputBatch()
    for (p in points) inputs.add(InputToolType.STYLUS, p.x, p.y, p.elapsedMillis, pressure = p.pressure)
    return Stroke(brush(tool, colour, size), inputs)
}

/** Draws finished strokes on their Pages, with the selection moved by the drag in progress. */
internal class StrokePainter {
    private val renderer = CanvasStrokeRenderer.create()

    // Ink API strokes by id, built on first draw: building a stroke's shape costs a little, so it happens once per
    // stroke and only for Pages on screen, not for the whole Sheet on every change. A piece an erase cut keeps its
    // stroke's id but not its points, so an entry is only good for the very stroke it was built from.
    private val built = HashMap<Long, Pair<InkStroke, Stroke>>()

    // Reused by every frame of a drag, rather than allocated per draw.
    private val previewMatrix = Matrix()
    private val previewToScreen = Matrix()

    /** Forgets the built strokes whose id isn't in [ids]. */
    fun keepOnly(ids: Set<Long>) {
        built.keys.retainAll(ids)
    }

    fun draw(native: Canvas, placements: List<PagePlacement>, strokes: List<InkStroke>, selection: Selection?, selectedIds: Set<Long>) {
        // The selected strokes are drawn in their own places in the list, moved by the drag in progress: the strokes
        // already built are reused, so nothing is rebuilt per frame and the drawing order stays.
        val preview = selection?.preview
        if (preview != null) previewMatrix.setTo(preview)
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
}
