package com.violinsheet.core.data.settings

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A stored Setting value. Kept in the app database, so Android backup covers it. */
@Entity
data class SettingValue(@PrimaryKey val key: String, val value: String)

@Dao
interface SettingDao {
    @Query("SELECT value FROM SettingValue WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Upsert
    suspend fun upsert(value: SettingValue)
}
