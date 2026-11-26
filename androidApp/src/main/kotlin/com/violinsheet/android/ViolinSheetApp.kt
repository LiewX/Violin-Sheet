package com.violinsheet.android

import android.app.Application
import com.violinsheet.core.data.SheetImporter
import com.violinsheet.core.data.SheetRepository
import com.violinsheet.core.data.appDatabase

class ViolinSheetApp : Application() {
    val sheetRepository by lazy { SheetRepository(appDatabase(this).sheetDao()) }
    val sheetImporter by lazy { SheetImporter(this, sheetRepository) }
}
