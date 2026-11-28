package com.violinsheet.feature.annotation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.violinsheet.core.ink.InkLayer
import com.violinsheet.core.ink.PagePlacement
import com.violinsheet.feature.annotation.edit.EditPenLayer
import com.violinsheet.feature.annotation.edit.Mode
import com.violinsheet.feature.annotation.edit.symbol.SymbolLayer

/**
 * The open Sheet's Annotations over the Pages at [placements], wired to its [viewModel], bottom to top: Symbols, Ink
 * (taking the pen only in Ink mode), and in Edit mode the Edit pen layer with the Symbol selection's frame. Tells the
 * ViewModel which Pages are shown.
 */
@Composable
fun AnnotationOverlay(viewModel: AnnotationViewModel, placements: List<PagePlacement>) {
    val mode by viewModel.mode.collectAsState()
    val strokes by viewModel.strokes.collectAsState()
    val tools by viewModel.tools.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val symbols by viewModel.symbols.collectAsState()
    val symbolSelection by viewModel.symbolSelection.collectAsState()
    val symbolPlacing by viewModel.symbolPlacing.collectAsState()
    val editTools by viewModel.editTools.collectAsState()
    val selectionColour = MaterialTheme.colorScheme.primary
    val shownPages = remember(placements) { placements.associate { it.pageIndex to it.pageSize } }
    LaunchedEffect(shownPages) { viewModel.onVisiblePages(shownPages) }
    SymbolLayer(placements, symbols, symbolSelection, symbolPlacing, Modifier.fillMaxSize())
    InkLayer(
        placements, strokes, tools.current, tools.eraser, viewModel::addStrokes, viewModel,
        selection, viewModel, selectionColour, viewModel::onPenDown,
        penEnabled = mode == Mode.Ink, modifier = Modifier.fillMaxSize(),
    )
    if (mode == Mode.Edit) {
        EditPenLayer(placements, viewModel.edit, editTools.chosen, symbolSelection, selectionColour, Modifier.fillMaxSize())
    }
}
