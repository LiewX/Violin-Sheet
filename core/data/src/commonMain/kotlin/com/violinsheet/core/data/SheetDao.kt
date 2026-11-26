package com.violinsheet.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SheetDao {
    @Query("SELECT * FROM Sheet WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Sheet>>

    /** Includes deleted Sheets still in their Undo window, so an undo never brings back a duplicate name. */
    @Query("SELECT name FROM Sheet")
    suspend fun names(): List<String>

    @Insert
    suspend fun insert(sheet: Sheet): Long

    @Query("UPDATE Sheet SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("SELECT * FROM Sheet WHERE id = :id")
    suspend fun get(id: Long): Sheet?

    @Query("SELECT * FROM Sheet WHERE id = :id")
    fun observe(id: Long): Flow<Sheet?>

    @Query("UPDATE Sheet SET advanceByOne = :advanceByOne WHERE id = :id")
    suspend fun setAdvanceByOne(id: Long, advanceByOne: Boolean)

    @Query("UPDATE Sheet SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun setDeletedAt(id: Long, deletedAt: Long)

    /** Returns 0 if the Sheet was already purged. */
    @Query("UPDATE Sheet SET deletedAt = NULL WHERE id = :id AND deletedAt IS NOT NULL")
    suspend fun undoDelete(id: Long): Int

    @Query("SELECT * FROM Sheet WHERE deletedAt IS NOT NULL AND deletedAt <= :cutoff")
    suspend fun deletedBefore(cutoff: Long): List<Sheet>

    @Query("DELETE FROM Sheet WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM Sheet WHERE pdfFile = :pdfFile")
    suspend fun countUsing(pdfFile: String): Int

    @Query("SELECT * FROM Sheet WHERE fileHash = :fileHash AND deletedAt IS NULL")
    suspend fun withHash(fileHash: String): List<Sheet>

    @Query("UPDATE Sheet SET pdfFile = :pdfFile WHERE id IN (:ids)")
    suspend fun setPdfFile(ids: List<Long>, pdfFile: String)
}
