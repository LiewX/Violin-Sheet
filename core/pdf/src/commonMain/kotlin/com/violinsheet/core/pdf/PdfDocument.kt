package com.violinsheet.core.pdf

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntRect

/** An open PDF whose Pages can be rendered as images (ADR 0001). */
interface PdfDocument : AutoCloseable {
    val pageCount: Int

    /** Size of every Page, in PDF points. */
    suspend fun pageSizes(): List<Size>

    /**
     * Renders Page [index] scaled to [width] × [height] pixels, on white paper. Only [area] of that (in the same
     * pixels) is rendered and returned, so a zoomed-in view can render just what is on screen.
     */
    suspend fun render(index: Int, width: Int, height: Int, area: IntRect = IntRect(0, 0, width, height)): ImageBitmap
}
