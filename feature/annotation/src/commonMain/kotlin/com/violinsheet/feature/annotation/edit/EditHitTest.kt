package com.violinsheet.feature.annotation.edit

import androidx.compose.ui.geometry.Offset
import com.violinsheet.core.ink.FrameHit
import com.violinsheet.core.ink.PagePlacement
import com.violinsheet.core.ink.SelectionFrame
import com.violinsheet.core.ink.hitTest

/** Whether the pen at [now] has gone further than [slopPx] from [down] (screen px): a drag, not a tap. */
internal fun beyondSlop(down: Offset, now: Offset, slopPx: Float): Boolean = (now - down).getDistanceSquared() > slopPx * slopPx

/**
 * The tool a pen gesture uses, decided once at pen down: the Eraser while the side button is held ([eraseHeld]), with
 * any tool, else the [chosen] one.
 */
internal fun editToolForGesture(chosen: EditTool, eraseHeld: Boolean): EditTool = if (eraseHeld) EditTool.Eraser else chosen

/** A screen distance [px] as Page points on [placement]. */
internal fun PagePlacement.pagePoints(px: Float): Float = px / scale

/**
 * What [screenPoint] hits on a Symbol's [frame] (no rotate handle). A fingering's frame is shown so small that its
 * corners' hit circles overlap and cover its middle, so within them the nearest corner counts, and a pen nearer the
 * frame's centre than to any corner moves the Symbol rather than resizing it. With [resizable] false (several
 * Symbols) the corners are no handles: a corner is [FrameHit.Inside], so it moves them.
 */
internal fun symbolFrameHit(
    frame: SelectionFrame,
    placement: PagePlacement,
    screenPoint: Offset,
    handleRadiusPx: Float,
    insideMarginPx: Float,
    minSidePx: Float,
    resizable: Boolean = true,
): FrameHit {
    val hit = hitTest(frame, placement, screenPoint, handleRadiusPx, 0f, insideMarginPx, minSidePx, rotatable = false)
    if (hit !is FrameHit.Corner) return hit
    if (!resizable) return FrameHit.Inside
    val distances = frame.shownAt(placement, minSidePx).corners().map { (placement.toScreen(it) - screenPoint).getDistanceSquared() }
    val nearest = distances.indices.minBy { distances[it] }
    val toCentre = (placement.toScreen(frame.center) - screenPoint).getDistanceSquared()
    return if (toCentre < distances[nearest]) FrameHit.Inside else FrameHit.Corner(nearest)
}
