package com.violinsheet.core.ink

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.Stroke as Outline

// The selection's handles are sized on screen, whatever the zoom.
private val HANDLE_DRAWN_RADIUS = 6.dp
val HANDLE_HIT_RADIUS = 24.dp
val ROTATE_HANDLE_DISTANCE = 32.dp
val INSIDE_MARGIN = 8.dp

// The frame is shown at least this long on each side, so a straight line's corners stay apart.
val FRAME_MIN_SIDE = 24.dp

/** The lasso being drawn, [points] in Page coordinates on [page]: a dashed outline, never a stroke. */
fun DrawScope.drawLasso(page: PagePlacement, points: List<Offset>, path: Path, dash: PathEffect) {
    if (points.size < 2) return
    path.reset()
    points.forEachIndexed { i, p ->
        val s = page.toScreen(p)
        if (i == 0) path.moveTo(s.x, s.y) else path.lineTo(s.x, s.y)
    }
    drawPath(path, Color.White, style = Outline(width = 3.dp.toPx()))
    drawPath(path, Color.DarkGray, style = Outline(width = 1.5.dp.toPx(), pathEffect = dash))
}

/** The selection's frame and handles on [page], where the drag in progress puts them, padded as hitTest pads them. */
fun DrawScope.drawSelectionFrame(selected: Selection, page: PagePlacement, colour: Color, path: Path) =
    drawSelectionFrame(selected.frame, selected.preview, page, colour, path)

/**
 * [frame] with [preview] applied, on [page], padded as hitTest pads it. With [rotatable] false (a Symbol) there is no
 * rotate handle; with [handles] false (several Symbols, which only move) no corner handles either.
 */
fun DrawScope.drawSelectionFrame(
    frame: SelectionFrame,
    preview: StrokeTransform?,
    page: PagePlacement,
    colour: Color,
    path: Path,
    rotatable: Boolean = true,
    handles: Boolean = true,
) {
    val shown = (preview?.let(frame::transformed) ?: frame).shownAt(page, FRAME_MIN_SIDE.toPx())
    drawFrame(shown, page, colour, path, rotatable && handles, handles)
}

private fun DrawScope.drawFrame(frame: SelectionFrame, page: PagePlacement, colour: Color, path: Path, rotatable: Boolean, handles: Boolean) {
    val corners = frame.corners().map(page::toScreen)
    path.reset()
    path.moveTo(corners[0].x, corners[0].y)
    for (i in 1..3) path.lineTo(corners[i].x, corners[i].y)
    path.close()
    drawPath(path, colour, style = Outline(width = 1.dp.toPx()))
    if (handles) for (c in corners) drawCircle(colour, HANDLE_DRAWN_RADIUS.toPx(), c)
    if (!rotatable) return
    val handle = rotateHandle(frame, page, ROTATE_HANDLE_DISTANCE.toPx())
    val topMiddle = Offset((corners[0].x + corners[1].x) / 2, (corners[0].y + corners[1].y) / 2)
    drawLine(colour, topMiddle, handle, strokeWidth = 1.dp.toPx())
    drawCircle(colour, HANDLE_DRAWN_RADIUS.toPx(), handle)
}
