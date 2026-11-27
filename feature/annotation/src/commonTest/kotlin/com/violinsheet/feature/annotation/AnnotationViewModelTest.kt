package com.violinsheet.feature.annotation

import com.violinsheet.core.data.ink.InkDao
import com.violinsheet.core.data.ink.InkRepository
import com.violinsheet.core.data.ink.InkStrokeRow
import com.violinsheet.core.data.settings.SettingDao
import com.violinsheet.core.data.settings.SettingValue
import com.violinsheet.core.data.settings.SettingsStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.violinsheet.core.ink.DragKind
import com.violinsheet.core.ink.EraseMode
import com.violinsheet.core.ink.SelectionFrame
import com.violinsheet.core.ink.decodeInkPoints
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
import kotlin.test.assertNull
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

    /** Stored Settings by key; empty means every Setting is at its declared default. Writes go to [saved]. */
    /** Settings as stored: a save is read back by the next ViewModel, as in the app. */
    private class FakeSettings(initial: Map<String, String> = emptyMap()) : SettingDao {
        private val stored = initial.toMutableMap()
        val saved = mutableListOf<SettingValue>()
        override fun observe(key: String) = flowOf(stored[key])
        override suspend fun upsert(value: SettingValue) {
            saved += value
            stored[value.key] = value.value
        }
        override suspend fun delete(key: String) {
            stored -= key
        }
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
        vm.addStrokes(listOf(stroke(1)))
        vm.addStrokes(listOf(stroke(2)))

        vm.undo()
        settle()
        assertEquals(listOf(1L), vm.ids())
        assertEquals(setOf(1L), savedIds())

        vm.redo()
        settle()
        assertEquals(listOf(1L, 2L), vm.ids())
        assertEquals(setOf(1L, 2L), savedIds())
    }

    @Test fun strokeSplitAcrossPagesIsOneUndoStep() = runTest {
        val vm = viewModel()
        settle()
        vm.addStrokes(listOf(stroke(1)))
        // One stroke drawn across a spread: a part on each Page.
        vm.addStrokes(listOf(line(2000, y = 10f, page = 0), line(2001, y = 10f, page = 1)))
        settle()
        assertEquals(mapOf(1L to 0, 2000L to 0, 2001L to 1), dao.rows.associate { it.id to it.pageIndex })

        vm.undo()
        settle()
        assertEquals(listOf(1L), vm.ids())
        assertEquals(setOf(1L), savedIds())

        vm.redo()
        settle()
        assertEquals(listOf(1L, 2000L, 2001L), vm.ids())
        assertEquals(mapOf(1L to 0, 2000L to 0, 2001L to 1), dao.rows.associate { it.id to it.pageIndex })
    }

    @Test fun erasingOnePartOfASplitStrokeLeavesTheOther() = runTest {
        val vm = viewModel()
        settle()
        vm.addStrokes(listOf(line(2000, y = 10f, page = 0), line(2001, y = 10f, page = 1)))
        vm.beginErase(Eraser(EraseMode.Stroke, 4f))
        vm.eraseAt(1, Offset(50f, 10f))
        vm.endErase(cancelled = false)
        settle()
        assertEquals(listOf(2000L), vm.ids())
        assertEquals(setOf(2000L), savedIds())

        vm.undo()
        settle()
        assertEquals(listOf(2000L, 2001L), vm.ids())
        assertEquals(mapOf(2000L to 0, 2001L to 1), dao.rows.associate { it.id to it.pageIndex })

        vm.undo()
        settle()
        assertEquals(emptyList(), vm.ids())
        assertEquals(emptySet(), savedIds())
    }

    @Test fun quickDrawUndoRedoLeavesDatabaseMatchingScreen() = runTest {
        val vm = viewModel()
        vm.addStrokes(listOf(stroke(1)))
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
        vm.addStrokes(listOf(stroke(1)))
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
        vm.addStrokes(listOf(stroke(1)))
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
        vm.addStrokes(listOf(stroke(1)))
        assertTrue(vm.canUndo.value)
        vm.undo()
        assertFalse(vm.canUndo.value)
        assertTrue(vm.canRedo.value)
        vm.addStrokes(listOf(stroke(2)))
        assertFalse(vm.canRedo.value)
    }

    @Test fun undoneStrokeStaysGoneWhenSheetIsReopenedAtOnce() = runTest {
        val firstOpening = ViewModelStore()
        val vm = viewModel(firstOpening)
        settle()
        vm.addStrokes(listOf(stroke(1)))
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

    @Test fun aSheetStartsOnEachPalettesFirstColourAndMiddlePresetNotOnToolbarChanges() = runTest {
        settingsDao = FakeSettings(
            mapOf(
                InkSettings.penPalette.key to InkSettings.penPalette.encode(listOf(0xFF123456.toInt(), 0xFF000000.toInt())),
                InkSettings.penSizes.key to InkSettings.penSizes.encode(listOf(1f, 2f, 4f)),
                InkSettings.highlighterPalette.key to InkSettings.highlighterPalette.encode(listOf(HIGHLIGHTER_COLOURS[2].argb, HIGHLIGHTER_COLOURS[0].argb)),
            ),
        )
        val first = viewModel()
        settle()
        val stored = first.tools.value
        assertEquals(0xFF123456.toInt(), stored.pen.colour)
        assertEquals(2f, stored.pen.size)
        assertEquals(HIGHLIGHTER_COLOURS[2].argb, stored.highlighter.colour)
        assertEquals(HIGHLIGHTER_SIZES[1], stored.highlighter.size)
        first.setTools(stored.copy(pen = stored.pen.copy(colour = 0xFF000000.toInt(), size = 1f)))

        val reopened = viewModel()
        settle()
        assertEquals(stored.pen, reopened.tools.value.pen)
        assertEquals(stored.highlighter, reopened.tools.value.highlighter)
    }

    @Test fun addedColoursAndEditedPresetsAreSavedAndReachTheNextSheet() = runTest {
        val first = viewModel()
        settle()
        first.setTools(first.tools.value.withColourAdded(0xFF6D4C41.toInt()))
        val (sorted, _) = first.tools.value.withPreset(1, 0.5f).withPresetsSorted(1, from = 1.5f)
        first.setTools(first.tools.value.withPreset(1, 0.5f), save = false)
        first.setTools(sorted)
        settle()

        val reopened = viewModel()
        settle()
        assertEquals(PEN_COLOURS.map { it.argb } + 0xFF6D4C41.toInt(), reopened.tools.value.palettes.pen)
        assertEquals(listOf(0.5f, 0.75f, 3f), reopened.tools.value.sizes.pen)
        // The colour and size chosen are this Sheet's: the next one starts from the first colour and the middle preset.
        assertEquals(PEN_COLOURS[0].argb, reopened.tools.value.pen.colour)
        assertEquals(0.75f, reopened.tools.value.pen.size)
    }

    @Test fun aPresetDragIsSavedOnceOnRelease() = runTest {
        val vm = viewModel()
        settle()
        listOf(1.75f, 2f, 2.25f).forEach { vm.setTools(vm.tools.value.withPreset(1, it), save = false) }
        settle()
        assertTrue(settingsDao.saved.none { it.key == InkSettings.penSizes.key })
        vm.setTools(vm.tools.value.withPresetsSorted(1, from = 1.5f).first)
        settle()
        assertEquals(listOf("0.75,2.25,3.0"), settingsDao.saved.filter { it.key == InkSettings.penSizes.key }.map { it.value })
    }

    @Test fun anEraserSizeWhosePresetChangedStartsAtTheMiddlePreset() = runTest {
        settingsDao = FakeSettings(mapOf(InkSettings.eraserSizes.key to InkSettings.eraserSizes.encode(listOf(5f, 10f, 20f))))
        val vm = viewModel()
        settle()
        assertEquals(10f, vm.tools.value.eraser.size)
    }

    @Test fun failedWriteSetsSaveFailed() = runTest {
        dao.failWrites = true
        val vm = viewModel()
        vm.addStrokes(listOf(stroke(1)))
        settle()
        assertTrue(vm.saveFailed.value)
        assertEquals(listOf(1L), vm.ids())
    }

    @Test fun failedDeleteOnUndoSetsSaveFailed() = runTest {
        dao.failDeletes = true
        val vm = viewModel()
        vm.addStrokes(listOf(stroke(1)))
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
        vm.addStrokes(listOf(line(1000, y = 0f)))
        vm.addStrokes(listOf(line(2000, y = 50f)))
        vm.addStrokes(listOf(line(3000, y = 0f, page = 1))) // same spot, other Page
        vm.addStrokes(listOf(line(4000, y = 0f)))

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
        vm.addStrokes(listOf(line(1000, y = 0f)))
        vm.addStrokes(listOf(line(2000, y = 5f))) // on top, out of the eraser's reach

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
        vm.addStrokes(listOf(line(1000, y = 0f)))

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
        vm.addStrokes(listOf(line(1000, y = 0f)))
        vm.addStrokes(listOf(line(2000, y = 50f)))
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
        vm.addStrokes(listOf(line(1000, y = 0f)))
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
        assertEquals(HIGHLIGHTER_COLOURS[0].argb, highlighter.colour)
        assertEquals(HIGHLIGHTER_SIZES[1], highlighter.size)
    }

    @Test fun strokeHandedOverDuringAnEraseKeepsPlacesOnUndo() = runTest {
        val vm = viewModel()
        settle()
        vm.addStrokes(listOf(line(1000, y = 0f)))
        vm.addStrokes(listOf(line(2000, y = 50f)))

        vm.beginErase(Eraser(EraseMode.Stroke, 4f))
        vm.eraseAt(0, Offset(50f, 0f))
        vm.addStrokes(listOf(line(3000, y = 80f))) // a lift lost earlier, handed over mid-erase
        vm.endErase(cancelled = false)
        assertEquals(listOf(2000L, 3000L), vm.ids())

        vm.undo()
        settle()
        assertEquals(listOf(1000L, 2000L, 3000L), vm.ids())

        vm.redo()
        vm.eraseThrough(Eraser(EraseMode.Area, 4f), listOf(Offset(50f, 50f)), cancelled = true)
        assertEquals(listOf(2000L, 3000L), vm.ids())
    }

    @Test fun splitStrokeHandedOverDuringAnEraseKeepsPlacesOnUndo() = runTest {
        val vm = viewModel()
        settle()
        vm.addStrokes(listOf(line(1000, y = 0f)))

        vm.beginErase(Eraser(EraseMode.Stroke, 4f))
        vm.eraseAt(0, Offset(50f, 0f))
        // A stroke across two Pages, its lift lost earlier, handed over mid-erase.
        vm.addStrokes(listOf(line(3000, y = 80f, page = 0), line(3001, y = 80f, page = 1)))
        vm.endErase(cancelled = false)
        assertEquals(listOf(3000L, 3001L), vm.ids())

        vm.undo()
        settle()
        assertEquals(listOf(1000L, 3000L, 3001L), vm.ids())
        assertEquals(setOf(1000L, 3000L, 3001L), savedIds())
    }

    @Test fun eraserThatLeftThePageDoesNotSweepFromWhereItLeft() = runTest {
        val vm = viewModel()
        settle()
        vm.addStrokes(listOf(line(1000, y = 50f)))

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
        vm.addStrokes(listOf(line(1000, y = 0f).copy(tool = StrokeTool.Highlighter)))
        vm.undo()
        vm.redo()
        settle()
        assertEquals("highlighter", dao.rows.single().tool)
        assertEquals(StrokeTool.Highlighter, vm.strokes.value.single().tool)
    }

    private val pageSize = Size(200f, 300f)

    /** Lines 1000, 2000 and 3000 at y = 0, 50 and 100 on Page 0 (on screen), with the Lasso tool chosen. */
    private fun TestScope.lassoViewModel(): AnnotationViewModel {
        val vm = viewModel()
        settle()
        vm.onVisiblePages(setOf(0))
        vm.addStrokes(listOf(line(1000, y = 0f)))
        vm.addStrokes(listOf(line(2000, y = 50f)))
        vm.addStrokes(listOf(line(3000, y = 100f)))
        vm.setTools(vm.tools.value.copy(chosen = ToolChoice.Lasso))
        settle()
        return vm
    }

    /** A rectangular lasso around the middle line (2000). */
    private val aroundMiddle = listOf(Offset(-10f, 40f), Offset(110f, 40f), Offset(110f, 60f), Offset(-10f, 60f))

    private fun AnnotationViewModel.first(id: Long) = strokes.value.single { it.id == id }.points.first()

    private fun savedFirst(id: Long) = decodeInkPoints(dao.rows.single { it.id == id }.points).first()

    private fun AnnotationViewModel.moveMiddle(by: Offset, cancelled: Boolean = false) {
        beginDrag(DragKind.Move, Offset(50f, 50f), pageSize)
        dragTo(Offset(50f, 50f) + by)
        endDrag(cancelled)
    }

    @Test fun lassoSelectsTheStrokesMostlyInsideIt() = runTest {
        val vm = lassoViewModel()
        vm.addStrokes(listOf(line(4000, y = 50f, page = 1))) // same spot, other Page
        vm.lassoDone(0, aroundMiddle)
        assertEquals(listOf(2000L), vm.selection.value?.ids)

        vm.lassoDone(0, listOf(Offset(150f, 150f), Offset(190f, 150f), Offset(190f, 190f)))
        assertNull(vm.selection.value)
    }

    @Test fun selectFramesTheStrokesInkBox() = runTest {
        val vm = lassoViewModel()
        vm.select(0, listOf(2000L))
        assertEquals(SelectionFrame(Offset(50f, 50f), Size(50.5f, 0.5f), 0f), vm.selection.value?.frame)
    }

    @Test fun moveBakesThePointsInAndKeepsIdsAndOrder() = runTest {
        val vm = lassoViewModel()
        vm.lassoDone(0, aroundMiddle)
        vm.beginDrag(DragKind.Move, Offset(50f, 50f), pageSize)
        vm.dragTo(Offset(60f, 70f))
        // While dragging only the preview moves.
        assertEquals(Offset(10f, 20f), vm.selection.value?.preview?.translation)
        assertEquals(0f, vm.first(2000).x)
        vm.endDrag(cancelled = false)
        settle()

        assertEquals(listOf(1000L, 2000L, 3000L), vm.ids())
        assertEquals(InkPoint(10f, 70f, 0.5f, 0), vm.first(2000))
        assertEquals(InkPoint(10f, 70f, 0.5f, 0), savedFirst(2000))
        assertNull(vm.selection.value?.preview)
        assertEquals(Offset(60f, 70f), vm.selection.value?.frame?.center)
    }

    @Test fun undoAndRedoOfAMoveRestoreRowsPointsAndFrame() = runTest {
        val vm = lassoViewModel()
        vm.lassoDone(0, aroundMiddle)
        vm.moveMiddle(by = Offset(10f, 20f))
        settle()

        vm.undo()
        settle()
        assertEquals(listOf(1000L, 2000L, 3000L), vm.ids())
        assertEquals(InkPoint(0f, 50f, 0.5f, 0), vm.first(2000))
        assertEquals(InkPoint(0f, 50f, 0.5f, 0), savedFirst(2000))
        assertEquals(listOf(2000L), vm.selection.value?.ids)
        assertEquals(Offset(50f, 50f), vm.selection.value?.frame?.center)

        vm.redo()
        settle()
        assertEquals(InkPoint(10f, 70f, 0.5f, 0), vm.first(2000))
        assertEquals(InkPoint(10f, 70f, 0.5f, 0), savedFirst(2000))
        assertEquals(Offset(60f, 70f), vm.selection.value?.frame?.center)

        // With another tool chosen, undo doesn't bring a selection back.
        vm.setTools(vm.tools.value.copy(chosen = ToolChoice.Pen))
        vm.undo()
        assertNull(vm.selection.value)
    }

    @Test fun deletedSelectionComesBackInItsPlaceSelected() = runTest {
        val vm = lassoViewModel()
        vm.lassoDone(0, aroundMiddle)
        vm.deleteSelection()
        settle()
        assertEquals(listOf(1000L, 3000L), vm.ids())
        assertEquals(setOf(1000L, 3000L), savedIds())
        assertNull(vm.selection.value)

        vm.undo()
        settle()
        assertEquals(listOf(1000L, 2000L, 3000L), vm.ids())
        assertEquals(setOf(1000L, 2000L, 3000L), savedIds())
        assertEquals(listOf(2000L), vm.selection.value?.ids)

        vm.redo()
        settle()
        assertEquals(listOf(1000L, 3000L), vm.ids())
        assertNull(vm.selection.value)
    }

    @Test fun cancelledDragChangesNothingAndAddsNoUndoStep() = runTest {
        val vm = lassoViewModel()
        vm.lassoDone(0, aroundMiddle)
        vm.moveMiddle(by = Offset(10f, 20f), cancelled = true)
        settle()
        assertEquals(InkPoint(0f, 50f, 0.5f, 0), vm.first(2000))
        assertNull(vm.selection.value?.preview)

        // The last step is still drawing line 3000.
        vm.undo()
        settle()
        assertEquals(listOf(1000L, 2000L), vm.ids())
        assertEquals(InkPoint(0f, 50f, 0.5f, 0), vm.first(2000))
    }

    @Test fun eraseAndToolSwitchClearTheSelection() = runTest {
        val vm = lassoViewModel()
        vm.lassoDone(0, aroundMiddle)
        vm.eraseThrough(Eraser(EraseMode.Stroke, 4f), listOf(Offset(150f, 250f)))
        assertNull(vm.selection.value)

        vm.lassoDone(0, aroundMiddle)
        vm.setTools(vm.tools.value.copy(eraser = Eraser(EraseMode.Area, 20f))) // same tool chosen: kept
        assertEquals(listOf(2000L), vm.selection.value?.ids)
        vm.setTools(vm.tools.value.copy(chosen = ToolChoice.Pen))
        assertNull(vm.selection.value)
    }

    @Test fun aSelectionEndsWhenItsPageLeavesTheScreen() = runTest {
        val vm = lassoViewModel()
        vm.lassoDone(0, aroundMiddle)
        vm.onVisiblePages(setOf(0, 1)) // zoom or 2-up: Page 0 still shown
        assertEquals(listOf(2000L), vm.selection.value?.ids)
        vm.onVisiblePages(setOf(1, 2))
        assertNull(vm.selection.value)
    }

    @Test fun undoAndRedoDoNotSelectStrokesOnAPageOffScreen() = runTest {
        val vm = lassoViewModel()
        vm.lassoDone(0, aroundMiddle)
        vm.moveMiddle(by = Offset(10f, 20f))
        settle()
        vm.onVisiblePages(setOf(1))

        vm.undo()
        settle()
        assertEquals(InkPoint(0f, 50f, 0.5f, 0), vm.first(2000))
        assertNull(vm.selection.value)
        vm.redo()
        settle()
        assertEquals(InkPoint(10f, 70f, 0.5f, 0), vm.first(2000))
        assertNull(vm.selection.value)
    }

    @Test fun undoingADeleteKeepsTheTurnedFrame() = runTest {
        val vm = lassoViewModel()
        vm.lassoDone(0, aroundMiddle)
        // A quarter turn about the centre (50, 50): from straight above it to straight right of it.
        vm.beginDrag(DragKind.Rotate, Offset(50f, 0f), pageSize)
        vm.dragTo(Offset(100f, 50f))
        vm.endDrag(cancelled = false)
        settle()
        val turned = vm.selection.value?.frame
        assertEquals(90f, turned?.angleDegrees)

        vm.deleteSelection()
        vm.undo()
        settle()
        assertEquals(turned, vm.selection.value?.frame)
    }

    @Test fun resizeGrabbedOffTheCornerDoesNotJump() = runTest {
        val vm = lassoViewModel()
        vm.lassoDone(0, aroundMiddle)
        val corner = vm.selection.value!!.frame.corners()[2]
        // Grabbed 5 pt off the corner (the padded frame, the handle's reach); not moved yet.
        vm.beginDrag(DragKind.Resize(2), corner + Offset(5f, 5f), pageSize)
        vm.dragTo(corner + Offset(5f, 5f))
        assertEquals(1f, vm.selection.value!!.preview!!.scale, 0.0001f)
    }

    /** A lasso around 95% of the middle line (2000): it misses x 0 to 5. */
    private val aroundMostOfMiddle = listOf(Offset(5f, 40f), Offset(110f, 40f), Offset(110f, 60f), Offset(5f, 60f))

    @Test fun lassoFollowsTheIncludePartlyEnclosedSettingAndTheToolbarSavesIt() = runTest {
        val vm = lassoViewModel()
        vm.lassoDone(0, aroundMostOfMiddle)
        assertNull(vm.selection.value)

        vm.setTools(vm.tools.value.copy(includePartlyEnclosed = true))
        settle()
        assertEquals(listOf(SettingValue(InkSettings.includePartlyEnclosed.key, "true")), settingsDao.saved)
        vm.lassoDone(0, aroundMostOfMiddle)
        assertEquals(listOf(2000L), vm.selection.value?.ids)
    }

    @Test fun storedIncludePartlyEnclosedSettingIsReadAtOpen() = runTest {
        settingsDao = FakeSettings(mapOf(InkSettings.includePartlyEnclosed.key to "true"))
        val vm = lassoViewModel()
        assertEquals(true, vm.tools.value.includePartlyEnclosed)
        vm.lassoDone(0, aroundMostOfMiddle)
        assertEquals(listOf(2000L), vm.selection.value?.ids)
    }

    @Test fun aTapSelectsTheStrokeUnderItAloneAndATapOnEmptySpaceClears() = runTest {
        val vm = lassoViewModel()
        vm.lassoDone(0, listOf(Offset(-10f, -10f), Offset(110f, -10f), Offset(110f, 110f), Offset(-10f, 110f)))
        assertEquals(listOf(1000L, 2000L, 3000L), vm.selection.value?.ids)

        vm.tapAt(0, Offset(30f, 51f), slop = 2f)
        assertEquals(listOf(2000L), vm.selection.value?.ids)
        vm.tapAt(0, Offset(30f, 25f), slop = 2f)
        assertNull(vm.selection.value)
    }

    @Test fun undoingADrawnStrokeClearsTheSelection() = runTest {
        val vm = lassoViewModel()
        vm.lassoDone(0, aroundMiddle)
        vm.undo()
        assertEquals(listOf(1000L, 2000L), vm.ids())
        assertNull(vm.selection.value)
    }
}
