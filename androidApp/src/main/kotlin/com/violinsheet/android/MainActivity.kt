package com.violinsheet.android

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.violinsheet.core.data.sheetPdf
import com.violinsheet.core.pdf.openPdf
import com.violinsheet.feature.viewer.SheetListScreen
import com.violinsheet.feature.viewer.SheetListViewModel
import com.violinsheet.feature.viewer.SheetScreen
import com.violinsheet.feature.viewer.SheetViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ViolinSheetApp
        setContent {
            val nav = rememberNavController()
            MaterialTheme {
                NavHost(nav, startDestination = "sheets") {
                    composable("sheets") {
                        val viewModel = viewModel { SheetListViewModel(app.sheetRepository) }
                        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                            if (uri != null) viewModel.runImport { app.sheetImporter.import(uri) }
                        }
                        SheetListScreen(
                            viewModel,
                            onImport = { picker.launch(arrayOf("application/pdf")) },
                            onOpen = { nav.navigate("sheet/${Uri.encode(it.pdfFile)}") },
                        )
                    }
                    composable("sheet/{pdfFile}") { entry ->
                        val pdfFile = entry.arguments?.getString("pdfFile").orEmpty()
                        // shortcut: a missing PDF (after a restore) crashes here; #3 adds "PDF missing, re-import".
                        // Imports are checked with openPdf, so other open failures shouldn't happen.
                        val viewModel = viewModel { SheetViewModel(openPdf(sheetPdf(app, pdfFile))) }
                        SheetScreen(viewModel)
                    }
                }
            }
        }
    }
}
