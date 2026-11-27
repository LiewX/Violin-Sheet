package com.violinsheet.core.ink

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

private val InkPoint.offset get() = Offset(x, y)

private fun List<PagePlacement>.pageAt(screen: Offset) = firstOrNull { it.holds(screen) }

private fun InkPoint.toScreen(page: PagePlacement) = page.toScreen(offset).let { copy(x = it.x, y = it.y) }

/** This screen point in [page]'s coordinates, kept within the Page (a cut point may lie a rounding error outside). */
private fun InkPoint.toPageOf(page: PagePlacement): InkPoint {
    val p = page.toPage(offset)
    return copy(x = p.x.coerceIn(0f, page.pageSize.width), y = p.y.coerceIn(0f, page.pageSize.height))
}

/** Where on the segment a→b (0–1, ends excluded) it crosses an edge of [box]. */
private fun MutableList<Float>.addEdgeCrossings(a: InkPoint, b: InkPoint, box: Rect) {
    val dx = b.x - a.x
    val dy = b.y - a.y
    for (edge in floatArrayOf(box.left, box.right)) {
        if (dx == 0f) break
        val t = (edge - a.x) / dx
        if (t > 0f && t < 1f && (a.y + dy * t) in box.top..box.bottom) add(t)
    }
    for (edge in floatArrayOf(box.top, box.bottom)) {
        if (dy == 0f) break
        val t = (edge - a.y) / dy
        if (t > 0f && t < 1f && (a.x + dx * t) in box.left..box.right) add(t)
    }
}

/** A point of a part on screen: the stroke's own point [index], or a cut point (index -1). */
private class Vertex(val screen: InkPoint, val index: Int)

/** A run of the stroke on one Page. */
private class Run(val page: PagePlacement, val vertices: List<Vertex>)

/** The runs of the polyline [screen] on [pages], cut at the Page edges, in drawing order; runs under 2 points dropped. */
private fun runsOn(screen: List<InkPoint>, pages: List<PagePlacement>): List<Run> {
    val runs = mutableListOf<Run>()
    var page: PagePlacement? = null
    var run = mutableListOf<Vertex>()
    fun closeRun() {
        val on = page
        if (on != null && run.size >= 2) runs += Run(on, run)
        run = mutableListOf()
    }
    fun MutableList<Vertex>.addDistinct(v: Vertex) {
        val last = lastOrNull()?.screen
        if (last == null || last.x != v.screen.x || last.y != v.screen.y || last.elapsedMillis != v.screen.elapsedMillis) add(v)
    }
    val cuts = mutableListOf<Float>()
    for (i in 1 until screen.size) {
        val a = screen[i - 1]
        val b = screen[i]
        // The stroke's own points exactly at the ends; interpolated only in between.
        fun at(t: Float) = when (t) {
            0f -> Vertex(a, i - 1)
            1f -> Vertex(b, i)
            else -> Vertex(a.lerp(b, t), -1)
        }
        cuts.clear()
        cuts += 0f
        for (p in pages) cuts.addEdgeCrossings(a, b, p.screenRect)
        cuts += 1f
        cuts.sort()
        for (k in 1 until cuts.size) {
            val t0 = cuts[k - 1]
            val t1 = cuts[k]
            if (t1 <= t0) continue // the same cut twice: a corner
            // The stretch between two cuts lies on one Page or none; its middle tells which.
            val on = pages.pageAt(a.lerp(b, (t0 + t1) / 2).offset)
            if (on != page) {
                closeRun()
                page = on
                if (on != null) run.addDistinct(at(t0))
            }
            if (on != null) run.addDistinct(at(t1))
        }
    }
    closeRun()
    return runs
}

/**
 * This stroke, drawn in [reference]'s Page coordinates, as one stroke per Page it runs over in [placements] (the Pages
 * on screen, plus [reference] if it has left the screen): it is cut where it crosses a Page's edge, each part in its own
 * Page's coordinates, in drawing order. A stroke that leaves a Page and comes back gives separate parts; what runs
 * outside every Page is dropped, and so is a part of fewer than 2 points (a single-point dot on a Page stays). Pressure
 * and time are interpolated at each cut; the stroke's own points on [reference] are kept exactly. The first part keeps
 * the stroke's id, further parts take [nextId]. A stroke wholly on [reference] comes back as itself.
 */
fun InkStroke.splitAtPages(reference: PagePlacement, placements: List<PagePlacement>, nextId: () -> Long): List<InkStroke> {
    val screen = points.map { it.toScreen(reference) }
    if (screen.all { reference.holds(it.offset) }) return listOf(this)
    val pages = if (placements.any { it.pageIndex == reference.pageIndex }) placements else placements + reference
    val runs = if (screen.size == 1) {
        listOfNotNull(pages.pageAt(screen[0].offset)?.let { Run(it, listOf(Vertex(screen[0], -1))) })
    } else {
        runsOn(screen, pages)
    }
    return runs.mapIndexed { i, run ->
        val on = run.page
        val ownPage = on.pageIndex == reference.pageIndex
        InkStroke(
            if (i == 0) id else nextId(),
            on.pageIndex,
            colour,
            // Size is in Page units: a Page shown at another scale needs it converted to look the same.
            size * reference.scale / on.scale,
            run.vertices.map { if (ownPage && it.index >= 0) points[it.index] else it.screen.toPageOf(on) },
            tool,
        )
    }
}
