package com.violinsheet.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Sheet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** SHA-256 of the PDF's contents; recognises the same file on re-import after a restore. */
    val fileHash: String,
    /** File name of the PDF copy in app storage. Only the name, so the storage folder can move. */
    val pdfFile: String,
    /** When the user deleted it (epoch ms). Hidden from the list, and purged once the Undo bar has expired. */
    val deletedAt: Long? = null,
)
