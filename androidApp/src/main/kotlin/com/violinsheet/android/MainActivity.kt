package com.violinsheet.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.violinsheet.feature.viewer.SheetListScreen
import com.violinsheet.feature.viewer.SheetListViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ViolinSheetApp
        setContent {
            val viewModel = viewModel { SheetListViewModel(app.sheetRepository) }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) viewModel.import { app.sheetImporter.import(uri) }
            }
            MaterialTheme {
                SheetListScreen(viewModel, onImport = { picker.launch(arrayOf("application/pdf")) })
            }
        }
    }
}
