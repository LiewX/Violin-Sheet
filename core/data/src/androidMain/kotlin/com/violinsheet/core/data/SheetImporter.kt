package com.violinsheet.core.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.violinsheet.core.pdf.openPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID

/** The stored PDF of a Sheet whose [Sheet.pdfFile] is [pdfFile]. */
// noBackupFilesDir: Android's backup is capped at 25 MB per app, so PDFs stay out of it (SPECS: Sheets and reading).
fun sheetPdf(context: Context, pdfFile: String): File = File(File(context.noBackupFilesDir, "sheets"), pdfFile)

/** Copies a picked PDF into app storage for a Sheet. The copy is never modified (ADR 0001). */
class SheetImporter(private val context: Context, private val repository: SheetRepository) {
    /** Home-screen import: a new Sheet, or reattaches Sheets missing this PDF. */
    suspend fun import(uri: Uri): ImportResult =
        withCopy(uri) { fileName, hash, pdfFile -> repository.import(fileName, hash, pdfFile) }

    /** Re-import from a Sheet's "PDF missing" prompt. Throws [SheetException] if it is a different file. */
    suspend fun reattach(sheetId: Long, uri: Uri): Sheet =
        withCopy(uri) { _, hash, pdfFile -> repository.reattach(sheetId, hash, pdfFile) }

    /** Copies and checks the picked file, then hands it to [use]. The copy is deleted if anything fails. */
    private suspend fun <T> withCopy(
        uri: Uri,
        use: suspend (fileName: String, fileHash: String, pdfFile: String) -> T,
    ): T = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val fileName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
            ?: "Sheet.pdf"
        val target = sheetPdf(context, "${UUID.randomUUID()}.pdf").apply { parentFile?.mkdirs() }
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val input = resolver.openInputStream(uri) ?: throw IOException("Can't open $fileName")
            DigestInputStream(input, digest).use { source -> target.outputStream().use { source.copyTo(it) } }
            // Opening it here rejects files the viewer couldn't render: not a PDF, damaged, password-protected, empty.
            val pageCount = try {
                openPdf(target).use { it.pageCount }
            } catch (e: Exception) {
                throw IOException("$fileName is not a readable PDF", e)
            }
            if (pageCount == 0) throw IOException("$fileName has no pages")
            use(fileName, digest.digest().joinToString("") { "%02x".format(it) }, target.name)
        } catch (e: Throwable) {
            target.delete()
            throw e
        }
    }
}
