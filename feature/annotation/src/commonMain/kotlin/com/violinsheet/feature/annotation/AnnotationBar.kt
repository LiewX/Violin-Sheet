package com.violinsheet.feature.annotation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.violinsheet.feature.annotation.edit.EditRegion
import com.violinsheet.feature.annotation.edit.Mode
import com.violinsheet.feature.annotation.edit.SymbolGroupPanel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The bar keeps this far from each side of the screen; on a narrow screen its slot gives way (design §6). */
private val SCREEN_GUTTER = 16.dp

/**
 * The open Sheet's toolbar wired to its [viewModel]: [BarLayout] with the current mode's region and panel content.
 * [panel] is shared with the screen, whose touches close it (closesToolPanel).
 */
@Composable
fun AnnotationBar(viewModel: AnnotationViewModel, panel: ToolPanelState) {
    val mode by viewModel.mode.collectAsState()
    val tools by viewModel.tools.collectAsState()
    val editTools by viewModel.editTools.collectAsState()
    val saveFailed by viewModel.saveFailed.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    // Only whether there is a selection, so the bar doesn't recompose on every drag event.
    val hasSelection by remember(viewModel) { viewModel.selection.map { it != null }.distinctUntilChanged() }
        .collectAsState(initial = false)
    val hasSymbolSelection by remember(viewModel) { viewModel.symbolSelection.map { it != null }.distinctUntilChanged() }
        .collectAsState(initial = false)
    val canPaste by viewModel.canPaste.collectAsState()
    BarLayout(
        mode,
        viewModel::setMode,
        UndoControls(canUndo, canRedo, viewModel::undo, viewModel::redo),
        panel,
        saveFailed,
        panelContent = { open ->
            when (mode) {
                Mode.Edit -> if (open == Panel.SymbolGroup) SymbolGroupPanel(editTools, viewModel::setEditTools, panel)
                else -> InkPanel(open, panel, tools, viewModel::setTools) { viewModel.setTools(it, save = false) }
            }
        },
    ) {
        when (mode) {
            Mode.Edit -> EditRegion(editTools, viewModel::setEditTools, hasSymbolSelection, viewModel::deleteSelection, panel)
            // Text mode is greyed out until #19, so the bar never shows it; Ink's region stands in.
            else -> InkToolbar(
                tools,
                viewModel::setTools,
                LassoControls(hasSelection, canPaste, viewModel::deleteSelection, viewModel::copySelection, viewModel::paste),
                panel,
            )
        }
    }
}

/**
 * The slim palette at the bottom edge, the same in every mode: the mode switch, Undo and Redo, the [mode]'s [region]
 * (its tool switch and slot, the slot as wide in every mode), then Layers (greyed out until #21). Above it the open
 * panel, its content from [panelContent], and a warning when [saveFailed] (the last change wasn't saved). No dialogs
 * (CLAUDE.md: UI/UX). Changing mode closes the panel.
 */
@Composable
internal fun BarLayout(
    mode: Mode,
    onMode: (Mode) -> Unit,
    undo: UndoControls,
    panel: ToolPanelState,
    saveFailed: Boolean,
    panelContent: @Composable (Panel) -> Unit,
    region: @Composable RowScope.() -> Unit,
) {
    Column(
        Modifier.padding(horizontal = SCREEN_GUTTER),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ToolPanel(panel, panelContent)
        if (saveFailed) Text("Last change not saved", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
        Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 3.dp, shadowElevation = 3.dp) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeSwitch(mode) {
                    panel.close()
                    onMode(it)
                }
                ToolbarDivider()
                IconButton(onClick = undo.onUndo, enabled = undo.canUndo, modifier = Modifier.size(40.dp)) {
                    Icon(UndoArrow, contentDescription = "Undo")
                }
                IconButton(onClick = undo.onRedo, enabled = undo.canRedo, modifier = Modifier.size(40.dp)) {
                    // Redo's arrow is Undo's, mirrored.
                    Icon(UndoArrow, contentDescription = "Redo", modifier = Modifier.graphicsLayer(scaleX = -1f))
                }
                ToolbarDivider()
                region()
                ToolbarDivider()
                IconButton(onClick = {}, enabled = false, modifier = Modifier.size(40.dp)) {
                    Icon(LayersIcon, contentDescription = "Layers")
                }
            }
        }
    }
}

/** Ink | Edit | Text as an outlined segmented control of icons, the current one filled. Text is greyed out until #19. */
@Composable
private fun ModeSwitch(mode: Mode, onMode: (Mode) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(Modifier.clip(shape).border(1.dp, MaterialTheme.colorScheme.outline, shape)) {
        Mode.entries.forEach { segment ->
            val selected = mode == segment
            val enabled = segment != Mode.Text
            Box(
                Modifier.size(width = 44.dp, height = 36.dp)
                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = { if (!selected) onMode(segment) })
                    .clearAndSetSemantics { contentDescription = "${segment.name} mode" }
                    .alpha(if (enabled) 1f else DISABLED_ALPHA),
                contentAlignment = Alignment.Center,
            ) {
                Icon(modeIcon(segment), contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun modeIcon(mode: Mode) = when (mode) {
    Mode.Ink -> InkModeIcon
    Mode.Edit -> EditModeIcon
    Mode.Text -> TextModeIcon
}
