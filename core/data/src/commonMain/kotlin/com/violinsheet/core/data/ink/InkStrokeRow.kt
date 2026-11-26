package com.violinsheet.core.data.ink

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
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
)

@Dao
interface InkDao {
    @Query("SELECT * FROM InkStroke WHERE sheetId = :sheetId ORDER BY id")
    suspend fun forSheet(sheetId: Long): List<InkStrokeRow>

    /** Replaces a row with the same id, so a redo that re-saves a stroke whose undo didn't delete it still succeeds. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: InkStrokeRow)

    @Query("DELETE FROM InkStroke WHERE id = :id")
    suspend fun delete(id: Long)
}
