package com.violinsheet.core.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SettingsTest {
    private class FakeSettingDao : SettingDao {
        val values = MutableStateFlow(emptyMap<String, String>())
        override fun observe(key: String): Flow<String?> = values.map { it[key] }
        override suspend fun upsert(value: SettingValue) {
            values.value += value.key to value.value
        }
        override suspend fun delete(key: String) {
            values.value -= key
        }
    }

    private fun choice(key: String, group: String = "Reading", default: String = "two") = ChoiceSetting(
        key = key,
        group = group,
        title = key,
        choices = listOf(ChoiceSetting.Choice("two", "Two Pages"), ChoiceSetting.Choice("one", "One Page")),
        default = default,
    )

    @Test fun registryGroupsDeclarationsFromEveryFeatureInOrder() {
        val a = choice("reading.a")
        val b = choice("ink.b", group = "Ink")
        val c = choice("reading.c")
        val registry = SettingsRegistry(listOf(listOf(a, b), listOf(c)))
        assertEquals(listOf("Reading", "Ink"), registry.groups.keys.toList())
        assertEquals(listOf(a, c), registry.groups["Reading"])
    }

    @Test fun registryRejectsTheSameKeyTwice() {
        assertFailsWith<IllegalArgumentException> { SettingsRegistry(listOf(listOf(choice("x")), listOf(choice("x")))) }
    }

    @Test fun choiceDefaultMustBeOneOfItsChoices() {
        assertFailsWith<IllegalArgumentException> { choice("x", default = "three") }
    }

    @Test fun choiceRejectsSavingAValueThatIsNotAChoice() {
        assertFailsWith<IllegalArgumentException> { choice("x").encode("three") }
    }

    private val black = 0xFF000000.toInt()
    private val blue = 0xFF1565C0.toInt()
    private val yellow = 0x99FFEB3B.toInt()
    private fun palette() = PaletteSetting("ink.palette", "Ink", "Palette", listOf(black, blue))

    @Test fun paletteRoundTripsInOrderWithAlpha() {
        val palette = palette()
        assertEquals(listOf(yellow, black, blue), palette.decode(palette.encode(listOf(yellow, black, blue))))
    }

    @Test fun paletteFallsBackToItsFactoryColoursOnMissingEmptyOrBadInput() {
        val palette = palette()
        assertEquals(listOf(black, blue), palette.decode(null))
        assertEquals(listOf(black, blue), palette.decode(""))
        assertEquals(listOf(black, blue), palette.decode("12,red"))
    }

    @Test fun paletteKeepsOneSwatchPerColour() {
        val palette = palette()
        assertEquals(listOf(blue, black), palette.decode(palette.encode(listOf(blue, black, blue))))
    }

    @Test fun paletteRejectsSavingNoColour() {
        assertFailsWith<IllegalArgumentException> { palette().encode(emptyList()) }
    }

    private fun presets() = SizePresetsSetting("ink.sizes", "Ink", "Sizes", 0.5f..10f, 0.25f, "pt", listOf(0.75f, 1.5f, 3f))

    @Test fun presetsRoundTrip() {
        val presets = presets()
        assertEquals(listOf(1f, 2f, 8f), presets.decode(presets.encode(listOf(1f, 2f, 8f))))
    }

    @Test fun presetsAreSavedSortedAndInRange() {
        val presets = presets()
        assertEquals(listOf(0.5f, 1.5f, 10f), presets.decode(presets.encode(listOf(40f, 0.1f, 1.5f))))
        assertEquals(listOf(0.5f, 2f, 10f), presets.decode("12,2,0"))
    }

    @Test fun presetsFallBackToTheFactoryThreeOnMissingOrBadInput() {
        val presets = presets()
        assertEquals(listOf(0.75f, 1.5f, 3f), presets.decode(null))
        assertEquals(listOf(0.75f, 1.5f, 3f), presets.decode("1,2"))
        assertEquals(listOf(0.75f, 1.5f, 3f), presets.decode("1,2,x"))
        assertEquals(listOf(0.75f, 1.5f, 3f), presets.decode("1,2,3,4"))
    }

    @Test fun presetsRejectSavingOtherThanThree() {
        assertFailsWith<IllegalArgumentException> { presets().encode(listOf(1f, 2f)) }
    }

    @Test fun settlingSortsAndTellsWhereEachPresetWent() {
        // The large preset (3) dragged down to 1: the three re-sort, and it is now the middle one.
        val settled = presets().settle(listOf(0.75f, 1.5f, 1f), index = 2, from = 3f)
        assertEquals(listOf(0.75f, 1f, 1.5f), settled.sizes)
        assertEquals(1, settled.indexOf(2))
        assertEquals(2, settled.indexOf(1))
    }

    @Test fun aPresetLetGoOnAnotherIsNudgedOneStepBackTowardsWhereItCameFrom() {
        // Medium dragged down onto small (0.75): one step up, back towards 1.5, and stays in the middle.
        val down = presets().settle(listOf(0.75f, 0.75f, 3f), index = 1, from = 1.5f)
        assertEquals(listOf(0.75f, 1f, 3f), down.sizes)
        assertEquals(1, down.indexOf(1))
        // Small dragged up onto medium (1.5): one step down.
        val up = presets().settle(listOf(1.5f, 1.5f, 3f), index = 0, from = 0.75f)
        assertEquals(listOf(1.25f, 1.5f, 3f), up.sizes)
    }

    @Test fun aNudgeSkipsPastAThirdPresetAndTurnsBackAtTheRangeEnd() {
        // 1.25 is taken too: on to 1.0.
        assertEquals(listOf(1f, 1.25f, 1.5f), presets().settle(listOf(1.5f, 1.25f, 1.5f), index = 0, from = 0.75f).sizes)
        // A tie with no drag direction (stored that way) goes up, unless that leaves the range.
        assertEquals(listOf(0.75f, 1.5f, 1.75f), presets().settle(listOf(0.75f, 1.5f, 1.5f), index = 2, from = 1.5f).sizes)
        val atEnd = presets().settle(listOf(0.75f, 10f, 10f), index = 2, from = 10f)
        assertEquals(listOf(0.75f, 9.75f, 10f), atEnd.sizes)
        assertEquals(1, atEnd.indexOf(2))
    }

    @Test fun toggleRoundTripsAndFallsBackToItsDefault() {
        val toggle = ToggleSetting("ink.toggle", "Ink", "Toggle", default = false)
        assertEquals(true, toggle.decode(toggle.encode(true)))
        assertEquals(false, toggle.decode(null))
        assertEquals(false, toggle.decode("yes"))
    }

    @Test fun unsetSettingReadsItsDefault() = runTest {
        assertEquals("two", SettingsStore(FakeSettingDao(), this).observe(choice("x")).first())
    }

    @Test fun setValueIsStoredAndReadBack() = runTest {
        val dao = FakeSettingDao()
        val store = SettingsStore(dao, this)
        val setting = choice("reading.advance")
        store.set(setting, "one").join()
        assertEquals("one", store.observe(setting).first())
        assertEquals(mapOf("reading.advance" to "one"), dao.values.value)
    }

    @Test fun storedValueNoLongerAChoiceFallsBackToDefault() = runTest {
        val dao = FakeSettingDao()
        dao.values.value = mapOf("x" to "removed")
        assertEquals("two", SettingsStore(dao, this).observe(choice("x")).first())
    }
}
