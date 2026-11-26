package com.violinsheet.feature.annotation

import com.violinsheet.core.data.ink.InkDao
import com.violinsheet.core.data.ink.InkRepository
import com.violinsheet.core.data.ink.InkStrokeRow
import com.violinsheet.core.data.settings.SettingDao
import com.violinsheet.core.data.settings.SettingValue
import com.violinsheet.core.data.settings.SettingsStore
import com.violinsheet.core.ink.InkPoint
import com.violinsheet.core.ink.InkStroke
import com.violinsheet.core.ink.encodeInkPoints
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AnnotationViewModelTest {
    /**
     * In-memory stand-in for the Room DAO (insert replaces). An insert takes longer than a delete, as a real one can,
     * so writes started side by side would land out of order.
     */
    private class FakeInkDao : InkDao {
        val rows = mutableListOf<InkStrokeRow>()
        /** The load reads the rows after this long, and returns them this much later. */
        var readAfterMillis = 0L
        var returnAfterMillis = 0L
        var deleteMillis = 0L
        var failWrites = false
        var failDeletes = false

        override suspend fun forSheet(sheetId: Long): List<InkStrokeRow> {
            delay(readAfterMillis)
            val snapshot = rows.filter { it.sheetId == sheetId }.sortedBy { it.id }
            delay(returnAfterMillis)
            return snapshot
        }

        override suspend fun insert(row: InkStrokeRow) {
            delay(10)
            check(!failWrites) { "disk full" }
            rows.removeAll { it.id == row.id } // REPLACE
            rows += row
        }

        override suspend fun delete(id: Long) {
            delay(deleteMillis)
            check(!failDeletes) { "disk full" }
            rows.removeAll { it.id == id }
        }
    }

    private object NoSettings : SettingDao {
        override fun observe(key: String) = flowOf<String?>(null)
        override suspend fun upsert(value: SettingValue) = Unit
    }

    private val dao = FakeInkDao()
    private val sheetId = 7L

    @BeforeTest fun mainDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest fun resetMainDispatcher() = Dispatchers.resetMain()

    private fun stroke(id: Long) = InkStroke(id, 0, 0xFF000000.toInt(), 1.5f, listOf(InkPoint(1f, id.toFloat(), 0.5f, 0)))

    /** One per test, like the app's: every ViewModel of the test shares its queue. */
    private var ink: InkRepository? = null

    /** Created through [store] as the app does, so clearing the store runs the ViewModel's onCleared. */
    private fun TestScope.viewModel(store: ViewModelStore = ViewModelStore()): AnnotationViewModel {
        val repository = ink ?: InkRepository(dao, backgroundScope).also { ink = it }
        val factory = viewModelFactory {
            initializer { AnnotationViewModel(sheetId, repository, SettingsStore(NoSettings, backgroundScope)) }
        }
        return ViewModelProvider.create(store, factory)[AnnotationViewModel::class]
    }

    /** Runs everything due, including the writer in backgroundScope, which advanceUntilIdle leaves alone. */
    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    private fun AnnotationViewModel.ids() = strokes.value.map { it.id }

    private fun savedIds() = dao.rows.map { it.id }.toSet()

    @Test fun undoRemovesLastStrokeAndRedoPutsItBack() = runTest {
        val vm = viewModel()
        settle()
        vm.addStroke(stroke(1))
        vm.addStroke(stroke(2))

        vm.undo()
        settle()
        assertEquals(listOf(1L), vm.ids())
        assertEquals(setOf(1L), savedIds())

        vm.redo()
        settle()
        assertEquals(listOf(1L, 2L), vm.ids())
        assertEquals(setOf(1L, 2L), savedIds())
    }

    @Test fun quickDrawUndoRedoLeavesDatabaseMatchingScreen() = runTest {
        val vm = viewModel()
        vm.addStroke(stroke(1))
        vm.undo()
        vm.redo()
        vm.undo()
        vm.redo()
        settle()
        assertEquals(listOf(1L), vm.ids())
        assertEquals(setOf(1L), savedIds())
        assertFalse(vm.saveFailed.value)

        vm.undo()
        settle()
        assertEquals(emptySet(), savedIds())
    }

    /**
     * Through the ViewModel an undone stroke is always the last one (undo only takes back the newest change, and saved
     * strokes load in front), so this checks the shift for loaded strokes. Redo at a place mid-list is checked on
     * [putBack] directly, below.
     */
    @Test fun redoKeepsPlaceWhenSavedStrokesLoadInBetween() = runTest {
        dao.rows += InkStrokeRow(100, sheetId, 0, 0, 1f, encodeInkPoints(stroke(100).points))
        dao.readAfterMillis = 50
        val vm = viewModel()
        vm.addStroke(stroke(1))
        vm.undo()
        settle()
        assertEquals(listOf(100L), vm.ids())

        vm.redo()
        assertEquals(listOf(100L, 1L), vm.ids())
    }

    @Test fun strokeUndoneWhileSavedStrokesLoadStaysUndone() = runTest {
        dao.readAfterMillis = 20
        dao.returnAfterMillis = 100
        val vm = viewModel()
        vm.addStroke(stroke(1))
        // Saved before the load's read, so the load returns it; undone before the load finishes.
        advanceTimeBy(30)
        vm.undo()
        settle()
        assertEquals(emptyList(), vm.ids())
        assertEquals(emptySet(), savedIds())
    }

    @Test fun undoAndRedoEnabledOnlyWithSomethingToDo() = runTest {
        val vm = viewModel()
        assertFalse(vm.canUndo.value)
        assertFalse(vm.canRedo.value)
        vm.addStroke(stroke(1))
        assertTrue(vm.canUndo.value)
        vm.undo()
        assertFalse(vm.canUndo.value)
        assertTrue(vm.canRedo.value)
        vm.addStroke(stroke(2))
        assertFalse(vm.canRedo.value)
    }

    @Test fun undoneStrokeStaysGoneWhenSheetIsReopenedAtOnce() = runTest {
        val firstOpening = ViewModelStore()
        val vm = viewModel(firstOpening)
        settle()
        vm.addStroke(stroke(1))
        vm.undo()
        firstOpening.clear() // the Sheet closes before the insert and delete have landed
        // The new opening's load reads between the insert (done at 10 ms) and the delete (done at 20 ms) unless it
        // waits behind them in the shared queue.
        dao.deleteMillis = 10
        dao.readAfterMillis = 15
        val reopened = viewModel()
        settle()
        assertEquals(emptyList(), reopened.ids())
        assertEquals(emptySet(), savedIds())
    }

    @Test fun failedWriteSetsSaveFailed() = runTest {
        dao.failWrites = true
        val vm = viewModel()
        vm.addStroke(stroke(1))
        settle()
        assertTrue(vm.saveFailed.value)
        assertEquals(listOf(1L), vm.ids())
    }

    @Test fun failedDeleteOnUndoSetsSaveFailed() = runTest {
        dao.failDeletes = true
        val vm = viewModel()
        vm.addStroke(stroke(1))
        settle()
        assertFalse(vm.saveFailed.value)

        vm.undo()
        settle()
        assertTrue(vm.saveFailed.value)
        assertEquals(emptyList(), vm.ids())
    }

    @Test fun putBackReturnsSeveralItemsToTheirPlacesMidList() {
        // a b c d e with b and d taken out leaves a c e.
        val removed = listOf(IndexedValue(1, "b"), IndexedValue(3, "d"))
        assertEquals(listOf("a", "b", "c", "d", "e"), listOf("a", "c", "e").putBack(removed, sizeWithout = 3))
        // Two saved strokes loaded in front since: every place shifts by two.
        assertEquals(
            listOf("x", "y", "a", "b", "c", "d", "e"),
            listOf("x", "y", "a", "c", "e").putBack(removed, sizeWithout = 3),
        )
    }
}
