package com.violinsheet.core.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.roundToInt

/** Opens [file] read-only. Throws if it is not a readable PDF (IOException) or is password-protected (SecurityException). */
fun openPdf(file: File): PdfDocument = AndroidPdfDocument(file)

private class AndroidPdfDocument(file: File) : PdfDocument {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = try {
        PdfRenderer(descriptor)
    } catch (e: Exception) {
        descriptor.close()
        throw e
    }

    // PdfRenderer allows one open Page at a time and isn't thread-safe, so every call runs on this one thread.
    private val thread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    override val pageCount: Int = renderer.pageCount

    override suspend fun render(index: Int, maxWidth: Int, maxHeight: Int): ImageBitmap = withContext(thread) {
        renderer.openPage(index).use { page ->
            val scale = min(maxWidth.toFloat() / page.width, maxHeight.toFloat() / page.height)
            val bitmap = Bitmap.createBitmap(
                (page.width * scale).roundToInt().coerceAtLeast(1),
                (page.height * scale).roundToInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            // Pages without their own background render transparent; paper is white.
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap.asImageBitmap()
        }
    }

    override fun close() {
        // Queued behind any render in progress, so the renderer is never closed mid-render.
        CoroutineScope(thread).launch {
            renderer.close()
            descriptor.close()
        }.invokeOnCompletion { thread.close() }
    }
}
