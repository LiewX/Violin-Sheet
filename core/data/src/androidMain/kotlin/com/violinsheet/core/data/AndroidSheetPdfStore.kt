package com.violinsheet.core.data

import android.content.Context

class AndroidSheetPdfStore(private val context: Context) : SheetPdfStore {
    override fun exists(pdfFile: String) = sheetPdf(context, pdfFile).exists()

    override fun delete(pdfFile: String) {
        sheetPdf(context, pdfFile).delete()
    }
}
