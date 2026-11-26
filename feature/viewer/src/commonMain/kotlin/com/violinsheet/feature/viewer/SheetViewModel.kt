package com.violinsheet.feature.viewer

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinsheet.core.data.SheetRepository
import com.violinsheet.core.pdf.PdfDocument
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One open Sheet: the current Page, and rendered Pages kept ready so a turn never shows a blank Page. */
class SheetViewModel(
    private val document: PdfDocument,
    private val sheetId: Long,
    private val repository: SheetRepository,
) : ViewModel() {
    val pageCount: Int = document.pageCount

    private val _pageIndex = MutableStateFlow(0)

    /** The first Page shown (the left one in a spread). */
    val pageIndex: StateFlow<Int> = _pageIndex.asStateFlow()

    private val _openFailed = MutableStateFlow(false)

    /** Reading the Page sizes failed: the PDF is damaged in a way the import check didn't catch. */
    val openFailed: StateFlow<Boolean> = _openFailed.asStateFlow()

    /** Page sizes in PDF points; null until read, once, when the Sheet opens. */
    val pageSizes: StateFlow<List<Size>?> = flow { emit(document.pageSizes()) }
        .catch { _openFailed.value = true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** In a spread, turns advance one Page instead of two (remembered per Sheet). */
    val advanceByOne: StateFlow<Boolean> = repository.sheet(sheetId)
        .map { it?.advanceByOne ?: false } // shortcut: false stands in for the app-wide Reading Setting until #5
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** A Page rendered at exactly [size] pixels. */
    private data class PageKey(val index: Int, val size: IntSize)

    // Only touched on the main thread (composition and viewModelScope), so plain maps are safe.
    private val rendered = object : LinkedHashMap<PageKey, ImageBitmap>(8, 0.75f, true) {
        // Previous, current and next spread of two Pages each.
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PageKey, ImageBitmap>) = size > 6
    }
    private val inFlight = mutableMapOf<PageKey, Deferred<ImageBitmap>>()

    fun setAdvanceByOne(advanceByOne: Boolean) {
        viewModelScope.launch { repository.setAdvanceByOne(sheetId, advanceByOne) }
    }

    private fun step(spread: Boolean) = if (spread && !advanceByOne.value) 2 else 1

    fun turn(direction: Int, spread: Boolean) {
        _pageIndex.value = turnedPage(_pageIndex.value, direction, step(spread), spread, pageCount)
    }

    /** The Page if it is already rendered at [size], so a turn can show it in the same frame. */
    fun renderedPage(index: Int, size: IntSize): ImageBitmap? = rendered[PageKey(index, size)]

    suspend fun renderPage(index: Int, size: IntSize): ImageBitmap {
        val key = PageKey(index, size)
        rendered[key]?.let { return it }
        // Shared, so a pre-render and the visible Page don't render the same Page twice. The render stores its own
        // result, so it isn't wasted when the caller is cancelled by a fast turn.
        return inFlight.getOrPut(key) {
            viewModelScope.async {
                try {
                    document.render(index, size.width, size.height).also { rendered[key] = it }
                } finally {
                    inFlight.remove(key)
                }
            }
        }.await()
    }

    /** Renders the Pages one turn forward and back from [index], so either turn shows them at once. */
    fun preRenderAround(index: Int, spread: Boolean, viewport: Size) {
        val sizes = pageSizes.value ?: return
        for (direction in listOf(1, -1)) {
            val start = turnedPage(index, direction, step(spread), spread, pageCount)
            if (start == index) continue
            val shown = shownPages(start, spread, pageCount)
            pageRects(viewport, shown.map { sizes[it] }).forEachIndexed { slot, rect ->
                // A Page that fails here is reported when it is turned to; a pre-render must not crash the app.
                viewModelScope.launch { runCatching { renderPage(shown[slot], rect.pixelSize()) } }
            }
        }
    }

    /** Renders only [area] of Page [index] at [pageSize] pixels: the sharp, on-screen part of a zoomed Page. */
    suspend fun renderArea(index: Int, pageSize: IntSize, area: IntRect): ImageBitmap =
        document.render(index, pageSize.width, pageSize.height, area)

    override fun onCleared() {
        document.close()
    }
}
