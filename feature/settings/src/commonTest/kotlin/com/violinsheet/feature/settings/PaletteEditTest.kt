package com.violinsheet.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class PaletteEditTest {
    private val black = 0xFF000000.toInt()
    private val blue = 0xFF1565C0.toInt()
    private val red = 0xFFC62828.toInt()
    private val brown = 0xFF6D4C41.toInt()
    private val factory = PaletteEdit(listOf(black, blue, red))

    @Test fun deletingAColourRemovesIt() {
        assertEquals(PaletteEdit(listOf(black, red)), factory.deleted(blue))
    }

    @Test fun theLastColourStays() {
        val one = PaletteEdit(listOf(red))
        assertEquals(one, one.deleted(red))
        assertEquals(false, one.canDelete)
        assertEquals(true, factory.canDelete)
    }

    @Test fun undoingADeletePutsTheColourBackAtItsOldIndex() {
        assertEquals(factory, factory.deleted(black).undeleted(black, index = 0))
        assertEquals(factory, factory.deleted(red).undeleted(red, index = 2))
    }

    @Test fun undoingADeleteKeepsWhatWasAddedSince() {
        val deleted = factory.deleted(blue).added(brown)
        assertEquals(PaletteEdit(listOf(black, blue, red, brown)), deleted.undeleted(blue, index = 1))
        assertEquals(PaletteEdit(listOf(black, red, brown, blue)), deleted.undeleted(blue, index = 9))
        val back = deleted.undeleted(blue, 1)
        assertEquals(back, back.undeleted(blue, 1))
    }

    @Test fun movingAColourReordersThePalette() {
        assertEquals(listOf(blue, red, black), factory.moved(from = 0, to = 2).colours)
        assertEquals(listOf(red, black, blue), factory.moved(from = 2, to = 0).colours)
    }

    @Test fun addingAppendsAndAColourAlreadyThereIsNotAddedTwice() {
        assertEquals(listOf(black, blue, red, brown), factory.added(brown).colours)
        assertEquals(factory, factory.added(blue))
    }

    @Test fun editingReplacesInPlace() {
        assertEquals(PaletteEdit(listOf(black, brown, red)), factory.replaced(blue, brown))
    }

    @Test fun savingAnUnchangedColourKeepsIt() {
        assertEquals(factory, factory.replaced(blue, blue))
        val one = PaletteEdit(listOf(red))
        assertEquals(one, one.replaced(red, red))
    }

    @Test fun editingOntoAnotherColourMergesTheTwo() {
        assertEquals(PaletteEdit(listOf(black, red)), factory.replaced(blue, red))
    }

    @Test fun restoringBringsBackTheFactoryColoursAndKeepsTheAddedOnesAfterThem() {
        // Blue deleted, brown added and moved first, red edited to a lighter red.
        val lightRed = 0xFFE57373.toInt()
        val edited = PaletteEdit(listOf(brown, black, lightRed))
        assertEquals(PaletteEdit(listOf(black, blue, red, brown, lightRed)), edited.restored(factory.colours))
    }
}
