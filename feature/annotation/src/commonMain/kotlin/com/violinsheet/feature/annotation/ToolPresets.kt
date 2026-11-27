package com.violinsheet.feature.annotation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.violinsheet.core.data.settings.SizePresetsSetting
import com.violinsheet.core.ink.DrawTool
import com.violinsheet.core.ink.EraseMode
import com.violinsheet.core.ink.Eraser
import com.violinsheet.feature.settings.formatPoints
import com.violinsheet.feature.settings.hexName

/** A toolbar choice is this wide; the palette scroller shows [VISIBLE_SWATCHES] of them, [SWATCH_GAP] apart. */
internal val CHOICE_SIZE = 40.dp
internal val SWATCH_GAP = 4.dp
private const val VISIBLE_SWATCHES = 5

/** Fixed, so the toolbar keeps one width however many colours the Palette holds (ToolSlot). */
internal val PALETTE_WIDTH = CHOICE_SIZE * VISIBLE_SWATCHES + SWATCH_GAP * (VISIBLE_SWATCHES - 1)
private val EDGE_FADE = 20.dp

internal fun factoryColours(chosen: ToolChoice) = if (chosen == ToolChoice.Highlighter) HIGHLIGHTER_COLOURS else PEN_COLOURS

/** The factory name of [argb] ("Red"), or its hex code ("#6D4C41") for a colour the user made. */
internal fun colourName(argb: Int, factory: List<NamedColour>): String =
    factory.firstOrNull { it.argb == argb }?.name ?: hexName(argb)

internal fun sizesSetting(chosen: ToolChoice): SizePresetsSetting? = when (chosen) {
    ToolChoice.Pen -> InkSettings.penSizes
    ToolChoice.Highlighter -> InkSettings.highlighterSizes
    ToolChoice.Eraser -> InkSettings.eraserSizes
    ToolChoice.Lasso -> null
}

/** Dot sizes for the small, medium and large preset, so the presets read at a glance whatever their sizes. */
private val PRESET_DOTS = listOf(8.dp, 14.dp, 22.dp)

/**
 * The pen's or highlighter's Palette (scrolling sideways past about five, edges fading), + Add colour, then its three
 * Size presets. Tapping the selected colour or preset opens the panel to edit it.
 */
@Composable
internal fun DrawPresets(
    tool: DrawTool,
    chosen: ToolChoice,
    palette: List<Int>,
    sizes: List<Float>,
    scroll: ScrollState,
    panel: ToolPanelState,
    onTool: (DrawTool) -> Unit,
) {
    val name = chosen.name.lowercase()
    val factory = factoryColours(chosen)
    Box(Modifier.width(PALETTE_WIDTH).fadedEdges(scroll)) {
        Row(Modifier.horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(SWATCH_GAP)) {
            palette.forEach { colour ->
                val selected = tool.colour == colour
                val onClick = if (selected) {
                    { panel.toggle(Panel.EditColour(colour), colour) }
                } else {
                    { onTool(tool.copy(colour = colour)) }
                }
                Choice(selected, "${colourName(colour, factory)} $name", onClick) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(Color(colour)))
                }
            }
        }
    }
    Choice(selected = panel.open == Panel.AddColour, label = "Add $name colour", onClick = { panel.toggle(Panel.AddColour, tool.colour) }) {
        Box(Modifier.size(24.dp).border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
            Text("+", style = MaterialTheme.typography.titleMedium)
        }
    }
    ToolbarDivider()
    SizeChoices(name.replaceFirstChar { it.uppercase() }, sizes, tool.size, panel, { onTool(tool.copy(size = it)) }) {
        Modifier.background(Color(tool.colour))
    }
}

@Composable
internal fun EraserPresets(eraser: Eraser, sizes: List<Float>, panel: ToolPanelState, onEraser: (Eraser) -> Unit) {
    val ring = MaterialTheme.colorScheme.onSurface
    SizeChoices("Eraser", sizes, eraser.size, panel, { onEraser(eraser.copy(size = it)) }) { Modifier.border(1.5.dp, ring, CircleShape) }
    ToolbarDivider()
    EraseMode.entries.forEach { mode ->
        val selected = eraser.mode == mode
        Box(
            Modifier.height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .selectable(selected = selected, role = Role.RadioButton, onClick = { onEraser(eraser.copy(mode = mode)) })
                .clearAndSetSemantics { contentDescription = "${mode.name} eraser" }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text(mode.name, style = MaterialTheme.typography.labelLarge) }
    }
}

/** The three Size presets as dots of [dot]'s look; tapping the selected one opens its slider. */
@Composable
private fun SizeChoices(title: String, sizes: List<Float>, size: Float, panel: ToolPanelState, onSize: (Float) -> Unit, dot: () -> Modifier) {
    sizes.forEachIndexed { i, preset ->
        val selected = size == preset
        val onClick = if (selected) {
            { panel.toggle(Panel.EditPreset(i)) }
        } else {
            { onSize(preset) }
        }
        Choice(selected, "$title size ${formatPoints(preset)} pt", onClick) {
            Box(Modifier.size(PRESET_DOTS[i]).clip(CircleShape).then(dot()))
        }
    }
}

/** Fades the content out towards an edge that has more to scroll to. */
private fun Modifier.fadedEdges(scroll: ScrollState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val fade = EDGE_FADE.toPx()
        if (scroll.canScrollBackward) {
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), endX = fade), size = Size(fade, size.height), blendMode = BlendMode.DstIn)
        }
        if (scroll.canScrollForward) {
            val start = size.width - fade
            drawRect(
                Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = start, endX = size.width),
                topLeft = Offset(start, 0f),
                size = Size(fade, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }
