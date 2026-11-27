package com.violinsheet.feature.annotation

import com.violinsheet.core.data.settings.PaletteSetting
import com.violinsheet.core.data.settings.RawSettings

/** The retired default-colour keys and the Palette each colour moves into. */
internal const val PEN_COLOUR_KEY = "ink.penColour"
internal const val HIGHLIGHTER_COLOUR_KEY = "ink.highlighterColour"

/** Retired keys deleted as they are: the default Size presets, and the free sizes from before Size presets. */
internal val RETIRED_SIZE_KEYS = listOf("ink.penSizePreset", "ink.highlighterSizePreset", "ink.penSize", "ink.highlighterSize")

/**
 * One-time migration from the stored default colour and size (before #22 part 3): a Sheet now starts on its Palette's
 * first colour and the middle Size preset. A stored default colour that is in its Palette moves to the front, so the
 * pen still starts on it; then the old keys are deleted. Once they are gone this does nothing. Pass it to the
 * SettingsStore's migrations. A failed write throws before the key is deleted, so the next start tries again.
 */
suspend fun retireInkDefaults(raw: RawSettings) {
    moveToFront(raw, PEN_COLOUR_KEY, InkSettings.penPalette)
    moveToFront(raw, HIGHLIGHTER_COLOUR_KEY, InkSettings.highlighterPalette)
    RETIRED_SIZE_KEYS.forEach { raw.remove(it) }
}

private suspend fun moveToFront(raw: RawSettings, key: String, palette: PaletteSetting) {
    val stored = raw.stored(key) ?: return
    val colour = stored.toIntOrNull()
    val colours = raw.read(palette)
    if (colour != null && colour in colours && colour != colours.first()) raw.write(palette, listOf(colour) + (colours - colour))
    raw.remove(key)
}
