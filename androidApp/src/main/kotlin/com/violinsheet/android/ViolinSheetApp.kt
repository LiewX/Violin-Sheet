package com.violinsheet.android

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.violinsheet.core.data.AndroidSheetPdfStore
import com.violinsheet.core.data.SheetImporter
import com.violinsheet.core.data.SheetRepository
import com.violinsheet.core.data.appDatabase
import com.violinsheet.core.data.ink.InkRepository
import com.violinsheet.core.data.settings.SettingsRegistry
import com.violinsheet.core.data.settings.SettingsStore
import com.violinsheet.feature.annotation.InkSettings
import com.violinsheet.feature.viewer.ViewerSettings

class ViolinSheetApp : Application() {
    private val database by lazy { appDatabase(this) }
    val sheetRepository by lazy { SheetRepository(database.sheetDao(), AndroidSheetPdfStore(this)) }
    /** For work that must finish even if the screen that started it closes (saving a Setting or a stroke). */
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.e("ViolinSheet", "Background save failed", e) },
    )
    val settingsStore by lazy { SettingsStore(database.settingDao(), appScope) }

    /** Every feature's Settings. A feature with Settings adds its list here once; the page picks up the rest. */
    val settingsRegistry by lazy { SettingsRegistry(listOf(ViewerSettings.all, InkSettings.all)) }
    val inkRepository by lazy { InkRepository(database.inkDao(), appScope) }
    val sheetImporter by lazy { SheetImporter(this, sheetRepository) }
}
