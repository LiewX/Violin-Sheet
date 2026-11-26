package com.violinsheet.feature.annotation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.violinsheet.core.ink.DrawTool
import com.violinsheet.core.ink.EraseMode
import com.violinsheet.core.ink.Eraser

/** What the toolbar's Undo and Redo buttons do, and whether each has anything to do. */
class UndoControls(val canUndo: Boolean, val canRedo: Boolean, val onUndo: () -> Unit, val onRedo: () -> Unit)

/**
 * The slim palette at the bottom edge: Undo and Redo, the tool switch (Pen, Highlighter, Eraser), then the chosen
 * tool's presets. No dialogs (CLAUDE.md: UI/UX). [saveFailed] shows a warning that the last change wasn't saved.
 */
@Composable
fun InkToolbar(tools: InkTools, onTools: (InkTools) -> Unit, undo: UndoControls, saveFailed: Boolean = false) {
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
            if (saveFailed) Text("Last change not saved", color = MaterialTheme.colorScheme.error)
            ToolChoice.entries.forEach { choice ->
                Choice(selected = tools.chosen == choice, label = choice.name, onClick = { onTools(tools.copy(chosen = choice)) }) {
                    Icon(toolIcon(choice), contentDescription = null)
                }
            }
            ToolbarDivider()
            when (tools.chosen) {
                ToolChoice.Pen -> DrawPresets(tools.pen, "pen", PEN_COLOURS, PEN_SIZES) { onTools(tools.copy(pen = it)) }
                ToolChoice.Highlighter ->
                    DrawPresets(tools.highlighter, "highlighter", HIGHLIGHTER_COLOURS, HIGHLIGHTER_SIZES) { onTools(tools.copy(highlighter = it)) }
                ToolChoice.Eraser -> EraserPresets(tools.eraser) { onTools(tools.copy(eraser = it)) }
            }
        }
    }
}

/** Dot sizes for the small, medium and large preset, so the presets read at a glance whatever their sizes. */
private val PRESET_DOTS = listOf(8.dp, 14.dp, 22.dp)

private fun presetDot(i: Int) = PRESET_DOTS.getOrElse(i) { PRESET_DOTS.last() }

@Composable
private fun DrawPresets(tool: DrawTool, name: String, colours: List<NamedColour>, sizes: List<Float>, onTool: (DrawTool) -> Unit) {
    colours.forEach { (colourName, colour) ->
        Choice(selected = tool.colour == colour, label = "$colourName $name", onClick = { onTool(tool.copy(colour = colour)) }) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(Color(colour)))
        }
    }
    ToolbarDivider()
    val title = name.replaceFirstChar { it.uppercase() }
    sizes.forEachIndexed { i, size ->
        Choice(selected = tool.size == size, label = "$title size ${size.label()} pt", onClick = { onTool(tool.copy(size = size)) }) {
            Box(Modifier.size(presetDot(i)).clip(CircleShape).background(Color(tool.colour)))
        }
    }
}

@Composable
private fun EraserPresets(eraser: Eraser, onEraser: (Eraser) -> Unit) {
    ERASER_SIZES.forEachIndexed { i, size ->
        Choice(selected = eraser.size == size, label = "Eraser size ${size.label()} pt", onClick = { onEraser(eraser.copy(size = size)) }) {
            Box(Modifier.size(presetDot(i)).border(1.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape))
        }
    }
    ToolbarDivider()
    EraseMode.entries.forEach { mode ->
        val selected = eraser.mode == mode
        Box(
            Modifier.height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .selectable(selected = selected, onClick = { onEraser(eraser.copy(mode = mode)) })
                .semantics { contentDescription = "${mode.name} eraser" }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text(mode.name, style = MaterialTheme.typography.labelLarge) }
    }
}

/** 0.75 → "0.75", 12.0 → "12". */
private fun Float.label() = if (this % 1f == 0f) toInt().toString() else toString()

@Composable
private fun ToolbarDivider() = VerticalDivider(Modifier.padding(horizontal = 4.dp).size(width = 1.dp, height = 28.dp))

// The Material "undo" arrow. Built here because material-icons-core has none, and material-icons-extended is large.
private val UndoArrow = ImageVector.Builder("Undo", 24.dp, 24.dp, 24f, 24f).addPath(
    addPathNodes(
        "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 " +
            "3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z",
    ),
    fill = SolidColor(Color.Black),
).build()

@Composable
private fun Choice(selected: Boolean, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(40.dp)
            .clip(CircleShape)
            .border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
            .selectable(selected = selected, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun toolIcon(choice: ToolChoice) = when (choice) {
    ToolChoice.Pen -> PenIcon
    ToolChoice.Highlighter -> HighlighterIcon
    ToolChoice.Eraser -> EraserIcon
}

private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(addPathNodes(path), fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd)
    .build()

// Material "edit".
private val PenIcon = icon(
    "Pen",
    "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34" +
        "c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z",
)

// A chisel-tip marker.
private val HighlighterIcon = icon(
    "Highlighter",
    "M18.5,1.15C17.97,1.15 17.46,1.34 17.07,1.73L11.26,7.55L16.91,13.2L22.73,7.39C23.5,6.61 23.5,5.35 22.73,4.56" +
        "L19.89,1.73C19.5,1.34 19,1.15 18.5,1.15ZM10.3,8.5L4.34,14.46C3.56,15.24 3.56,16.5 4.36,17.31L0.67,21H6.33" +
        "L7.19,20.14C7.97,20.9 9.22,20.89 10,20.12L15.95,14.16Z",
)

// A block eraser, its rubber end hollow.
private val EraserIcon = icon(
    "Eraser",
    "M16.24,3.56L21.19,8.5C21.97,9.29 21.97,10.55 21.19,11.34L12,20.53C10.44,22.09 7.91,22.09 6.34,20.53L2.81,17" +
        "C2.03,16.22 2.03,14.95 2.81,14.17L13.41,3.56C14.2,2.78 15.46,2.78 16.24,3.56ZM4.22,15.58L7.76,19.11" +
        "C8.54,19.9 9.8,19.9 10.59,19.11L14.12,15.58L9.17,10.63L4.22,15.58Z",
)
