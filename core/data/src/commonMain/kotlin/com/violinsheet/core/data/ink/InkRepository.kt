package com.violinsheet.core.data.ink

import com.violinsheet.core.ink.InkStroke
import com.violinsheet.core.ink.decodeInkPoints
import com.violinsheet.core.ink.encodeInkPoints

/** A Sheet's Ink strokes, in the shared stroke format. */
class InkRepository(private val dao: InkDao) {
    /** The Sheet's strokes. A row that can't be read is skipped (and reported), so it can't hide the rest. */
    suspend fun strokes(sheetId: Long, onUnreadable: (Throwable) -> Unit = {}): List<InkStroke> =
        dao.forSheet(sheetId).mapNotNull { row ->
            runCatching { InkStroke(row.id, row.pageIndex, row.colour, row.size, decodeInkPoints(row.points)) }
                .onFailure(onUnreadable)
                .getOrNull()
        }

    suspend fun add(sheetId: Long, stroke: InkStroke) =
        dao.insert(InkStrokeRow(stroke.id, sheetId, stroke.pageIndex, stroke.colour, stroke.size, encodeInkPoints(stroke.points)))
}
