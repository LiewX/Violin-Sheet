package com.violinsheet.android

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Puts stored Setting values back after a tablet check that changed app-wide ones (Palettes, Size presets), in the
 * app's own process, so no root. Arguments: `keys`, comma-separated Setting keys to delete (they read their defaults
 * again); `put`, `key=value` pairs joined by `~` to store (values may hold commas). Run with
 * `adb shell am instrument -w -e class com.violinsheet.android.RestoreSettingsDeviceTest -e keys a,b -e put a=1~b=2
 * com.violinsheet.test/androidx.test.runner.AndroidJUnitRunner` after the test APK is installed.
 */
@RunWith(AndroidJUnit4::class)
class RestoreSettingsDeviceTest {
    @Test
    fun restoreSettings() {
        val args = InstrumentationRegistry.getArguments()
        val keys = args.getString("keys")?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        val put = args.getString("put")?.split('~')?.filter { it.isNotBlank() }?.map { it.substringBefore('=') to it.substringAfter('=') }.orEmpty()
        require(keys.isNotEmpty() || put.isNotEmpty()) { "pass -e keys <keys> and/or -e put <key=value~...>" }
        val path = InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath("violinsheet.db").path
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            if (keys.isNotEmpty()) db.delete("SettingValue", "key IN (${keys.joinToString { "?" }})", keys.toTypedArray())
            put.forEach { (key, value) ->
                val row = ContentValues().apply { put("key", key); put("value", value) }
                db.insertWithOnConflict("SettingValue", null, row, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }
}
