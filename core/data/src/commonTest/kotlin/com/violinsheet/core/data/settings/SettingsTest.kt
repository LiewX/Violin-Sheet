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

    @Test fun sliderKeepsStoredValuesInRange() {
        val size = SliderSetting("ink.size", "Ink", "Size", 0.5f..10f, 0.5f, "pt", 1.5f)
        assertEquals(10f, size.decode("40"))
        assertEquals(1.5f, size.decode("not a number"))
        assertEquals("0.5", size.encode(0.1f))
    }

    @Test fun colourRoundTrips() {
        val colour = ColourSetting("ink.colour", "Ink", "Colour", listOf(0xFF000000.toInt()), 0xFF000000.toInt())
        assertEquals(0xFF1565C0.toInt(), colour.decode(colour.encode(0xFF1565C0.toInt())))
        assertEquals(0xFF000000.toInt(), colour.decode(null))
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
