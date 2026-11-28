package com.violinsheet.feature.annotation.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.violinsheet.feature.annotation.CHOICE_SIZE
import com.violinsheet.feature.annotation.Choice
import com.violinsheet.feature.annotation.CoverIcon
import com.violinsheet.feature.annotation.CaretIcon
import com.violinsheet.feature.annotation.DISABLED_ALPHA
import com.violinsheet.feature.annotation.DeleteIcon
import com.violinsheet.feature.annotation.EraserIcon
import com.violinsheet.feature.annotation.Panel
import com.violinsheet.feature.annotation.SWATCH_GAP
import com.violinsheet.feature.annotation.SelectIcon
import com.violinsheet.feature.annotation.SlotFrame
import com.violinsheet.feature.annotation.ToolPanelState
import com.violinsheet.feature.annotation.ToolSlot
import com.violinsheet.feature.annotation.ToolbarDivider

/** Room for Dynamics' eight Symbol buttons (the largest group), so the slot doesn't change with the group. */
private const val MOST_SYMBOLS = 8
private val SYMBOLS_WIDTH = CHOICE_SIZE * MOST_SYMBOLS + SWATCH_GAP * (MOST_SYMBOLS - 1)
private val GROUP_SWITCH_WIDTH = 50.dp

/**
 * Edit mode's region of the bar: the tool switch (Symbol, Select, Eraser, Cover; Cover greyed out until #18), then the
 * chosen tool's slot. The Symbol slot holds the group switch, the group's Symbols as printed icons, and the colour
 * swatch and scale button (greyed out until #14/#15); no Delete (Decision 2). The Select slot holds Delete, enabled
 * with a [hasSelection]; the Eraser slot a hint.
 */
@Composable
internal fun RowScope.EditRegion(
    tools: EditTools,
    onTools: (EditTools) -> Unit,
    hasSelection: Boolean,
    onDelete: () -> Unit,
    panel: ToolPanelState,
) {
    // Switching tool closes the panel (outside the slot, which must hold no effects).
    LaunchedEffect(tools.chosen) { panel.close() }
    val font = rememberSymbolFont()
    EditTool.entries.forEach { tool ->
        Choice(tools.chosen == tool, tool.name, { onTools(tools.copy(chosen = tool)) }, enabled = tool != EditTool.Cover) {
            when (tool) {
                EditTool.Symbol -> SymbolGlyph(SymbolKind.Fingering1.codepoint, SymbolKind.Fingering1.box, font)
                EditTool.Select -> Icon(SelectIcon, contentDescription = null)
                EditTool.Eraser -> Icon(EraserIcon, contentDescription = null)
                EditTool.Cover -> Icon(CoverIcon, contentDescription = null)
            }
        }
    }
    ToolbarDivider()
    SlotFrame {
        ToolSlot(EditTool.entries, tools.chosen) { tool ->
            when (tool) {
                EditTool.Symbol -> SymbolSlot(tools, onTools, panel, font)
                EditTool.Select -> IconButton(onClick = onDelete, enabled = hasSelection, modifier = Modifier.size(CHOICE_SIZE)) {
                    Icon(DeleteIcon, contentDescription = "Delete Symbol")
                }
                EditTool.Eraser -> Text("Touch a Symbol to remove it", style = MaterialTheme.typography.bodyMedium)
                EditTool.Cover -> Unit
            }
        }
    }
}

@Composable
private fun SymbolSlot(tools: EditTools, onTools: (EditTools) -> Unit, panel: ToolPanelState, font: FontFamily) {
    Row(
        Modifier.width(GROUP_SWITCH_WIDTH).height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(role = Role.Button) { panel.toggle(Panel.SymbolGroup) }
            .clearAndSetSemantics { contentDescription = "Choose Symbol group" },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolGroupIcon(tools.group, font)
        Icon(CaretIcon, contentDescription = null, modifier = Modifier.size(16.dp))
    }
    ToolbarDivider()
    Row(Modifier.width(SYMBOLS_WIDTH), horizontalArrangement = Arrangement.spacedBy(SWATCH_GAP)) {
        tools.group.kinds.forEach { kind ->
            Choice(tools.kind == kind, kind.label, { onTools(tools.copy(kind = kind)) }) {
                SymbolGlyph(kind.codepoint, kind.box, font)
            }
        }
    }
    ToolbarDivider()
    Choice(selected = false, label = "Symbol colour", onClick = {}, enabled = false) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Color.Black))
    }
    Choice(selected = false, label = "Symbol scale", onClick = {}, enabled = false) {
        Text("AA", style = MaterialTheme.typography.labelLarge)
    }
}

/** A kind's accessible name: "Fingering 3" for Fingering3. */
private val SymbolKind.label: String get() = name.replace(Regex("(\\D)(\\d)"), "$1 $2")

/**
 * The group panel's content: the six groups as their printed representatives, the current one filled. A group with no
 * kinds yet (all but Fingering in #12) is greyed out. Choosing a group shows its Symbols and closes the panel.
 */
@Composable
internal fun SymbolGroupPanel(tools: EditTools, onTools: (EditTools) -> Unit, panel: ToolPanelState) {
    val font = rememberSymbolFont()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SymbolGroup.entries.forEach { group ->
            val current = tools.group == group
            val enabled = group.kinds.isNotEmpty()
            Box(
                Modifier.size(CHOICE_SIZE)
                    .clip(CircleShape)
                    .background(if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .selectable(selected = current, enabled = enabled, role = Role.RadioButton, onClick = {
                        if (!current) onTools(tools.copy(group = group, kind = group.kinds.first()))
                        panel.close()
                    })
                    .clearAndSetSemantics { contentDescription = "${group.name} group" }
                    .alpha(if (enabled) 1f else DISABLED_ALPHA),
                contentAlignment = Alignment.Center,
            ) { SymbolGroupIcon(group, font) }
        }
    }
}
