package com.violinsheet.feature.viewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import com.violinsheet.core.ink.PagePlacement
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

// How Pages sit on screen. Fit layout: each shown Page gets a rect in the viewport. Zoom: one scale and offset
// over the whole spread (screen = fit * scale + offset). Ink (#6) maps Page coordinates through the same two steps,
// so it stays aligned at every zoom and layout.

/** Most a Page can be zoomed in, relative to fitting the screen. */
private const val MAX_ZOOM = 5f

/** Share of the width on each side where a tap turns the page. */
private const val EDGE_FRACTION = 0.2f

/**
 * Rects for [pages] (sizes in PDF points) laid out side by side at one scale, as large as fits in [viewport],
 * centred.
 */
internal fun pageRects(viewport: Size, pages: List<Size>): List<Rect> {
    val totalWidth = pages.sumOf { it.width.toDouble() }.toFloat()
    val scale = min(viewport.width / totalWidth, viewport.height / pages.maxOf { it.height })
    var left = (viewport.width - totalWidth * scale) / 2
    return pages.map { page ->
        val width = page.width * scale
        val height = page.height * scale
        val top = (viewport.height - height) / 2
        Rect(left, top, left + width, top + height).also { left += width }
    }
}

/** The smallest rect holding both. */
internal fun Rect.union(other: Rect) =
    Rect(min(left, other.left), min(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom))

/** Whole pixels covered by this rect, for rendering it. */
internal fun Rect.pixelSize() = IntSize(width.roundToInt(), height.roundToInt())

/** Indices of the Pages shown from [index]: two side by side in a [spread], else one. */
internal fun shownPages(index: Int, spread: Boolean, pageCount: Int): List<Int> =
    if (spread && index + 1 < pageCount) listOf(index, index + 1) else listOf(index)

/**
 * First Page after turning [direction] (+1 forward, -1 back) by [step] Pages from [index]. Forward stops when
 * nothing new would show (the last Page is already on screen); back stops at the first Page.
 */
internal fun turnedPage(index: Int, direction: Int, step: Int, spread: Boolean, pageCount: Int): Int {
    val target = index + direction * step
    fun lastShown(first: Int) = shownPages(first, spread, pageCount).last()
    return when {
        target < 0 -> 0
        target >= pageCount || lastShown(target) <= lastShown(index) && direction > 0 -> index
        else -> target
    }
}

/** Zoom over the fit layout: screen = fit * [scale] + [offset]. */
internal data class Zoom(val scale: Float = 1f, val offset: Offset = Offset.Zero) {
    val isZoomed: Boolean get() = scale > 1f

    /** Where [fit] (a rect in fit coordinates) is on screen. */
    fun toScreen(fit: Rect) = Rect(fit.topLeft * scale + offset, fit.size * scale)

    /** Where Page [pageIndex] (of [pageSize] points, laid out at [fit]) is on screen, for Annotations to map through. */
    fun placement(pageIndex: Int, pageSize: Size, fit: Rect) =
        PagePlacement(pageIndex, pageSize, scale * fit.width / pageSize.width, fit.topLeft * scale + offset)

    /** Zooms by [factor] around [centroid] (screen), within 1×–[MAX_ZOOM], then keeps [content] on screen. */
    fun zoomedBy(factor: Float, centroid: Offset, viewport: Size, content: Rect): Zoom {
        // Zooming out snaps to fit near 1×, so a Page is never left slightly scaled (and blurred) yet treated as not
        // zoomed. Only out: a pinch arrives in steps of about 1.005×, which must add up when zooming in.
        val newScale = (scale * factor).coerceIn(1f, MAX_ZOOM).let { if (factor < 1f && it < 1.01f) 1f else it }
        val applied = newScale / scale
        return Zoom(newScale, centroid - (centroid - offset) * applied).clamped(viewport, content)
    }

    /** Pans by [delta]. Returns the new zoom and the overscroll: the part of [delta] stopped at the content's edge. */
    fun pannedBy(delta: Offset, viewport: Size, content: Rect): Pair<Zoom, Offset> {
        val wanted = copy(offset = offset + delta)
        val allowed = wanted.clamped(viewport, content)
        return allowed to (wanted.offset - allowed.offset)
    }

    /** Keeps [content] (fit coordinates) covering the viewport where it is larger, centred where it is smaller. */
    fun clamped(viewport: Size, content: Rect): Zoom {
        fun axis(offset: Float, start: Float, end: Float, length: Float): Float {
            val scaledStart = start * scale
            val scaledEnd = end * scale
            val size = scaledEnd - scaledStart
            return if (size <= length) (length - size) / 2 - scaledStart else offset.coerceIn(length - scaledEnd, -scaledStart)
        }
        return copy(
            offset = Offset(
                axis(offset.x, content.left, content.right, viewport.width),
                axis(offset.y, content.top, content.bottom, viewport.height),
            ),
        )
    }
}

/**
 * Page turn for one finger gesture: +1 forward, -1 back, 0 none.
 * - A tap (finger travel [drag] under [tapSlop]) in the outer [EDGE_FRACTION] of [width] turns towards that side.
 * - Not zoomed, a horizontal swipe longer than [swipeDistance] turns.
 * - Zoomed, the swipe has to go that far past the content's edge ([overscroll]), since a plain swipe pans.
 */
internal fun pageTurnFor(
    start: Offset,
    drag: Offset,
    overscroll: Offset,
    zoomed: Boolean,
    width: Float,
    swipeDistance: Float,
    tapSlop: Float,
): Int {
    if (drag.getDistance() <= tapSlop) {
        return when {
            start.x < width * EDGE_FRACTION -> -1
            start.x > width * (1 - EDGE_FRACTION) -> 1
            else -> 0
        }
    }
    val swipe = if (zoomed) overscroll else drag
    return if (abs(swipe.x) > swipeDistance && abs(swipe.x) > abs(swipe.y)) -sign(swipe.x).toInt() else 0
}
