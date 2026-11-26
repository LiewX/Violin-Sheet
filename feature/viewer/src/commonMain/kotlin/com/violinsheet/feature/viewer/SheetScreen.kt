package com.violinsheet.feature.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.violinsheet.core.ink.PagePlacement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Neutral dark grey around the Page, not black, to reduce eye strain. The Page itself is never inverted. */
private val Surround = Color(0xFF3A3A3A)

/** How long a zoom or pan must rest before the sharp render of what is on screen starts. */
private const val SETTLE_MILLIS = 150L

/**
 * Reads one Sheet: one Page in portrait, two side by side in landscape, with pinch zoom. [overlay] draws Annotations
 * over the Pages, given where each shown Page is on screen; [toolbar] sits at the bottom edge.
 */
@Composable
fun SheetScreen(
    viewModel: SheetViewModel,
    overlay: @Composable (List<PagePlacement>) -> Unit = {},
    toolbar: @Composable () -> Unit = {},
) {
    val index by viewModel.pageIndex.collectAsState()
    val pageSizes by viewModel.pageSizes.collectAsState()
    val advanceByOne by viewModel.advanceByOne.collectAsState()
    val openFailed by viewModel.openFailed.collectAsState()
    BoxWithConstraints(Modifier.fillMaxSize().background(Surround).windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (openFailed) {
            Text("Couldn't open this Sheet: the PDF is damaged", Modifier.align(Alignment.Center), color = Color.White)
            return@BoxWithConstraints
        }
        val sizes = pageSizes ?: return@BoxWithConstraints
        val viewport = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val spread = viewport.width > viewport.height
        val shown = shownPages(index, spread, viewModel.pageCount)
        val rects = remember(shown, viewport, sizes) { pageRects(viewport, shown.map { sizes[it] }) }
        val content = rects.reduce(Rect::union)
        // Kept across turns (landing on the opposite side, see Zoom.afterTurn); a rotation starts at fit.
        var zoom by remember(viewport) { mutableStateOf(Zoom()) }
        var gestureStart by remember { mutableStateOf(zoom) }
        val gestures = object : SheetGestureHandler {
            override val zoomed get() = zoom.isZoomed
            override val atLeftEdge get() = zoom.atLeftEdge(content)
            override val atRightEdge get() = zoom.atRightEdge(viewport, content)

            override fun beginGesture() {
                gestureStart = zoom
            }

            override fun revertGesture() {
                zoom = gestureStart
            }

            override fun transform(centroid: Offset, pan: Offset, factor: Float) {
                zoom = zoom.zoomedBy(factor, centroid, viewport, content).pannedBy(pan, viewport, content).first
            }

            override fun pan(delta: Offset): Offset {
                val (panned, overscroll) = zoom.pannedBy(delta, viewport, content)
                zoom = panned
                return overscroll
            }

            override fun turn(direction: Int) {
                viewModel.turn(direction, spread)
                val next = shownPages(viewModel.pageIndex.value, spread, viewModel.pageCount)
                if (next == shown) return
                // Laid out here, now, so the first frame of the new Pages is already in place.
                val nextRects = pageRects(viewport, next.map { sizes[it] })
                // Land on a Page that wasn't on screen: the first new one going forward, the last going back.
                val fresh = next.indices.filter { next[it] !in shown }
                val landing = nextRects[if (direction > 0) fresh.firstOrNull() ?: 0 else fresh.lastOrNull() ?: next.lastIndex]
                zoom = zoom.afterTurn(direction, viewport, nextRects.reduce(Rect::union), landing)
            }
        }
        // Clipped, so zoomed content never draws over the status bar or the surround.
        Box(Modifier.fillMaxSize().clipToBounds().sheetGestures(gestures)) {
            // The sharp Pages and the Annotation overlay below are drawn in screen coordinates, through the same
            // Zoom, so they line up with this layer at every zoom.
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.offset.x
                    translationY = zoom.offset.y
                },
            ) {
                shown.forEachIndexed { slot, page -> FitPage(viewModel, page, rects[slot]) }
            }
            if (zoom.isZoomed) SharpPages(viewModel, shown, rects, zoom, viewport)
            overlay(shown.mapIndexed { slot, page -> zoom.placement(page, sizes[page], rects[slot]) })
            // Page labels for accessibility, above the overlay: Compose drops nodes that a later sibling (the
            // full-screen Ink view) covers. No input handling, so touches and the pen pass through.
            val zoomLabel = if (zoom.isZoomed) ", zoomed ${(zoom.scale * 100).roundToInt()}%" else ""
            shown.forEachIndexed { slot, page ->
                // Only the on-screen part: labels far larger than the window stop being refreshed while zoomed.
                val visible = zoom.toScreen(rects[slot]).intersect(Rect(Offset.Zero, viewport))
                if (visible.width > 0f && visible.height > 0f) {
                    Box(
                        Modifier.at(visible)
                            .semantics { contentDescription = "Page ${page + 1} of ${viewModel.pageCount}$zoomLabel" },
                    )
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)) { toolbar() }
        if (spread) {
            FilterChip(
                selected = advanceByOne,
                onClick = { viewModel.setAdvanceByOne(!advanceByOne) },
                label = { Text("Advance by one") },
                colors = FilterChipDefaults.filterChipColors(containerColor = Color.White.copy(alpha = 0.85f)),
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
        }
        // After the shown Pages, so their renders are queued first.
        LaunchedEffect(index, spread, viewport, advanceByOne) { viewModel.preRenderAround(index, spread, viewport) }
    }
}

