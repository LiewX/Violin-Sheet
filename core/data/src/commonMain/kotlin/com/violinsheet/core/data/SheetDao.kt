package com.violinsheet.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SheetDao {
    @Query("SELECT * FROM Sheet ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Sheet>>

    @Query("SELECT name FROM Sheet")
    suspend fun names(): List<String>

    @Insert
    suspend fun insert(sheet: Sheet): Long
}
