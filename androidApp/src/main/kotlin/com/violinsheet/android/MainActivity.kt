package com.violinsheet.android

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.violinsheet.core.data.sheetPdf
import com.violinsheet.core.pdf.openPdf
import com.violinsheet.feature.settings.SettingsScreen
import com.violinsheet.feature.annotation.AnnotationViewModel
import com.violinsheet.feature.annotation.InkToolbar
import com.violinsheet.feature.annotation.UndoControls
import com.violinsheet.feature.annotation.LassoControls
import com.violinsheet.feature.annotation.ToolPanelState
import com.violinsheet.feature.annotation.closesToolPanel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.violinsheet.core.ink.InkLayer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.violinsheet.feature.viewer.SheetListScreen
import com.violinsheet.feature.viewer.SheetListViewModel
import com.violinsheet.feature.viewer.SheetScreen
import com.violinsheet.feature.viewer.SheetViewModel

private val PDF_MIME_TYPES = arrayOf("application/pdf")

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
                        val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                            if (uri != null) viewModel.import { app.sheetImporter.import(uri) }
                        }
                        // Saved, because Android can recreate the activity (or the process) while the picker is open.
                        var reattachId by rememberSaveable { mutableStateOf<Long?>(null) }
                        val reattachPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                            val sheetId = reattachId
                            reattachId = null
                            if (uri != null && sheetId != null) viewModel.reattach { app.sheetImporter.reattach(sheetId, uri) }
                        }
                        SheetListScreen(
                            viewModel,
                            onImport = { importPicker.launch(PDF_MIME_TYPES) },
                            onOpen = { nav.navigate("sheet/${it.id}/${Uri.encode(it.pdfFile)}") },
                            onReattach = {
                                reattachId = it.id
                                reattachPicker.launch(PDF_MIME_TYPES)
                            },
                            onSettings = { nav.navigate("settings") },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(app.settingsRegistry, app.settingsStore, onBack = { nav.popBackStack() })
                    }
                    composable("sheet/{id}/{pdfFile}") { entry ->
                        val sheetId = entry.arguments?.getString("id")?.toLongOrNull() ?: 0
                        val pdfFile = entry.arguments?.getString("pdfFile").orEmpty()
                        // The list only opens Sheets whose PDF exists (a missing one shows the re-import prompt).
                        val viewModel = viewModel { SheetViewModel(openPdf(sheetPdf(app, pdfFile)), app.settingsStore) }
                        val annotations = viewModel { AnnotationViewModel(sheetId, app.inkRepository, app.settingsStore, app.eraserMemory) }
                        val strokes by annotations.strokes.collectAsState()
                        val tools by annotations.tools.collectAsState()
                        val saveFailed by annotations.saveFailed.collectAsState()
                        val canUndo by annotations.canUndo.collectAsState()
                        val canRedo by annotations.canRedo.collectAsState()
                        // Only whether there is a selection, so the toolbar doesn't recompose on every drag event.
                        val hasSelection by remember(annotations) { annotations.selection.map { it != null }.distinctUntilChanged() }
                            .collectAsState(initial = false)
                        val canPaste by annotations.canPaste.collectAsState()
                        val selectionColour = MaterialTheme.colorScheme.primary
                        val panel = remember { ToolPanelState() }
                        SheetScreen(
                            viewModel,
                            overlay = { placements ->
                                val selection by annotations.selection.collectAsState()
                                val shownPages = remember(placements) { placements.associate { it.pageIndex to it.pageSize } }
                                LaunchedEffect(shownPages) { annotations.onVisiblePages(shownPages) }
                                InkLayer(
                                    placements, strokes, tools.current, tools.eraser, annotations::addStrokes, annotations,
                                    selection, annotations, selectionColour, annotations::onPenDown, Modifier.fillMaxSize(),
                                )
                            },
                            toolbar = {
                                InkToolbar(
                                    tools,
                                    annotations::setTools,
                                    { annotations.setTools(it, save = false) },
                                    UndoControls(canUndo, canRedo, annotations::undo, annotations::redo),
                                    LassoControls(hasSelection, canPaste, annotations::deleteSelection, annotations::copySelection, annotations::paste),
                                    panel,
                                    saveFailed,
                                )
                            },
                            modifier = Modifier.closesToolPanel(panel),
                        )
                    }
                }
            }
        }
    }
}
