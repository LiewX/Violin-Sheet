package com.violinsheet.core.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID

fun appDatabase(context: Context): AppDatabase =
    Room.databaseBuilder<AppDatabase>(context, context.getDatabasePath("violinsheet.db").absolutePath)
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

/** Copies a picked PDF into app storage and adds it as a Sheet. The copy is never modified (ADR 0001). */
class SheetImporter(private val context: Context, private val repository: SheetRepository) {
    // noBackupFilesDir: Android's backup is capped at 25 MB per app, so PDFs stay out of it (SPECS: Sheets and reading).
    private val sheetsDir get() = File(context.noBackupFilesDir, "sheets").apply { mkdirs() }

    suspend fun import(uri: Uri): Sheet = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val fileName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
            ?: "Sheet.pdf"
        val target = File(sheetsDir, "${UUID.randomUUID()}.pdf")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val input = resolver.openInputStream(uri) ?: throw IOException("Can't open $fileName")
            DigestInputStream(input, digest).use { source -> target.outputStream().use { source.copyTo(it) } }
            if (!looksLikePdf(target)) throw IOException("$fileName is not a PDF")
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            repository.add(fileName, hash, target.name)
        } catch (e: Throwable) {
            target.delete()
            throw e
        }
    }

    // The PDF header must appear within the first 1024 bytes.
    private fun looksLikePdf(file: File): Boolean =
        file.inputStream().use { String(it.readNBytes(1024), Charsets.ISO_8859_1) }.contains("%PDF-")
}
