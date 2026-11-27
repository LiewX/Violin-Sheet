package com.violinsheet.feature.annotation

import com.violinsheet.core.data.settings.Setting
import com.violinsheet.core.data.settings.SettingsStore
import com.violinsheet.core.data.settings.SizePresetsSetting
import com.violinsheet.core.ink.DrawTool
import com.violinsheet.core.ink.StrokeTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The toolbar's choices on one open Sheet. Pen and highlighter start on their Palette's first colour and middle Size preset, and a change of tool,
 * colour or size lasts only while this Sheet is open (SPECS: Settings). The Palettes, Size presets and the lasso's rule
 * are app-wide: a change to them is saved as its Setting. The eraser has no Setting: its mode and size carry over to
 * the next Sheet while the app runs ([eraserMemory]).
 */
internal class InkToolsState(private val settings: SettingsStore, private val eraserMemory: EraserMemory, scope: CoroutineScope) {
    private val _tools = MutableStateFlow(
        InkTools(
            ToolChoice.Pen,
            DrawTool(StrokeTool.Pen, PEN_COLOURS.first().argb, PEN_SIZES[SizePresetsSetting.MEDIUM]),
            DrawTool(StrokeTool.Highlighter, HIGHLIGHTER_COLOURS.first().argb, HIGHLIGHTER_SIZES[SizePresetsSetting.MEDIUM]),
            eraserMemory.eraser,
        ),
    )
    val tools: StateFlow<InkTools> = _tools.asStateFlow()

    /** The app-wide parts as last loaded or saved: a save writes only what differs from it. */
    private var saved = _tools.value

    // What the user changed before the Settings loaded: the load leaves those alone.
    private var penChosen = false
    private var highlighterChosen = false
    private var eraserChosen = false
    private var partlyChosen = false
    private var palettesChosen = false
    private var sizesChosen = false

    init {
        scope.launch {
            val palettes = Palettes(settings.observe(InkSettings.penPalette).first(), settings.observe(InkSettings.highlighterPalette).first())
            val sizes = SizePresets(
                settings.observe(InkSettings.penSizes).first(),
                settings.observe(InkSettings.highlighterSizes).first(),
                settings.observe(InkSettings.eraserSizes).first(),
            )
            // A Sheet starts on the Palette's first colour and the middle Size preset (SPECS: Settings → Ink).
            val pen = DrawTool(StrokeTool.Pen, palettes.pen.first(), sizes.pen[SizePresetsSetting.MEDIUM])
            val highlighter = DrawTool(StrokeTool.Highlighter, palettes.highlighter.first(), sizes.highlighter[SizePresetsSetting.MEDIUM])
            // The eraser's size from the last Sheet, unless its preset has changed since.
            val eraser = eraserMemory.eraser.let { if (it.size in sizes.eraser) it else it.copy(size = sizes.eraser[SizePresetsSetting.MEDIUM]) }
            val partly = settings.observe(InkSettings.includePartlyEnclosed).first()
            saved = saved.copy(
                includePartlyEnclosed = if (partlyChosen) saved.includePartlyEnclosed else partly,
                palettes = if (palettesChosen) saved.palettes else palettes,
                sizes = if (sizesChosen) saved.sizes else sizes,
            )
            _tools.update {
                it.copy(
                    pen = if (penChosen) it.pen else pen,
                    highlighter = if (highlighterChosen) it.highlighter else highlighter,
                    eraser = if (eraserChosen) it.eraser else eraser,
                    includePartlyEnclosed = if (partlyChosen) it.includePartlyEnclosed else partly,
                    palettes = if (palettesChosen) it.palettes else palettes,
                    sizes = if (sizesChosen) it.sizes else sizes,
                )
            }
        }
    }

    /**
     * The toolbar now shows [tools]. With [save] off (a Size preset's slider mid-drag) the change stays in memory; the
     * release saves the settled presets once, so a drag is one database write, not one per step.
     */
    fun set(tools: InkTools, save: Boolean = true) {
        val before = _tools.value
        if (tools.pen != before.pen) penChosen = true
        if (tools.highlighter != before.highlighter) highlighterChosen = true
        if (tools.eraser != before.eraser) eraserChosen = true
        if (tools.includePartlyEnclosed != before.includePartlyEnclosed) partlyChosen = true
        if (tools.palettes != before.palettes) palettesChosen = true
        if (tools.sizes != before.sizes) sizesChosen = true
        eraserMemory.eraser = tools.eraser
        _tools.value = tools
        if (save) save(tools)
    }

    private fun save(tools: InkTools) {
        val old = saved
        fun <T> write(setting: Setting<T>, value: (InkTools) -> T) {
            if (value(tools) != value(old)) settings.set(setting, value(tools))
        }
        write(InkSettings.includePartlyEnclosed) { it.includePartlyEnclosed }
        write(InkSettings.penPalette) { it.palettes.pen }
        write(InkSettings.highlighterPalette) { it.palettes.highlighter }
        write(InkSettings.penSizes) { it.sizes.pen }
        write(InkSettings.highlighterSizes) { it.sizes.highlighter }
        write(InkSettings.eraserSizes) { it.sizes.eraser }
        saved = tools
    }
}
