package com.violinsheet.core.data

/** The imported PDF copies, by [Sheet.pdfFile]. They live outside Android's backup, so after a restore they can be missing. */
interface SheetPdfStore {
    fun exists(pdfFile: String): Boolean

    fun delete(pdfFile: String)
}
