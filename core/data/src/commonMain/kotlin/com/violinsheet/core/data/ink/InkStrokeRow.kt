package com.violinsheet.core.data.ink

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.violinsheet.core.data.Sheet

/** A stored Ink stroke. Deleted with its Sheet. Points are packed by encodeInkPoints. */
@Entity(
    tableName = "InkStroke",
    foreignKeys = [ForeignKey(entity = Sheet::class, parentColumns = ["id"], childColumns = ["sheetId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sheetId")],
)
class InkStrokeRow(
    /** The stroke's own id (InkStroke.id), given when it is drawn. */
    @PrimaryKey val id: Long,
    val sheetId: Long,
    val pageIndex: Int,
    val colour: Int,
    val size: Float,
    val points: ByteArray,
    /** StrokeTool.stored. Rows from before the highlighter (database version 5) are pens. */
    @ColumnInfo(defaultValue = "pen") val tool: String,
)

@Dao
interface InkDao {
    @Query("SELECT * FROM InkStroke WHERE sheetId = :sheetId ORDER BY id")
    suspend fun forSheet(sheetId: Long): List<InkStrokeRow>

    /** Replaces a row with the same id, so a redo that re-saves a stroke whose undo didn't delete it still succeeds. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rows: List<InkStrokeRow>)

    @Query("DELETE FROM InkStroke WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("SELECT id FROM InkStroke WHERE id IN (:ids) AND sheetId != :sheetId")
    suspend fun idsOnOtherSheets(ids: List<Long>, sheetId: Long): List<Long>

    /**
     * Deletes [removeIds], then saves [rows], as one write: an erase that cuts a stroke into pieces (the first keeping
     * the stroke's id) is saved whole or not at all. Stroke ids are unique across all Sheets, but a piece's id is only
     * chosen against its own Sheet's strokes: rather than silently replace another Sheet's stroke, the write fails.
     */
    @Transaction
    suspend fun replace(removeIds: List<Long>, rows: List<InkStrokeRow>) {
        if (removeIds.isNotEmpty()) delete(removeIds)
        if (rows.isEmpty()) return
        val taken = idsOnOtherSheets(rows.map { it.id }, rows.first().sheetId)
        check(taken.isEmpty()) { "Stroke ids $taken belong to another Sheet" }
        insert(rows)
    }
}
