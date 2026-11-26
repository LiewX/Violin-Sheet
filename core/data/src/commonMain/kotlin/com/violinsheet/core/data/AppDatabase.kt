package com.violinsheet.core.data

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [Sheet::class],
    version = 3,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // Sheet.deletedAt
        AutoMigration(from = 2, to = 3), // Sheet.advanceByOne
    ],
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sheetDao(): SheetDao
}

// Room's KSP processor generates the actual for each target.
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
