package com.violinsheet.feature.annotation

import com.violinsheet.core.data.settings.ColourSetting
import com.violinsheet.core.data.settings.SliderSetting

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

    val all = listOf(penColour, penSize)
}
