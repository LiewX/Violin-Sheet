package com.violinsheet.core.data

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.violinsheet.core.data.settings.SettingDao
import com.violinsheet.core.data.settings.SettingValue

@Database(
    entities = [Sheet::class, SettingValue::class],
    version = 4,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // Sheet.deletedAt
        AutoMigration(from = 2, to = 3), // Sheet.advanceByOne
        AutoMigration(from = 3, to = 4), // SettingValue
    ],
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sheetDao(): SheetDao

    abstract fun settingDao(): SettingDao
}

// Room's KSP processor generates the actual for each target.
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
