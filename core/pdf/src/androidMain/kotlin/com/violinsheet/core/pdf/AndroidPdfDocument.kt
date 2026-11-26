package com.violinsheet.core.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntRect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

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

    override suspend fun pageSizes(): List<Size> = withContext(thread) {
        (0 until pageCount).map { index ->
            renderer.openPage(index).use { Size(it.width.toFloat(), it.height.toFloat()) }
        }
    }

    override suspend fun render(index: Int, width: Int, height: Int, area: IntRect): ImageBitmap = withContext(thread) {
        renderer.openPage(index).use { page ->
            val bitmap = Bitmap.createBitmap(area.width.coerceAtLeast(1), area.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            // Pages without their own background render transparent; paper is white.
            bitmap.eraseColor(Color.WHITE)
            val transform = Matrix().apply {
                setScale(width.toFloat() / page.width, height.toFloat() / page.height)
                postTranslate(-area.left.toFloat(), -area.top.toFloat())
            }
            page.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
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
