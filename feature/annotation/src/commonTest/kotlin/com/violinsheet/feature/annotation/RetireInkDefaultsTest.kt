package com.violinsheet.feature.annotation

import com.violinsheet.core.data.settings.SettingDao
import com.violinsheet.core.data.settings.SettingValue
import com.violinsheet.core.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RetireInkDefaultsTest {
    private class FakeSettings(initial: Map<String, String>) : SettingDao {
        val values = MutableStateFlow(initial)
        var writes = 0
        var failUpserts = false
        override fun observe(key: String) = values.map { it[key] }
        override suspend fun upsert(value: SettingValue) {
            check(!failUpserts) { "disk full" }
            writes++
            values.value += value.key to value.value
        }
        override suspend fun delete(key: String) {
            if (key in values.value) writes++
            values.value -= key
        }
    }

    private val black = 0xFF000000.toInt()
    private val blue = 0xFF1565C0.toInt()
    private val red = 0xFFC62828.toInt()
    private val brown = 0xFF6D4C41.toInt()

    /** Starts a store on [dao] with the migration [runs] times, as [runs] app starts, and reads the pen palette after. */
    private fun retired(dao: FakeSettings, runs: Int = 1, check: (FakeSettings, List<Int>) -> Unit) = runTest {
        var palette = emptyList<Int>()
        repeat(runs) {
            val scope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, _ -> })
            palette = SettingsStore(dao, scope, listOf(::retireInkDefaults)).observe(InkSettings.penPalette).first()
        }
        check(dao, palette)
    }

    private fun retired(initial: Map<String, String>, runs: Int = 1, check: (FakeSettings, List<Int>) -> Unit) = retired(FakeSettings(initial), runs, check)

    @Test fun aStoredDefaultColourInItsPaletteMovesToTheFrontAndItsKeyGoes() = retired(
        mapOf(
            "ink.penPalette" to "$black,$blue,$red",
            PEN_COLOUR_KEY to "$red",
            HIGHLIGHTER_COLOUR_KEY to "${HIGHLIGHTER_COLOURS[2].argb}",
        ),
    ) { dao, palette ->
        // The first read already sees the migrated palette: reads wait for the migration.
        assertEquals(listOf(red, black, blue), palette)
        // The highlighter palette was never stored: the factory one gets the default in front.
        assertEquals(
            listOf(HIGHLIGHTER_COLOURS[2], HIGHLIGHTER_COLOURS[0], HIGHLIGHTER_COLOURS[1]).joinToString(",") { "${it.argb}" },
            dao.values.value["ink.highlighterPalette"],
        )
        assertEquals(setOf("ink.penPalette", "ink.highlighterPalette"), dao.values.value.keys)
    }

    @Test fun noStoredDefaultsChangeNothing() = retired(mapOf("ink.penPalette" to "$black,$blue")) { dao, _ ->
        assertEquals(mapOf("ink.penPalette" to "$black,$blue"), dao.values.value)
        assertEquals(0, dao.writes)
    }

    @Test fun aStoredDefaultNotInItsPaletteIsDeletedAndThePaletteStays() = retired(
        mapOf("ink.penPalette" to "$black,$blue", PEN_COLOUR_KEY to "$brown"),
    ) { dao, _ ->
        assertEquals(mapOf("ink.penPalette" to "$black,$blue"), dao.values.value)
    }

    @Test fun theRetiredSizeKeysAreDeleted() = retired(
        RETIRED_SIZE_KEYS.associateWith { "1" } + ("ink.penSizes" to "1.0,2.0,3.0"),
    ) { dao, _ ->
        assertEquals(mapOf("ink.penSizes" to "1.0,2.0,3.0"), dao.values.value)
    }

    @Test fun aFailedPaletteWriteKeepsTheOldKeySoTheNextStartTriesAgain() {
        val dao = FakeSettings(mapOf("ink.penPalette" to "$black,$blue,$red", PEN_COLOUR_KEY to "$red")).apply { failUpserts = true }
        retired(dao) { _, _ -> assertEquals("$red", dao.values.value[PEN_COLOUR_KEY]) }
        dao.failUpserts = false
        retired(dao) { _, palette ->
            assertEquals(listOf(red, black, blue), palette)
            assertEquals(null, dao.values.value[PEN_COLOUR_KEY])
        }
    }

    @Test fun aSecondRunChangesNothing() {
        val start = mapOf("ink.penPalette" to "$black,$blue,$red", PEN_COLOUR_KEY to "$red", "ink.penSizePreset" to "2")
        var once = emptyMap<String, String>()
        retired(start) { dao, _ -> once = dao.values.value }
        retired(start, runs = 2) { dao, _ -> assertEquals(once, dao.values.value) }
        retired(once) { dao, _ -> assertEquals(0, dao.writes) }
    }
}
