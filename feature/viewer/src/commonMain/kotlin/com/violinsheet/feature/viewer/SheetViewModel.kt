package com.violinsheet.feature.viewer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinsheet.core.pdf.PdfDocument
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One open Sheet: the current Page, and rendered Pages kept ready so a turn never shows a blank Page. */
class SheetViewModel(private val document: PdfDocument) : ViewModel() {
    val pageCount: Int = document.pageCount

    private val _pageIndex = MutableStateFlow(0)
    val pageIndex: StateFlow<Int> = _pageIndex.asStateFlow()

    /** A Page rendered to fit a view of [size] pixels. */
    private data class PageKey(val index: Int, val size: IntSize)

    // Only touched on the main thread (composition and viewModelScope), so plain maps are safe.
    private val rendered = object : LinkedHashMap<PageKey, ImageBitmap>(8, 0.75f, true) {
        // Previous, current and next Page, plus one spare for a quick turn back.
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PageKey, ImageBitmap>) = size > 4
    }
    private val inFlight = mutableMapOf<PageKey, Deferred<ImageBitmap>>()

    fun turn(by: Int) {
        _pageIndex.value = (_pageIndex.value + by).coerceIn(0, pageCount - 1)
    }

    /** The Page if it is already rendered, so a turn can show it in the same frame. */
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

    fun preRender(index: Int, size: IntSize) {
        if (index !in 0 until pageCount) return
        viewModelScope.launch {
            // A Page that fails here is reported when it is turned to; a pre-render must not crash the app.
            runCatching { renderPage(index, size) }
        }
    }

    override fun onCleared() {
        document.close()
    }
}
