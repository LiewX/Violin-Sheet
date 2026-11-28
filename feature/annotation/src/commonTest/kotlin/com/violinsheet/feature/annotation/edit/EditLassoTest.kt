package com.violinsheet.feature.annotation.edit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.violinsheet.core.data.ink.InkDao
import com.violinsheet.core.data.ink.InkRepository
import com.violinsheet.core.data.ink.InkStrokeRow
import com.violinsheet.core.data.settings.SettingDao
import com.violinsheet.core.data.settings.SettingValue
import com.violinsheet.core.data.settings.SettingsStore
import com.violinsheet.core.data.symbol.SymbolRepository
import com.violinsheet.core.ink.FrameHit
import com.violinsheet.core.ink.PagePlacement
import com.violinsheet.core.ink.SelectionFrame
import com.violinsheet.feature.annotation.AnnotationViewModel
import com.violinsheet.feature.annotation.EraserMemory
import com.violinsheet.feature.annotation.edit.symbol.FakeSymbolDao
import com.violinsheet.feature.annotation.edit.symbol.SymbolKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Edit mode's multi-selection (#27 part 5): a loop selects the Symbols wholly inside it on its Page; several move,
 * delete and recolour together, one undo step each, and undo or redo leaves them selected. The loop gesture itself is
 * part 6's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditLassoTest {
    private class NoInk : InkDao {
        override suspend fun forSheet(sheetId: Long) = emptyList<InkStrokeRow>()
        override suspend fun insert(rows: List<InkStrokeRow>) = Unit
        override suspend fun delete(ids: List<Long>) = Unit
        override suspend fun idsOnOtherSheets(ids: List<Long>, sheetId: Long) = emptyList<Long>()
    }

    private class NoSettings : SettingDao {
        override fun observe(key: String) = flowOf<String?>(null)
        override suspend fun upsert(value: SettingValue) = Unit
        override suspend fun delete(key: String) = Unit
    }

    private val sheetId = 7L
    private val symbolDao = FakeSymbolDao()
    private var nextId = 100L
    private val pageSize = Size(200f, 300f)
    private val reach = 2f
    private val black = 0xFF000000.toInt()
    private val blue = 0xFF1565C0.toInt()

    @BeforeTest fun mainDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest fun resetMainDispatcher() = Dispatchers.resetMain()

    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    /** A loaded ViewModel in Edit mode with the Symbol tool, Pages 0 and 1 on screen (a spread). */
    private fun TestScope.editing(): AnnotationViewModel {
        val factory = viewModelFactory {
            initializer {
                AnnotationViewModel(
                    sheetId, InkRepository(NoInk(), backgroundScope), SymbolRepository(symbolDao, backgroundScope),
                    SettingsStore(NoSettings(), backgroundScope), EraserMemory(),
                ) { nextId++ }
            }
        }
        val vm = ViewModelProvider.create(ViewModelStore(), factory)[AnnotationViewModel::class]
        settle()
        vm.onVisiblePages(mapOf(0 to pageSize, 1 to pageSize))
        vm.setMode(Mode.Edit)
        return vm
    }

    /** A Symbol-tool tap at [at] on Page [page]: places a fingering there. */
    private fun AnnotationViewModel.tap(at: Offset, page: Int = 0) {
        edit.penDown(page, at, pageSize, reach, FrameHit.Outside)
        edit.penUp(at, moved = false, cancelled = false)
    }

    /** A rectangular loop on Page [page]. */
    private fun AnnotationViewModel.loop(left: Float, top: Float, right: Float, bottom: Float, page: Int = 0) =
        edit.loopDone(page, listOf(Offset(left, top), Offset(right, top), Offset(right, bottom), Offset(left, bottom)))

    /** A pen drag by [delta] from [from], starting on [hit] of the selection's frame. */
    private fun AnnotationViewModel.drag(from: Offset, delta: Offset, hit: FrameHit = FrameHit.Inside) {
        edit.penDown(0, from, pageSize, reach, hit)
        edit.dragTo(from + delta)
        edit.penUp(from + delta, moved = true, cancelled = false)
    }

    private fun AnnotationViewModel.tool(tool: EditTool) = setEditTools(editTools.value.copy(chosen = tool))

    private fun AnnotationViewModel.selectedIds() = symbolSelection.value?.ids

    private fun AnnotationViewModel.centres() = symbols.value.map { Offset(it.x, it.y) }

    private fun near(expected: Float, actual: Float) = assertTrue(abs(expected - actual) < 0.01f, "expected $expected, was $actual")

    /** Two fingerings at (50, 60) and (70, 60) on Page 0, both selected by a loop. */
    private fun TestScope.twoSelected(): AnnotationViewModel {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        vm.tap(Offset(70f, 60f))
        vm.loop(40f, 50f, 80f, 70f)
        settle()
        return vm
    }

    @Test fun aLoopSelectsOnlySymbolsWhollyInsideItOnItsPage() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        vm.tap(Offset(70f, 60f))
        vm.tap(Offset(80f, 60f)) // its box straddles the loop's right edge
        vm.tap(Offset(150f, 60f))
        vm.tap(Offset(50f, 60f), page = 1) // the same spot on the other Page of the spread
        val ids = vm.symbols.value.map { it.id }
        vm.loop(40f, 50f, 80f, 70f)
        val selection = vm.symbolSelection.value!!
        assertEquals(setOf(ids[0], ids[1]), selection.ids)
        assertEquals(0, selection.pageIndex)
        assertFalse(selection.single)
        // The frame is the union of the two glyph boxes.
        val a = vm.symbols.value[0]
        val b = vm.symbols.value[1]
        near((a.x + b.x) / 2, selection.frame.center.x)
        assertTrue(selection.frame.halfSize.width > (b.x - a.x) / 2)
    }

    @Test fun aLoopCatchingNothingClearsTheSelection() = runTest {
        val vm = twoSelected()
        vm.loop(100f, 100f, 150f, 150f)
        assertNull(vm.symbolSelection.value)
        assertEquals(2, vm.symbols.value.size)
    }

    @Test fun severalMoveTogetherInOneUndoStepAndStaySelected() = runTest {
        val vm = twoSelected()
        val ids = vm.selectedIds()
        vm.drag(Offset(60f, 60f), Offset(10f, 20f))
        settle()
        assertEquals(listOf(Offset(60f, 80f), Offset(80f, 80f)), vm.centres())
        assertEquals(listOf(80f, 80f), symbolDao.rows.map { it.y })
        assertEquals(ids, vm.selectedIds())
        assertNull(vm.symbolSelection.value!!.preview)
        vm.undo()
        settle()
        assertEquals(listOf(Offset(50f, 60f), Offset(70f, 60f)), vm.centres(), "one step moves both back")
        assertEquals(listOf(60f, 60f), symbolDao.rows.map { it.y })
        assertEquals(ids, vm.selectedIds())
        vm.redo()
        assertEquals(listOf(Offset(60f, 80f), Offset(80f, 80f)), vm.centres())
        assertEquals(ids, vm.selectedIds())
    }

    @Test fun severalStopAsAGroupWhenOneReachesThePageEdge() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        vm.tap(Offset(150f, 60f))
        vm.loop(40f, 50f, 160f, 70f)
        val right = vm.symbols.value[1]
        vm.drag(Offset(100f, 60f), Offset(100f, 0f))
        // The right one stops with its box at the edge; the left one moves as far, no further.
        val shift = pageSize.width - SymbolKind.Fingering1.boxOnPage(right.x, right.y, 1f).right
        assertTrue(shift < 100f)
        near(right.x + shift, vm.symbols.value[1].x)
        near(50f + shift, vm.symbols.value[0].x)
        assertEquals(listOf(60f, 60f), vm.symbols.value.map { it.y })
    }

    @Test fun aCornerOfSeveralMovesThemAndNeverResizes() = runTest {
        val vm = twoSelected()
        vm.drag(Offset(80f, 70f), Offset(10f, 10f), hit = FrameHit.Corner(2))
        assertEquals(listOf(Offset(60f, 70f), Offset(80f, 70f)), vm.centres())
        assertEquals(listOf(1f, 1f), vm.symbols.value.map { it.scale })
    }

    @Test fun aStillTapOnEmptySpaceInsideTheFrameOfSeveralChangesNothing() = runTest {
        val vm = twoSelected()
        val ids = vm.selectedIds()
        val writes = symbolDao.writes
        vm.edit.penDown(0, Offset(60f, 60f), pageSize, reach, FrameHit.Inside)
        vm.edit.penUp(Offset(60f, 60f), moved = false, cancelled = false)
        settle()
        assertEquals(ids, vm.selectedIds())
        assertEquals(2, vm.symbols.value.size, "no Symbol placed")
        assertEquals(writes, symbolDao.writes)
    }

    @Test fun deletingSeveralIsOneStepAndUndoBringsThemBackSelected() = runTest {
        val vm = twoSelected()
        val ids = vm.selectedIds()
        vm.edit.deleteSelection()
        settle()
        assertTrue(vm.symbols.value.isEmpty())
        assertTrue(symbolDao.rows.isEmpty())
        assertNull(vm.symbolSelection.value)
        vm.undo()
        settle()
        assertEquals(2, vm.symbols.value.size)
        assertEquals(2, symbolDao.rows.size)
        assertEquals(ids, vm.selectedIds())
    }

    @Test fun recolouringSeveralIsOneStepAndKeepsThemSelected() = runTest {
        val vm = twoSelected()
        val ids = vm.selectedIds()
        vm.recolourSymbols(blue)
        settle()
        assertEquals(listOf(blue, blue), vm.symbols.value.map { it.colour })
        assertEquals(listOf(blue, blue), symbolDao.rows.map { it.colour })
        assertEquals(ids, vm.selectedIds())
        vm.undo()
        settle()
        assertEquals(listOf(black, black), vm.symbols.value.map { it.colour }, "one step takes both back")
        assertEquals(listOf(black, black), symbolDao.rows.map { it.colour })
        assertEquals(ids, vm.selectedIds())
        vm.redo()
        assertEquals(listOf(blue, blue), vm.symbols.value.map { it.colour })
        assertEquals(ids, vm.selectedIds())
    }

    @Test fun recolouringToTheColourTheyHaveIsNoStep() = runTest {
        val vm = twoSelected()
        val writes = symbolDao.writes
        vm.recolourSymbols(black)
        settle()
        assertEquals(writes, symbolDao.writes)
        vm.undo()
        assertEquals(1, vm.symbols.value.size, "the undo took the second placement, not a recolour")
    }

    @Test fun recolourWithNothingSelectedOrDuringADragDoesNothing() = runTest {
        val vm = twoSelected()
        vm.edit.clearSelection()
        vm.recolourSymbols(blue)
        assertEquals(listOf(black, black), vm.symbols.value.map { it.colour })
        vm.loop(40f, 50f, 80f, 70f)
        vm.edit.penDown(0, Offset(60f, 60f), pageSize, reach, FrameHit.Inside)
        vm.edit.dragTo(Offset(70f, 70f))
        vm.recolourSymbols(blue)
        vm.edit.penUp(Offset(70f, 70f), moved = true, cancelled = false)
        assertEquals(listOf(black, black), vm.symbols.value.map { it.colour })
        assertEquals(listOf(Offset(60f, 70f), Offset(80f, 70f)), vm.centres(), "the drag still lands")
    }

    @Test fun undoOfAnEraseOfSeveralReselectsThemAll() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        vm.tap(Offset(70f, 60f))
        val ids = vm.symbols.value.mapTo(LinkedHashSet()) { it.id }
        vm.edit.beginErase()
        vm.edit.eraseAt(0, Offset(40f, 60f), reach)
        vm.edit.eraseAt(0, Offset(80f, 60f), reach)
        vm.edit.endErase(cancelled = false)
        assertTrue(vm.symbols.value.isEmpty())
        vm.undo()
        assertEquals(ids, vm.selectedIds())
    }

    @Test fun undoOfAnEraseAcrossBothPagesOfASpreadSelectsNone() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        vm.tap(Offset(50f, 60f), page = 1)
        vm.edit.beginErase()
        vm.edit.eraseAt(0, Offset(50f, 60f), reach)
        vm.edit.eraseAt(1, Offset(50f, 60f), reach)
        vm.edit.endErase(cancelled = false)
        vm.undo()
        assertEquals(2, vm.symbols.value.size)
        assertNull(vm.symbolSelection.value)
    }

    @Test fun severalAreReselectedOnlyWithTheSymbolOrLassoToolAndTheirPageShown() = runTest {
        val vm = twoSelected()
        val ids = vm.selectedIds()
        vm.drag(Offset(60f, 60f), Offset(10f, 0f))
        vm.tool(EditTool.Eraser)
        vm.undo()
        assertNull(vm.symbolSelection.value, "no reselect with the Eraser tool")
        vm.tool(EditTool.Lasso)
        vm.redo()
        assertEquals(ids, vm.selectedIds())
        vm.onVisiblePages(mapOf(1 to pageSize))
        vm.undo()
        assertNull(vm.symbolSelection.value, "their Page is off the screen")
        assertEquals(listOf(Offset(50f, 60f), Offset(70f, 60f)), vm.centres())
    }

    @Test fun aSingleSelectionStillResizesFromACorner() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        assertTrue(vm.symbolSelection.value!!.single)
        val frame = vm.symbolSelection.value!!.frame
        val corner = frame.corners()[2]
        vm.drag(corner, Offset(5f, 5f), hit = FrameHit.Corner(2))
        assertTrue(vm.symbols.value.single().scale > 1f)
    }

    @Test fun aCornerOfSeveralIsInsideForTheHitTest() {
        val placement = PagePlacement(0, Size(600f, 800f), scale = 2f, offset = Offset(10f, 20f))
        val frame = SelectionFrame(Offset(100f, 100f), Size(50f, 30f), 0f)
        val corner = placement.toScreen(frame.corners()[0])
        fun hit(resizable: Boolean) = symbolFrameHit(frame, placement, corner, 24f, 8f, 24f, resizable)
        assertEquals(FrameHit.Corner(0), hit(resizable = true))
        assertEquals(FrameHit.Inside, hit(resizable = false))
    }

    /** Two fingerings at (50, 60) and (70, 60), then the Lasso tool with both selected by a loop. */
    private fun TestScope.twoLassoed(): AnnotationViewModel {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        vm.tap(Offset(70f, 60f))
        vm.tool(EditTool.Lasso)
        vm.loop(40f, 50f, 80f, 70f)
        settle()
        return vm
    }

    private fun AnnotationViewModel.down(at: Offset, hit: FrameHit = FrameHit.Outside) = edit.penDown(0, at, pageSize, reach, hit)

    @Test fun withTheLassoAPenDownOnEmptyPageClearsTheSelectionAndStartsALoop() = runTest {
        val vm = twoLassoed()
        assertEquals(2, vm.selectedIds()?.size)
        assertTrue(vm.down(Offset(150f, 150f)), "a loop starts")
        assertNull(vm.symbolSelection.value)
        assertNull(vm.symbolPlacing.value, "the Lasso places nothing")
        vm.edit.penUp(Offset(150f, 150f), moved = false, cancelled = false)
        settle()
        assertEquals(2, vm.symbols.value.size)
        assertEquals(2, symbolDao.rows.size)
    }

    @Test fun withTheLassoAPenDownOnAnUnselectedSymbolSelectsItAloneAndDragsItInOneGo() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        vm.tap(Offset(120f, 60f))
        val ids = vm.symbols.value.map { it.id }
        vm.tool(EditTool.Lasso)
        vm.loop(40f, 50f, 60f, 70f)
        assertEquals(setOf(ids[0]), vm.selectedIds())
        assertFalse(vm.down(Offset(120f, 60f)), "no loop on a Symbol")
        assertEquals(setOf(ids[1]), vm.selectedIds())
        vm.edit.dragTo(Offset(130f, 60f))
        vm.edit.penUp(Offset(130f, 60f), moved = true, cancelled = false)
        assertEquals(listOf(Offset(50f, 60f), Offset(130f, 60f)), vm.centres())
        assertEquals(setOf(ids[1]), vm.selectedIds())
    }

    @Test fun withTheLassoAPenDownInsideTheFrameOfSeveralMovesThemAndStartsNoLoop() = runTest {
        val vm = twoLassoed()
        assertFalse(vm.down(Offset(60f, 60f), FrameHit.Inside))
        vm.edit.dragTo(Offset(60f, 70f))
        vm.edit.penUp(Offset(60f, 70f), moved = true, cancelled = false)
        assertEquals(listOf(Offset(50f, 70f), Offset(70f, 70f)), vm.centres())
        assertEquals(2, vm.selectedIds()?.size)
    }

    @Test fun onlyTheLassoStartsALoop() = runTest {
        val vm = editing()
        assertFalse(vm.down(Offset(150f, 150f)), "the Symbol tool places")
        vm.edit.clearSelection()
        for (tool in listOf(EditTool.Eraser, EditTool.Cover)) {
            vm.tool(tool)
            assertFalse(vm.down(Offset(150f, 150f)), "$tool")
        }
        vm.tool(EditTool.Lasso)
        vm.setMode(Mode.Ink)
        assertFalse(vm.down(Offset(150f, 150f)), "not in Ink mode")
    }
}
