package com.violinsheet.feature.annotation

import com.violinsheet.core.ink.DrawTool
import com.violinsheet.core.ink.EraseMode
import com.violinsheet.core.ink.Eraser
import com.violinsheet.core.ink.StrokeTool
import kotlin.test.Test
import kotlin.test.assertEquals

class InkToolsTest {
    private val black = 0xFF000000.toInt()
    private val blue = 0xFF1565C0.toInt()
    private val red = 0xFFC62828.toInt()
    private val brown = 0xFF6D4C41.toInt()

    private fun tools(chosen: ToolChoice = ToolChoice.Pen) = InkTools(
        chosen,
        DrawTool(StrokeTool.Pen, blue, 1.5f),
        DrawTool(StrokeTool.Highlighter, HIGHLIGHTER_COLOURS[0].argb, 12f),
        Eraser(EraseMode.Stroke, 12f),
        palettes = Palettes(pen = listOf(black, blue, red)),
        sizes = SizePresets(pen = listOf(0.75f, 1.5f, 3f)),
    )

    @Test fun addedColourGoesLastAndIsSelected() {
        val added = tools().withColourAdded(brown)
        assertEquals(listOf(black, blue, red, brown), added.palettes.pen)
        assertEquals(brown, added.pen.colour)
    }

    @Test fun addingAColourAlreadyInThePaletteSelectsItInstead() {
        val added = tools().withColourAdded(red)
        assertEquals(listOf(black, blue, red), added.palettes.pen)
        assertEquals(red, added.pen.colour)
    }

    @Test fun addingWorksOnTheChosenToolsPalette() {
        val added = tools(ToolChoice.Highlighter).withColourAdded(0x80123456.toInt())
        assertEquals(HIGHLIGHTER_COLOURS.map { it.argb } + 0x80123456.toInt(), added.palettes.highlighter)
        assertEquals(0x80123456.toInt(), added.highlighter.colour)
        assertEquals(listOf(black, blue, red), added.palettes.pen)
    }

    @Test fun editedColourReplacesItsSwatchInPlaceAndStaysSelected() {
        val edited = tools().withColourReplaced(blue, brown)
        assertEquals(listOf(black, brown, red), edited.palettes.pen)
        assertEquals(brown, edited.pen.colour)
    }

    @Test fun editingAColourToMatchAnotherMergesThemIntoThatSwatch() {
        val edited = tools().withColourReplaced(blue, red)
        assertEquals(listOf(black, red), edited.palettes.pen)
        assertEquals(red, edited.pen.colour)
    }

    @Test fun savingAnUnchangedColourKeepsIt() {
        assertEquals(tools(), tools().withColourReplaced(blue, blue))
        val one = tools().copy(palettes = Palettes(pen = listOf(blue)))
        assertEquals(one, one.withColourReplaced(blue, blue))
    }

    @Test fun savingFromTheSelectedColourEditsItAndNeverAddsOne() {
        val saved = tools().withColourSaved(Panel.EditColour(blue), brown)
        assertEquals(listOf(black, brown, red), saved.palettes.pen)
        assertEquals(brown, saved.pen.colour)
    }

    @Test fun savingFromAddColourAddsIt() {
        assertEquals(listOf(black, blue, red, brown), tools().withColourSaved(Panel.AddColour, brown).palettes.pen)
    }

    @Test fun tappingTheSelectedColourOpensItsEditPanelNotAnAddOne() {
        val panel = ToolPanelState()
        panel.toggle(Panel.EditColour(blue), blue)
        assertEquals(Panel.EditColour(blue), panel.open)
        assertEquals(blue, panel.draft)
        panel.toggle(Panel.EditColour(blue), blue) // tapped again: closes
        assertEquals(null, panel.open)
    }

    @Test fun editingAPresetMovesItAndTheSizeLiveWithoutSorting() {
        val edited = tools().withPreset(1, 0.5f)
        assertEquals(listOf(0.75f, 0.5f, 3f), edited.sizes.pen)
        assertEquals(0.5f, edited.pen.size)
    }

    @Test fun releasedPresetsReSortAndTheSelectionFollowsTheEditedOne() {
        val (sorted, index) = tools().withPreset(1, 0.5f).withPresetsSorted(1, from = 1.5f)
        assertEquals(listOf(0.5f, 0.75f, 3f), sorted.sizes.pen)
        assertEquals(0, index)
        assertEquals(0.5f, sorted.pen.size)
    }

    @Test fun aPresetLetGoOnAnotherIsNudgedOneStepBackSoTheThreeStayApart() {
        // Small (0.75) dragged up onto large (3): one pen step (0.25) back down, and it is now the middle one.
        val (sorted, index) = tools().withPreset(0, 3f).withPresetsSorted(0, from = 0.75f)
        assertEquals(listOf(1.5f, 2.75f, 3f), sorted.sizes.pen)
        assertEquals(1, index)
        assertEquals(2.75f, sorted.pen.size)
    }

    @Test fun eraserPresetsAreEditedLikeTheOthers() {
        val (sorted, index) = tools(ToolChoice.Eraser).withPreset(2, 2f).withPresetsSorted(2, from = 30f)
        assertEquals(listOf(2f, 4f, 12f), sorted.sizes.eraser)
        assertEquals(0, index)
        assertEquals(2f, sorted.eraser.size)
    }
}
