package com.violinsheet.feature.annotation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/** What the toolbar's Undo and Redo buttons do, and whether each has anything to do. */
class UndoControls(val canUndo: Boolean, val canRedo: Boolean, val onUndo: () -> Unit, val onRedo: () -> Unit)

/** What the Lasso tool's buttons do, and whether there is a selection for them to act on. */
data class LassoControls(val hasSelection: Boolean, val onDelete: () -> Unit)

/**
 * The slim palette at the bottom edge: Undo and Redo, the tool switch (Pen, Highlighter, Eraser, Lasso), then the chosen
 * tool's Palette and Size presets (the Lasso's actions), with [panel] above it. No dialogs (CLAUDE.md: UI/UX).
 * [onToolsLive] takes a Size preset slider's steps mid-drag, which are not saved yet. [saveFailed] shows a warning that the
 * last change wasn't saved.
 */
@Composable
fun InkToolbar(
    tools: InkTools,
    onTools: (InkTools) -> Unit,
    onToolsLive: (InkTools) -> Unit,
    undo: UndoControls,
    lasso: LassoControls,
    panel: ToolPanelState,
    saveFailed: Boolean = false,
) {
    // Switching tool closes the panel, unsaved colour and all.
    LaunchedEffect(tools.chosen) { panel.close() }
    val penScroll = rememberScrollState()
    val highlighterScroll = rememberScrollState()
    val density = LocalDensity.current
    // Outside ToolSlot, which must hold no effects: the selected colour (one just added goes last) is scrolled into view.
    LaunchedEffect(tools.palettes.pen, tools.pen.colour) { penScroll.reveal(tools.palettes.pen.indexOf(tools.pen.colour), density) }
    LaunchedEffect(tools.palettes.highlighter, tools.highlighter.colour) {
        highlighterScroll.reveal(tools.palettes.highlighter.indexOf(tools.highlighter.colour), density)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolPanel(panel, tools, onTools, onToolsLive)
        if (saveFailed) Text("Last change not saved", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
        Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 3.dp, shadowElevation = 3.dp) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = undo.onUndo, enabled = undo.canUndo, modifier = Modifier.size(40.dp)) {
                    Icon(UndoArrow, contentDescription = "Undo")
                }
                IconButton(onClick = undo.onRedo, enabled = undo.canRedo, modifier = Modifier.size(40.dp)) {
                    // Redo's arrow is Undo's, mirrored.
                    Icon(UndoArrow, contentDescription = "Redo", modifier = Modifier.graphicsLayer(scaleX = -1f))
                }
                ToolbarDivider()
                ToolChoice.entries.forEach { choice ->
                    // Tapping the Lasso again opens its panel (the selection rule); it has no colour or size.
                    val onClick = if (choice == ToolChoice.Lasso && tools.chosen == ToolChoice.Lasso) {
                        { panel.toggle(Panel.Lasso) }
                    } else {
                        { onTools(tools.copy(chosen = choice)) }
                    }
                    Choice(selected = tools.chosen == choice, label = choice.name, onClick = onClick) {
                        Icon(toolIcon(choice), contentDescription = null)
                    }
                }
                ToolbarDivider()
                ToolSlot(tools.chosen) { choice ->
                    when (choice) {
                        ToolChoice.Pen ->
                            DrawPresets(tools.pen, choice, tools.palettes.pen, tools.sizes.pen, penScroll, panel) { onTools(tools.copy(pen = it)) }
                        ToolChoice.Highlighter -> DrawPresets(
                            tools.highlighter, choice, tools.palettes.highlighter, tools.sizes.highlighter, highlighterScroll, panel,
                        ) { onTools(tools.copy(highlighter = it)) }
                        ToolChoice.Eraser -> EraserPresets(tools.eraser, tools.sizes.eraser, panel) { onTools(tools.copy(eraser = it)) }
                        ToolChoice.Lasso -> IconButton(onClick = lasso.onDelete, enabled = lasso.hasSelection, modifier = Modifier.size(40.dp)) {
                            Icon(DeleteIcon, contentDescription = "Delete selection")
                        }
                    }
                }
            }
        }
    }
}

/** Scrolls the palette just enough to show swatch [index] whole; nothing if it is already shown, or not there. */
private suspend fun ScrollState.reveal(index: Int, density: Density) {
    if (index < 0) return
    val (start, end, width) = with(density) {
        val start = ((CHOICE_SIZE + SWATCH_GAP) * index).roundToPx()
        Triple(start, start + CHOICE_SIZE.roundToPx(), PALETTE_WIDTH.roundToPx())
    }
    when {
        start < value -> animateScrollTo(start)
        end > value + width -> animateScrollTo(end - width)
    }
}

@Composable
internal fun ToolbarDivider() = VerticalDivider(Modifier.padding(horizontal = 4.dp).size(width = 1.dp, height = 28.dp))

@Composable
internal fun Choice(selected: Boolean, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(CHOICE_SIZE)
            .clip(CircleShape)
            .border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}
