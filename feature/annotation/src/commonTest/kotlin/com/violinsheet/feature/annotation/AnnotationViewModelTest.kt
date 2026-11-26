package com.violinsheet.feature.annotation

import com.violinsheet.core.data.ink.InkDao
import com.violinsheet.core.data.ink.InkRepository
import com.violinsheet.core.data.ink.InkStrokeRow
import com.violinsheet.core.data.settings.SettingDao
import com.violinsheet.core.data.settings.SettingValue
import com.violinsheet.core.data.settings.SettingsStore
import androidx.compose.ui.geometry.Offset
import com.violinsheet.core.ink.EraseMode
import com.violinsheet.core.ink.Eraser
import com.violinsheet.core.ink.InkPoint
import com.violinsheet.core.ink.InkStroke
import com.violinsheet.core.ink.StrokeTool
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

        override suspend fun insert(rows: List<InkStrokeRow>) {
            delay(10)
            check(!failWrites) { "disk full" }
            val ids = rows.map { it.id }.toSet()
            this.rows.removeAll { it.id in ids } // REPLACE
            this.rows += rows
        }

        override suspend fun delete(ids: List<Long>) {
            delay(deleteMillis)
            check(!failDeletes) { "disk full" }
            rows.removeAll { it.id in ids }
        }

        override suspend fun idsOnOtherSheets(ids: List<Long>, sheetId: Long) =
            rows.filter { it.id in ids && it.sheetId != sheetId }.map { it.id }
    }

    /** Stored Settings by key; empty means every Setting is at its declared default. */
    private class FakeSettings(val stored: Map<String, String> = emptyMap()) : SettingDao {
        override fun observe(key: String) = flowOf(stored[key])
        override suspend fun upsert(value: SettingValue) = Unit
    }

    private var settingsDao = FakeSettings()

    private val dao = FakeInkDao()
    private val sheetId = 7L

    @BeforeTest fun mainDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest fun resetMainDispatcher() = Dispatchers.resetMain()

    private fun stroke(id: Long) = InkStroke(id, 0, 0xFF000000.toInt(), 1.5f, listOf(InkPoint(1f, id.toFloat(), 0.5f, 0)), StrokeTool.Pen)

    /** Ids given to pieces that can't keep their stroke's place. */
    private var nextFallbackId = 1_000_000L

    /** A straight 1 pt stroke along y = [y] from x = 0 to 100, a point every 1 pt. */
    private fun line(id: Long, y: Float, page: Int = 0) =
        InkStroke(id, page, 0xFF000000.toInt(), 1f, List(101) { InkPoint(it.toFloat(), y, 0.5f, it.toLong()) }, StrokeTool.Pen)

    /** One erase gesture through [points] (Page 0), as InkLayer reports it. */
    private fun AnnotationViewModel.eraseThrough(eraser: Eraser, points: List<Offset>, cancelled: Boolean = false) {
        beginErase(eraser)
        for (p in points) eraseAt(0, p)
        endErase(cancelled)
    }

    private fun savedPoints(id: Long) = dao.rows.single { it.id == id }.points.size

    /** One per test, like the app's: every ViewModel of the test shares its queue. */
    private var ink: InkRepository? = null

    /** Created through [store] as the app does, so clearing the store runs the ViewModel's onCleared. */
    private fun TestScope.viewModel(store: ViewModelStore = ViewModelStore()): AnnotationViewModel {
        val repository = ink ?: InkRepository(dao, backgroundScope).also { ink = it }
        val factory = viewModelFactory {
            initializer { AnnotationViewModel(sheetId, repository, SettingsStore(settingsDao, backgroundScope), EraserMemory()) { nextFallbackId++ } }
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
        dao.rows += InkStrokeRow(100, sheetId, 0, 0, 1f, encodeInkPoints(stroke(100).points), "pen")
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

    @Test fun reopenedSheetStartsFromSettingsNotFromToolbarChanges() = runTest {
        settingsDao = FakeSettings(
            mapOf(
                InkSettings.penColour.key to InkSettings.penColour.encode(0xFF123456.toInt()),
                InkSettings.penSize.key to InkSettings.penSize.encode(4f),
                InkSettings.highlighterColour.key to InkSettings.highlighterColour.encode(0x99ABCDEF.toInt()),
                InkSettings.highlighterSize.key to InkSettings.highlighterSize.encode(9f),
            ),
        )
        val first = viewModel()
        settle()
        val stored = first.tools.value
        assertEquals(0xFF123456.toInt(), stored.pen.colour)
        first.setTools(stored.copy(pen = stored.pen.copy(colour = 0xFF654321.toInt(), size = 10f), highlighter = stored.highlighter.copy(colour = 0x99010203.toInt(), size = 30f)))
        assertEquals(10f, first.tools.value.pen.size)

        val reopened = viewModel()
        settle()
        assertEquals(stored.pen, reopened.tools.value.pen)
        assertEquals(stored.highlighter, reopened.tools.value.highlighter)
        assertEquals(4f, reopened.tools.value.pen.size)
        assertEquals(9f, reopened.tools.value.highlighter.size)
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
        val (left, removed) = listOf("a", "b", "c", "d", "e").takeOut { it == "b" || it == "d" }
        assertEquals(listOf("a", "c", "e"), left)
        assertEquals(listOf("a", "b", "c", "d", "e"), left.putBack(removed))
        // Two saved strokes loaded in front since: every place shifts by two.
        assertEquals(listOf("x", "y", "a", "b", "c", "d", "e"), (listOf("x", "y") + left).putBack(removed))
    }

    @Test fun strokeEraserRemovesWholeStrokesItTouchesAsOneUndoStep() = runTest {
        val vm = viewModel()
        settle()
        vm.addStroke(line(1000, y = 0f))
        vm.addStroke(line(2000, y = 50f))
        vm.addStroke(line(3000, y = 0f, page = 1)) // same spot, other Page
        vm.addStroke(line(4000, y = 0f))

        // Down at (50, -20), across both strokes at y = 0, up past them.
        vm.eraseThrough(Eraser(EraseMode.Stroke, 4f), listOf(Offset(50f, -20f), Offset(50f, 20f)))
        settle()
        assertEquals(listOf(2000L, 3000L), vm.ids())
        assertEquals(setOf(2000L, 3000L), savedIds())

        vm.undo()
        settle()
        assertEquals(listOf(1000L, 2000L, 3000L, 4000L), vm.ids())
        assertEquals(setOf(1000L, 2000L, 3000L, 4000L), savedIds())

        vm.redo()
        settle()
        assertEquals(listOf(2000L, 3000L), vm.ids())
        assertEquals(setOf(2000L, 3000L), savedIds())
    }

    @Test fun areaEraserCutsAStrokeIntoPiecesThatKeepItsPlace() = runTest {
        val vm = viewModel()
        settle()
        vm.addStroke(line(1000, y = 0f))
        vm.addStroke(line(2000, y = 5f)) // on top, out of the eraser's reach

        vm.eraseThrough(Eraser(EraseMode.Area, 4f), listOf(Offset(50f, -3f), Offset(50f, 1f)))
        settle()
        assertEquals(listOf(1000L, 1001L, 2000L), vm.ids())
        assertEquals(setOf(1000L, 1001L, 2000L), savedIds())
        val (left, right) = vm.strokes.value
        assertTrue(left.points.last().x < 48f && right.points.first().x > 52f)

        vm.undo()
        settle()
        assertEquals(listOf(line(1000, 0f), line(2000, 5f)), vm.strokes.value)
        assertEquals(setOf(1000L, 2000L), savedIds())
        assertEquals(encodeInkPoints(line(1000, 0f).points).size, savedPoints(1000))

        vm.redo()
        settle()
        assertEquals(listOf(1000L, 1001L, 2000L), vm.ids())
        assertEquals(setOf(1000L, 1001L, 2000L), savedIds())
    }

    @Test fun gestureThatCutsItsOwnPiecesIsStillOneStep() = runTest {
        val vm = viewModel()
        settle()
        vm.addStroke(line(1000, y = 0f))

        // Two dabs in one gesture: the second cuts the right-hand piece of the first.
        vm.eraseThrough(Eraser(EraseMode.Area, 4f), listOf(Offset(30f, 0f), Offset(30f, 30f), Offset(70f, 30f), Offset(70f, 0f)))
        settle()
        assertEquals(listOf(1000L, 1001L, 1002L), vm.ids())
        assertEquals(setOf(1000L, 1001L, 1002L), savedIds())

        vm.undo()
        settle()
        assertEquals(listOf(line(1000, 0f)), vm.strokes.value)
        assertEquals(setOf(1000L), savedIds())
        vm.undo() // the next step back is the drawing
        assertEquals(emptyList(), vm.ids())
    }

    @Test fun eraseThatTouchesNothingAddsNoUndoStep() = runTest {
        val vm = viewModel()
        settle()
        vm.eraseThrough(Eraser(EraseMode.Area, 4f), listOf(Offset(30f, 30f)))
        assertFalse(vm.canUndo.value)
    }

    @Test fun cancelledEraseTakesItsErasingBack() = runTest {
        val vm = viewModel()
        settle()
        vm.addStroke(line(1000, y = 0f))
        vm.addStroke(line(2000, y = 50f))
        settle()

        vm.eraseThrough(Eraser(EraseMode.Area, 4f), listOf(Offset(50f, 0f)), cancelled = true)
        settle()
        assertEquals(listOf(line(1000, 0f), line(2000, 50f)), vm.strokes.value)
        assertEquals(setOf(1000L, 2000L), savedIds())
        vm.undo() // takes back the last drawn stroke, not a phantom erase
        assertEquals(listOf(1000L), vm.ids())
    }

    @Test fun failedEraseWriteSetsSaveFailed() = runTest {
        val vm = viewModel()
        settle()
        vm.addStroke(line(1000, y = 0f))
        settle()
        dao.failDeletes = true
        vm.eraseThrough(Eraser(EraseMode.Stroke, 4f), listOf(Offset(50f, 0f)))
        settle()
        assertTrue(vm.saveFailed.value)
    }

    @Test fun highlighterStartsFromItsSettings() = runTest {
        val vm = viewModel()
        settle()
        val highlighter = vm.tools.value.highlighter
        assertEquals(InkSettings.highlighterColour.default, highlighter.colour)
        assertEquals(InkSettings.highlighterSize.default, highlighter.size)
    }

    @Test fun strokeHandedOverDuringAnEraseKeepsPlacesOnUndo() = runTest {
        val vm = viewModel()
        settle()
        vm.addStroke(line(1000, y = 0f))
        vm.addStroke(line(2000, y = 50f))

        vm.beginErase(Eraser(EraseMode.Stroke, 4f))
        vm.eraseAt(0, Offset(50f, 0f))
        vm.addStroke(line(3000, y = 80f)) // a lift lost earlier, handed over mid-erase
        vm.endErase(cancelled = false)
        assertEquals(listOf(2000L, 3000L), vm.ids())

        vm.undo()
        settle()
        assertEquals(listOf(1000L, 2000L, 3000L), vm.ids())

        vm.redo()
        vm.eraseThrough(Eraser(EraseMode.Area, 4f), listOf(Offset(50f, 50f)), cancelled = true)
        assertEquals(listOf(2000L, 3000L), vm.ids())
    }

    @Test fun eraserThatLeftThePageDoesNotSweepFromWhereItLeft() = runTest {
        val vm = viewModel()
        settle()
        vm.addStroke(line(1000, y = 50f))

        vm.beginErase(Eraser(EraseMode.Stroke, 4f))
        vm.eraseAt(0, Offset(50f, 0f))
        vm.eraserOffPage()
        vm.eraseAt(0, Offset(50f, 100f)) // the straight path back would cross the stroke
        vm.endErase(cancelled = false)
        assertEquals(listOf(1000L), vm.ids())
    }

    @Test fun highlighterUndoAndRedoKeepItAHighlighter() = runTest {
        val vm = viewModel()
        settle()
        vm.addStroke(line(1000, y = 0f).copy(tool = StrokeTool.Highlighter))
        vm.undo()
        vm.redo()
        settle()
        assertEquals("highlighter", dao.rows.single().tool)
        assertEquals(StrokeTool.Highlighter, vm.strokes.value.single().tool)
    }
}
