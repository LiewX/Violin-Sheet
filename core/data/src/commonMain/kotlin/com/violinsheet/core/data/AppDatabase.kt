package com.violinsheet.core.data

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.violinsheet.core.data.ink.InkDao
import com.violinsheet.core.data.ink.InkStrokeRow
import com.violinsheet.core.data.settings.SettingDao
import com.violinsheet.core.data.settings.SettingValue
import com.violinsheet.core.data.symbol.SymbolDao
import com.violinsheet.core.data.symbol.SymbolRow

@Database(
    entities = [Sheet::class, SettingValue::class, InkStrokeRow::class, SymbolRow::class],
    version = 7,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // Sheet.deletedAt
        AutoMigration(from = 2, to = 3), // Sheet.advanceByOne
        AutoMigration(from = 3, to = 4), // SettingValue
        AutoMigration(from = 4, to = 5), // InkStroke
        AutoMigration(from = 5, to = 6), // InkStroke.tool
        AutoMigration(from = 6, to = 7), // Symbol
    ],
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sheetDao(): SheetDao

    abstract fun settingDao(): SettingDao

    abstract fun inkDao(): InkDao

    abstract fun symbolDao(): SymbolDao
}

// Room's KSP processor generates the actual for each target.
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
