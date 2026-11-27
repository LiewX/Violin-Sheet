package com.violinsheet.feature.annotation

import com.violinsheet.core.data.settings.PaletteSetting
import com.violinsheet.core.data.settings.SizePresetsSetting
import com.violinsheet.core.data.settings.SizePresetsSetting.SizePreview
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

/**
 * Settings declared by Ink mode. Add one here and it appears on the Settings page. A Sheet starts on each Palette's
 * first colour and the middle Size preset: there is no stored default colour or size.
 */
object InkSettings {
    /** Off: the lasso selects only strokes wholly inside it, as in Samsung Notes. On: also those mostly inside. */
    val includePartlyEnclosed = ToggleSetting(
        key = "ink.lassoIncludePartlyEnclosed",
        group = "Ink",
        title = "Include partly enclosed strokes",
        default = false,
    )

    val penPalette = PaletteSetting("ink.penPalette", "Ink", "Pen palette", PEN_COLOURS.map { it.argb })

    val highlighterPalette = PaletteSetting("ink.highlighterPalette", "Ink", "Highlighter palette", HIGHLIGHTER_COLOURS.map { it.argb })

    val penSizes = SizePresetsSetting("ink.penSizes", "Ink", "Pen sizes", 0.5f..10f, 0.25f, "pt", PEN_SIZES, SizePreview.Line)

    val highlighterSizes = SizePresetsSetting("ink.highlighterSizes", "Ink", "Highlighter sizes", 4f..30f, 1f, "pt", HIGHLIGHTER_SIZES, SizePreview.Band)

    val eraserSizes = SizePresetsSetting("ink.eraserSizes", "Ink", "Eraser sizes", 2f..40f, 1f, "pt", ERASER_SIZES, SizePreview.Circle)

    /** In page order (SettingsInk mockup). */
    val all = listOf(penPalette, highlighterPalette, penSizes, highlighterSizes, eraserSizes, includePartlyEnclosed)
}
