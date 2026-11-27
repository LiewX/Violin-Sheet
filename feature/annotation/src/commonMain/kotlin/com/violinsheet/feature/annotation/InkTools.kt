package com.violinsheet.feature.annotation

import com.violinsheet.core.ink.DrawTool
import com.violinsheet.core.ink.Eraser
import com.violinsheet.core.ink.InkTool
import com.violinsheet.core.ink.Lasso
import com.violinsheet.feature.settings.PaletteEdit

/** The Ink mode tools in the toolbar. */
enum class ToolChoice { Pen, Highlighter, Eraser, Lasso }

/** The app-wide pen and highlighter Palettes (InkSettings), ARGB in toolbar order. */
data class Palettes(
    val pen: List<Int> = InkSettings.penPalette.default,
    val highlighter: List<Int> = InkSettings.highlighterPalette.default,
)

/** The app-wide Size presets per tool (InkSettings), in points on the Page; small to large except while one is dragged. */
data class SizePresets(
    val pen: List<Float> = InkSettings.penSizes.default,
    val highlighter: List<Float> = InkSettings.highlighterSizes.default,
    val eraser: List<Float> = InkSettings.eraserSizes.default,
)

/**
 * Which tool the toolbar has chosen, and each tool's current colour, size and mode. [includePartlyEnclosed] is the
 * lasso's rule (InkSettings.includePartlyEnclosed). [palettes] and [sizes] are what the colours and sizes are picked
 * from; every colour and size drawn with is one of them.
 */
data class InkTools(
    val chosen: ToolChoice,
    val pen: DrawTool,
    val highlighter: DrawTool,
    val eraser: Eraser,
    val includePartlyEnclosed: Boolean = false,
    val palettes: Palettes = Palettes(),
    val sizes: SizePresets = SizePresets(),
) {
    val current: InkTool
        get() = when (chosen) {
            ToolChoice.Pen -> pen
            ToolChoice.Highlighter -> highlighter
            ToolChoice.Eraser -> eraser
            ToolChoice.Lasso -> Lasso
        }

    /** The chosen tool's Size presets; empty for the Lasso. */
    val chosenSizes: List<Float>
        get() = when (chosen) {
            ToolChoice.Pen -> sizes.pen
            ToolChoice.Highlighter -> sizes.highlighter
            ToolChoice.Eraser -> sizes.eraser
            ToolChoice.Lasso -> emptyList()
        }
}

/** [colour] added to the end of the chosen tool's Palette (or found there: one swatch per colour), and selected. */
fun InkTools.withColourAdded(colour: Int): InkTools = withPalette(colour) { PaletteEdit(it).added(colour).colours }

/**
 * The chosen tool's Palette colour [old] replaced in its place by [new], app-wide, and selected. If [new] is already
 * another swatch, the two merge into that one (PaletteEdit.replaced). Strokes keep their own colour: nothing here touches them.
 */
fun InkTools.withColourReplaced(old: Int, new: Int): InkTools = withPalette(new) { PaletteEdit(it).replaced(old, new).colours }

private fun InkTools.withPalette(selected: Int, change: (List<Int>) -> List<Int>): InkTools = when (chosen) {
    ToolChoice.Pen -> copy(palettes = palettes.copy(pen = change(palettes.pen)), pen = pen.copy(colour = selected))
    ToolChoice.Highlighter ->
        copy(palettes = palettes.copy(highlighter = change(palettes.highlighter)), highlighter = highlighter.copy(colour = selected))
    ToolChoice.Eraser, ToolChoice.Lasso -> this
}

/** The chosen tool's Size preset [index] set to [size], and drawn with, while the slider moves: not re-sorted yet. */
fun InkTools.withPreset(index: Int, size: Float): InkTools = withSizes(size) { it.toMutableList().apply { set(index, size) } }

/**
 * The chosen tool's Size presets settled once the slider is let go, and where preset [index] went: if it landed on
 * another preset it is nudged back towards [from], where its drag started, then the three sort small to large
 * (SizePresetsSetting.settle, shared with the Settings page).
 */
fun InkTools.withPresetsSorted(index: Int, from: Float): Pair<InkTools, Int> {
    val settled = sizesSetting(chosen)?.settle(chosenSizes, index, from) ?: return this to index
    val moved = settled.indexOf(index)
    return withSizes(settled.sizes[moved]) { settled.sizes } to moved
}

private fun InkTools.withSizes(selected: Float, change: (List<Float>) -> List<Float>): InkTools = when (chosen) {
    ToolChoice.Pen -> copy(sizes = sizes.copy(pen = change(sizes.pen)), pen = pen.copy(size = selected))
    ToolChoice.Highlighter -> copy(sizes = sizes.copy(highlighter = change(sizes.highlighter)), highlighter = highlighter.copy(size = selected))
    ToolChoice.Eraser -> copy(sizes = sizes.copy(eraser = change(sizes.eraser)), eraser = eraser.copy(size = selected))
    ToolChoice.Lasso -> this
}
