package com.violinsheet.core.data.symbol

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

/** A stored Symbol (see [Symbol] for the fields). Deleted with its Sheet. */
@Entity(
    tableName = "Symbol",
    foreignKeys = [ForeignKey(entity = Sheet::class, parentColumns = ["id"], childColumns = ["sheetId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sheetId")],
)
class SymbolRow(
    @PrimaryKey val id: Long,
    val sheetId: Long,
    val pageIndex: Int,
    val kind: String,
    val x: Float,
    val y: Float,
    @ColumnInfo(defaultValue = "1") val scale: Float,
    val colour: Int,
    /** Null until ties, slurs and hairpins (#16, #17). */
    val endX: Float?,
    /** Null until ties, slurs and hairpins (#16, #17). */
    val endY: Float?,
    /** Null until ties, slurs and hairpins (#16, #17). */
    val bend: Float?,
)

@Dao
interface SymbolDao {
    @Query("SELECT * FROM Symbol WHERE sheetId = :sheetId ORDER BY id")
    suspend fun forSheet(sheetId: Long): List<SymbolRow>

    /** Replaces a row with the same id, so a redo that re-saves a Symbol whose undo didn't delete it still succeeds. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rows: List<SymbolRow>)

    @Query("DELETE FROM Symbol WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("SELECT id FROM Symbol WHERE id IN (:ids) AND sheetId != :sheetId")
    suspend fun idsOnOtherSheets(ids: List<Long>, sheetId: Long): List<Long>

    /**
     * Deletes [removeIds], then saves [rows], as one write. Symbol ids are unique across all Sheets: rather than
     * silently replace another Sheet's Symbol, the write fails.
     */
    @Transaction
    suspend fun replace(removeIds: List<Long>, rows: List<SymbolRow>) {
        if (removeIds.isNotEmpty()) delete(removeIds)
        if (rows.isEmpty()) return
        val taken = idsOnOtherSheets(rows.map { it.id }, rows.first().sheetId)
        check(taken.isEmpty()) { "Symbol ids $taken belong to another Sheet" }
        insert(rows)
    }
}
