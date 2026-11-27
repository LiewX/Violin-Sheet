package com.violinsheet.feature.annotation

import com.violinsheet.core.data.settings.ColourSetting
import com.violinsheet.core.data.settings.SliderSetting
import com.violinsheet.core.data.settings.ToggleSetting

/** A preset colour with the name screen readers announce. */
data class NamedColour(val name: String, val argb: Int)

/** Pen colour presets: black, blue, red, green (SPECS: Ink mode). */
val PEN_COLOURS = listOf(
    NamedColour("Black", 0xFF000000.toInt()),
    NamedColour("Blue", 0xFF1565C0.toInt()),
    NamedColour("Red", 0xFFC62828.toInt()),
    NamedColour("Green", 0xFF2E7D32.toInt()),
)

/** Pen size presets, in points on the Page. */
val PEN_SIZES = listOf(0.75f, 1.5f, 3f)

/**
 * Highlighter colour presets: yellow, green, pink (SPECS: Ink mode). Translucent, so they read as a highlighter even
 * where they can't be multiplied (the stroke being drawn); multiplied onto the Page they tint the paper and leave the
 * notes black.
 */
val HIGHLIGHTER_COLOURS = listOf(
    NamedColour("Yellow", 0x99FFEB3B.toInt()),
    NamedColour("Green", 0x8066BB6A.toInt()),
    NamedColour("Pink", 0x80F06292.toInt()),
)

/** Highlighter size presets, in points on the Page. */
val HIGHLIGHTER_SIZES = listOf(6f, 12f, 20f)

/** Eraser size presets: the eraser circle's diameter in points on the Page. */
val ERASER_SIZES = listOf(4f, 12f, 30f)

/** The eraser size slider's range in points, stepping by 1 (the eraser has no Setting). */
val ERASER_SIZE_RANGE = 2f..40f
const val ERASER_SIZE_STEP = 1f

/** Settings declared by Ink mode. Add one here and it appears on the Settings page. */
object InkSettings {
    val penColour = ColourSetting(
        key = "ink.penColour",
        group = "Ink",
        title = "Pen colour",
        presets = PEN_COLOURS.map { it.argb },
        default = PEN_COLOURS.first().argb,
    )

    val penSize = SliderSetting(
        key = "ink.penSize",
        group = "Ink",
        title = "Pen size",
        range = 0.5f..10f,
        step = 0.25f,
        unit = "pt",
        default = 1.5f,
    )

    val highlighterColour = ColourSetting(
        key = "ink.highlighterColour",
        group = "Ink",
        title = "Highlighter colour",
        presets = HIGHLIGHTER_COLOURS.map { it.argb },
        default = HIGHLIGHTER_COLOURS.first().argb,
    )

    val highlighterSize = SliderSetting(
        key = "ink.highlighterSize",
        group = "Ink",
        title = "Highlighter size",
        range = 4f..30f,
        step = 1f,
        unit = "pt",
        default = 12f,
    )

    /** Off: the lasso selects only strokes wholly inside it, as in Samsung Notes. On: also those mostly inside. */
    val includePartlyEnclosed = ToggleSetting(
        key = "ink.lassoIncludePartlyEnclosed",
        group = "Ink",
        title = "Include partly enclosed strokes",
        default = false,
    )

    val all = listOf(penColour, penSize, highlighterColour, highlighterSize, includePartlyEnclosed)
}
