package com.violinsheet.feature.annotation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.violinsheet.core.data.settings.SizePresetsSetting
import com.violinsheet.feature.settings.ColourPicker
import com.violinsheet.feature.settings.StepSlider
import com.violinsheet.feature.settings.SwitchRow
import com.violinsheet.feature.settings.formatPoints

/**
 * The panel above the toolbar, never a dialog, showing the open [Panel] through [content] (the mode's panel content:
 * [InkPanel], or Edit mode's group list). Nothing while no panel is open.
 */
@Composable
internal fun ToolPanel(panel: ToolPanelState, content: @Composable (Panel) -> Unit) {
    val open = panel.open ?: return
    Surface(
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        modifier = Modifier.onGloballyPositioned { panel.bounds = it.boundsInRoot() },
    ) {
        // As wide as its content: the colour picker sets it; the slider and the switch are given PanelWidth.
        Column(Modifier.padding(16.dp).width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content(open)
        }
    }
}

/** Ink mode's panel content: the colour picker, a Size preset's slider, or the lasso's rule. */
@Composable
internal fun InkPanel(open: Panel, panel: ToolPanelState, tools: InkTools, onTools: (InkTools) -> Unit, onToolsLive: (InkTools) -> Unit) {
    when (open) {
        Panel.Lasso -> SwitchRow(InkSettings.includePartlyEnclosed.title, tools.includePartlyEnclosed, modifier = Modifier.width(PanelWidth)) {
            onTools(tools.copy(includePartlyEnclosed = it))
        }
        Panel.AddColour, is Panel.EditColour -> ColourPanel(panel, open, tools, onTools)
        is Panel.EditPreset -> PresetPanel(panel, open.index, tools, onTools, onToolsLive)
        Panel.SymbolGroup -> Unit // Edit mode's; switching mode closes the panel.
    }
}

/** The size slider's and the lasso switch's panel width. */
private val PanelWidth = 320.dp

@Composable
private fun PanelLabel(text: String) =
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

@Composable
private fun ColourPanel(panel: ToolPanelState, open: Panel, tools: InkTools, onTools: (InkTools) -> Unit) {
    val factory = factoryColours(tools.chosen)
    PanelLabel(if (open is Panel.EditColour) "Editing: ${colourName(open.argb, factory).lowercase()}" else "Add ${tools.chosen.name.lowercase()} colour")
    ColourPicker(panel.draft, { panel.draft = it })
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Factory", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(54.dp))
        factory.forEach { (name, argb) ->
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(Color(argb))
                    .selectable(selected = panel.draft == argb, onClick = { panel.draft = argb })
                    .semantics { contentDescription = "Factory $name" },
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        TextButton(onClick = panel::close) { Text("Cancel") }
        Button(onClick = {
            onTools(tools.withColourSaved(open, panel.draft))
            panel.close()
        }) { Text("Save") }
    }
}

@Composable
private fun PresetPanel(panel: ToolPanelState, index: Int, tools: InkTools, onTools: (InkTools) -> Unit, onToolsLive: (InkTools) -> Unit) {
    val setting = sizesSetting(tools.chosen) ?: return
    val size = tools.chosenSizes[index]
    PanelLabel("Editing: ${SizePresetsSetting.NAMES[index]} preset")
    // The slider let go, or the panel closed mid-drag (a touch outside, a tool switch): the three re-sort.
    val chosen = tools.chosen
    val latestTools by rememberUpdatedState(tools)
    val latestIndex by rememberUpdatedState(index)
    // The preset's size when the drag began, so a release on another preset is nudged back towards it.
    var dragFrom by remember { mutableStateOf<Float?>(null) }
    val finish = finish@{
        val from = dragFrom ?: return@finish
        dragFrom = null
        val edited = latestTools.copy(chosen = chosen)
        val (sorted, moved) = edited.withPresetsSorted(latestIndex, from)
        onTools(sorted.copy(chosen = latestTools.chosen))
        panel.follow(Panel.EditPreset(moved))
    }
    DisposableEffect(Unit) { onDispose { finish() } }
    Row(Modifier.width(PanelWidth), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        StepSlider(
            size,
            setting.range,
            setting.step,
            onValueChange = {
                if (dragFrom == null) dragFrom = size
                onToolsLive(tools.withPreset(index, it))
            },
            modifier = Modifier.weight(1f).semantics { contentDescription = "Size" },
            onValueChangeFinished = finish,
        )
        Text("${formatPoints(size)} ${setting.unit}", style = MaterialTheme.typography.bodyMedium)
    }
}