/** One Page at fit size. Until it is rendered, the last Page shown in this place stays, rather than a blank flash. */
@Composable
private fun FitPage(viewModel: SheetViewModel, page: Int, rect: Rect) {
    val size = rect.pixelSize()
    val ready = viewModel.renderedPage(page, size)
    var lastRendered by remember { mutableStateOf<ImageBitmap?>(null) }
    var failedPage by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(page, size) {
        try {
            lastRendered = viewModel.renderPage(page, size)
            failedPage = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failedPage = page
        }
    }
    Box(Modifier.at(rect), contentAlignment = Alignment.Center) {
        if (failedPage == page) {
            Text("Couldn't show Page ${page + 1}", color = Color.White)
        } else {
            (ready ?: lastRendered)?.let {
                Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) // labelled above the overlay
            }
        }
    }
}

/** While zoomed: once the view rests, renders the visible part of each Page at screen resolution over the fit image. */
@Composable
private fun SharpPages(viewModel: SheetViewModel, shown: List<Int>, rects: List<Rect>, zoom: Zoom, viewport: Size) {
    var sharp by remember { mutableStateOf<Pair<Zoom, List<Pair<Rect, ImageBitmap>>>?>(null) }
    LaunchedEffect(zoom, shown, rects) {
        delay(SETTLE_MILLIS)
        val screen = Rect(Offset.Zero, viewport)
        sharp = zoom to shown.mapIndexedNotNull { slot, page ->
            val onScreen = zoom.toScreen(rects[slot])
            val visible = onScreen.intersect(screen).roundToIntRect()
            if (visible.width <= 0 || visible.height <= 0) return@mapIndexedNotNull null
            val area = visible.translate(-onScreen.left.roundToInt(), -onScreen.top.roundToInt())
            // Best effort: if this fails, the fit image stays.
            runCatching { viewModel.renderArea(page, onScreen.pixelSize(), area) }.getOrNull()?.let { visible.toRect() to it }
        }
    }
    sharp?.takeIf { it.first == zoom }?.second?.forEach { (rect, bitmap) ->
        Image(bitmap, null, Modifier.at(rect), contentScale = ContentScale.FillBounds)
    }
}

private fun Rect.roundToIntRect() = IntRect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())

private fun IntRect.toRect() = Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

/** Places a child at [rect], in pixels of its parent. */
@Composable
private fun Modifier.at(rect: Rect): Modifier {
    val density = LocalDensity.current
    return offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
        .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
}
