package com.violinsheet.core.pdf

import androidx.compose.ui.graphics.ImageBitmap

/** An open PDF whose Pages can be rendered as images (ADR 0001). */
interface PdfDocument : AutoCloseable {
    val pageCount: Int

    /** Renders Page [index] as large as fits in [maxWidth] × [maxHeight] pixels, on white paper. */
    suspend fun render(index: Int, maxWidth: Int, maxHeight: Int): ImageBitmap
}
