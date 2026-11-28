package com.violinsheet.feature.annotation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/** What the toolbar's Undo and Redo buttons do, and whether each has anything to do. */
class UndoControls(val canUndo: Boolean, val canRedo: Boolean, val onUndo: () -> Unit, val onRedo: () -> Unit)

/** What the Lasso tool's buttons do: Delete and Copy need a selection, Paste something copied. */
data class LassoControls(
    val hasSelection: Boolean,
    val canPaste: Boolean,
    val onDelete: () -> Unit,
    val onCopy: () -> Unit,
    val onPaste: () -> Unit,
)

/**
 * Ink mode's region of the bar ([AnnotationBar]): the tool switch (Pen, Highlighter, Eraser, Lasso), then the chosen
 * tool's Palette and Size presets (the Lasso's actions) in the slot. Its panel content is [InkPanel].
 */
@Composable
internal fun RowScope.InkToolbar(tools: InkTools, onTools: (InkTools) -> Unit, lasso: LassoControls, panel: ToolPanelState) {
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
    SlotFrame {
        ToolSlot(ToolChoice.entries, tools.chosen) { choice ->
            when (choice) {
                ToolChoice.Pen ->
                    DrawPresets(tools.pen, choice, tools.palettes.pen, tools.sizes.pen, penScroll, panel) { onTools(tools.copy(pen = it)) }
                ToolChoice.Highlighter -> DrawPresets(
                    tools.highlighter, choice, tools.palettes.highlighter, tools.sizes.highlighter, highlighterScroll, panel,
                ) { onTools(tools.copy(highlighter = it)) }
                ToolChoice.Eraser -> EraserPresets(tools.eraser, tools.sizes.eraser, panel) { onTools(tools.copy(eraser = it)) }
                ToolChoice.Lasso -> {
                    IconButton(onClick = lasso.onDelete, enabled = lasso.hasSelection, modifier = Modifier.size(40.dp)) {
                        Icon(DeleteIcon, contentDescription = "Delete selection")
                    }
                    IconButton(onClick = lasso.onCopy, enabled = lasso.hasSelection, modifier = Modifier.size(40.dp)) {
                        Icon(CopyIcon, contentDescription = "Copy selection")
                    }
                    IconButton(onClick = lasso.onPaste, enabled = lasso.canPaste, modifier = Modifier.size(40.dp)) {
                        Icon(PasteIcon, contentDescription = "Paste")
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

/**
 * A round toolbar choice, ringed when [selected]. Greyed out and inert when not [enabled] (an unbuilt control,
 * design Decision 1). The [label] is its only accessible text, on the same node as its checked state.
 */
@Composable
internal fun Choice(selected: Boolean, label: String, onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    Box(
        Modifier.size(CHOICE_SIZE)
            .clip(CircleShape)
            .border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics { contentDescription = label }
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Material's opacity for disabled content. */
internal const val DISABLED_ALPHA = 0.38f
