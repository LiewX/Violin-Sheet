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
import com.violinsheet.core.data.symbol.Symbol
import com.violinsheet.core.data.symbol.SymbolRepository
import com.violinsheet.core.data.symbol.SymbolRow
import com.violinsheet.core.ink.FrameHit
import com.violinsheet.core.ink.InkPoint
import com.violinsheet.core.ink.InkStroke
import com.violinsheet.core.ink.StrokeTool
import com.violinsheet.feature.annotation.AnnotationViewModel
import com.violinsheet.feature.annotation.EraserMemory
import com.violinsheet.feature.annotation.ToolChoice
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

@OptIn(ExperimentalCoroutinesApi::class)
class SymbolEditingTest {
    private class FakeInkDao : InkDao {
        val rows = mutableListOf<InkStrokeRow>()
        override suspend fun forSheet(sheetId: Long) = rows.filter { it.sheetId == sheetId }.sortedBy { it.id }
        override suspend fun insert(rows: List<InkStrokeRow>) {
            val ids = rows.map { it.id }.toSet()
            this.rows.removeAll { it.id in ids }
            this.rows += rows
        }
        override suspend fun delete(ids: List<Long>) {
            rows.removeAll { it.id in ids }
        }
        override suspend fun idsOnOtherSheets(ids: List<Long>, sheetId: Long) = emptyList<Long>()
    }

    private class FakeSettings : SettingDao {
        override fun observe(key: String) = flowOf<String?>(null)
        override suspend fun upsert(value: SettingValue) = Unit
        override suspend fun delete(key: String) = Unit
    }

    private val sheetId = 7L
    private val inkDao = FakeInkDao()
    private val symbolDao = FakeSymbolDao()
    private var nextId = 100L
    private val pageSize = Size(200f, 300f)

    /** Fingering 1's glyph box on the Page at scale 1, from Bravura's metadata: 0.468 × 1.016 staff spaces of 5 pt. */
    private val halfWidth = 0.468f * 5 / 2
    private val halfHeight = 1.016f * 5 / 2

    private val reach = 2f

    @BeforeTest fun mainDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest fun resetMainDispatcher() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): AnnotationViewModel {
        val factory = viewModelFactory {
            initializer {
                AnnotationViewModel(
                    sheetId, InkRepository(inkDao, backgroundScope), SymbolRepository(symbolDao, backgroundScope),
                    SettingsStore(FakeSettings(), backgroundScope), EraserMemory(),
                ) { nextId++ }
            }
        }
        return ViewModelProvider.create(ViewModelStore(), factory)[AnnotationViewModel::class]
    }

    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    /** A loaded ViewModel in Edit mode with the Symbol tool, Page 0 on screen. */
    private fun TestScope.editing(): AnnotationViewModel {
        val vm = viewModel()
        settle()
        vm.onVisiblePages(mapOf(0 to pageSize))
        vm.setMode(Mode.Edit)
        return vm
    }

    private fun AnnotationViewModel.tap(at: Offset, hit: FrameHit = FrameHit.Outside, page: Int = 0) {
        edit.penDown(page, at, pageSize, reach, hit)
        edit.penUp(at, moved = false, cancelled = false)
    }

    private fun AnnotationViewModel.drag(from: Offset, to: Offset, hit: FrameHit = FrameHit.Outside, cancelled: Boolean = false) {
        edit.penDown(0, from, pageSize, reach, hit)
        edit.dragTo(to)
        edit.penUp(to, moved = true, cancelled = cancelled)
    }

    private fun AnnotationViewModel.only(): Symbol = symbols.value.single()

    private fun AnnotationViewModel.selectedId() = symbolSelection.value?.id

    private fun AnnotationViewModel.tool(tool: EditTool) = setEditTools(editTools.value.copy(chosen = tool))

    private fun near(expected: Float, actual: Float) = assertTrue(abs(expected - actual) < 1e-3f, "expected $expected, got $actual")

    private fun stroke(id: Long) = InkStroke(id, 0, 0xFF000000.toInt(), 1f, listOf(InkPoint(1f, 1f, 0.5f, 0)), StrokeTool.Pen)

    private fun row(id: Long, kind: String, x: Float, y: Float) = SymbolRow(id, sheetId, 0, kind, x, y, 1f, 0xFF000000.toInt(), null, null, null)

    private fun savedSymbols() = symbolDao.rows.map { Triple(it.id, it.x, it.y) }

    @Test fun aTapPlacesFingeringOneCentredThereSelectedAndSaved() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        settle()
        val s = vm.only()
        assertEquals("fingering.1", s.kind)
        assertEquals(Offset(50f, 60f), Offset(s.x, s.y))
        assertEquals(1f, s.scale)
        assertEquals(s.id, vm.selectedId())
        assertEquals(listOf(Triple(s.id, 50f, 60f)), savedSymbols())
        assertTrue(vm.canUndo.value)
    }

    @Test fun aSymbolPlacedAtThePageEdgeIsKeptInside() = runTest {
        val vm = editing()
        vm.tap(Offset(199.5f, 0.5f))
        val s = vm.only()
        near(200f - halfWidth, s.x)
        near(halfHeight, s.y)
    }

    @Test fun aTapOnEmptyPageWhileASymbolIsSelectedPlacesAnother() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        vm.tap(Offset(100f, 60f))
        assertEquals(2, vm.symbols.value.size)
        assertEquals(vm.symbols.value.last().id, vm.selectedId())
    }

    @Test fun aTapOnASymbolSelectsItAndPlacesNothing() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        vm.edit.clearSelection()
        vm.tap(Offset(51f, 62f))
        assertEquals(1, vm.symbols.value.size)
        assertEquals(vm.only().id, vm.selectedId())
        vm.undo()
        assertTrue(vm.symbols.value.isEmpty())
        assertFalse(vm.canUndo.value)
    }

    @Test fun ofTwoOverlappingSymbolsTheTopmostIsPicked() = runTest {
        symbolDao.rows += row(10, "fingering.2", 50f, 60f)
        symbolDao.rows += row(20, "fingering.3", 50f, 60f)
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        assertEquals(20L, vm.selectedId())
        assertEquals(2, vm.symbols.value.size)
    }

    @Test fun penDownOnAnUnselectedSymbolThenADragMovesItInOneGesture() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        vm.edit.clearSelection()
        vm.drag(Offset(50f, 60f), Offset(70f, 80f))
        settle()
        val s = vm.only()
        assertEquals(Offset(70f, 80f), Offset(s.x, s.y))
        assertEquals(s.id, vm.selectedId())
        assertEquals(listOf(Triple(s.id, 70f, 80f)), savedSymbols())
        vm.undo()
        assertEquals(Offset(50f, 60f), Offset(vm.only().x, vm.only().y))
        assertEquals(s.id, vm.selectedId())
    }

    @Test fun aStillLiftOnAnUnselectedSymbolOnlySelectsIt() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        vm.edit.clearSelection()
        vm.edit.penDown(0, Offset(50f, 60f), pageSize, reach, FrameHit.Outside)
        vm.edit.dragTo(Offset(50.5f, 60f)) // jitter within the tap slop
        vm.edit.penUp(Offset(50.5f, 60f), moved = false, cancelled = false)
        assertEquals(Offset(50f, 60f), Offset(vm.only().x, vm.only().y))
        assertEquals(vm.only().id, vm.selectedId())
        vm.undo()
        assertTrue(vm.symbols.value.isEmpty(), "the only step was the place")
    }

    @Test fun aMoveStopsAtThePageEdge() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        vm.drag(Offset(50f, 60f), Offset(550f, -500f), FrameHit.Inside)
        near(200f - halfWidth, vm.only().x)
        near(halfHeight, vm.only().y)
    }

    @Test fun aResizeScalesAboutTheOppositeCorner() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        val corner = Offset(50f + halfWidth, 60f + halfHeight)
        vm.drag(corner, corner + Offset(2 * halfWidth, 2 * halfHeight), FrameHit.Corner(2))
        val s = vm.only()
        near(2f, s.scale)
        near(50f + halfWidth, s.x)
        near(60f + halfHeight, s.y)
        assertEquals(s.id, vm.selectedId())
        near(2 * halfWidth, vm.symbolSelection.value!!.frame.halfSize.width)
    }

    @Test fun aResizeStopsAtThePageEdge() = runTest {
        val vm = editing()
        vm.tap(Offset(190f, 60f))
        val corner = Offset(190f + halfWidth, 60f + halfHeight)
        vm.drag(corner, corner + Offset(100f, 100f), FrameHit.Corner(2))
        val s = vm.only()
        // The top-left corner stays; the right edge reaches the Page's.
        near(200f, s.x + halfWidth * s.scale)
        near(60f - halfHeight, s.y - halfHeight * s.scale)
    }

    @Test fun aResizeKeepsTheTwoPointMinimum() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        val corner = Offset(50f + halfWidth, 60f + halfHeight)
        vm.drag(corner, corner - Offset(50f, 50f), FrameHit.Corner(2))
        // The shorter side (the width, 2.34 pt) stops at 2 pt.
        near(2f, 2 * halfWidth * vm.only().scale)
    }

    @Test fun aResizeFollowsThePenOnTheFrameAsShown() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        // The frame is shown 24 pt square (the minimum side), much bigger than the 2.3 × 5.1 pt glyph box. The pen
        // grabs its shown bottom-right corner (62, 72) and moves 50 pt out along the diagonal: the shown frame's
        // corner follows to 74 pt from its anchor, ×(74 / 24). The glyph box's own corner would give about ×13.
        val shownCorner = Offset(62f, 72f)
        vm.edit.penDown(0, shownCorner, pageSize, reach, FrameHit.Corner(2), frameMinSide = 24f)
        vm.edit.dragTo(shownCorner + Offset(50f, 50f))
        vm.edit.penUp(shownCorner + Offset(50f, 50f), moved = true, cancelled = false)
        val s = vm.only()
        near(74f / 24f, s.scale)
        assertTrue(s.scale < 4f)
        // About the glyph box's own top-left corner, which stays.
        near(50f - halfWidth, s.x - halfWidth * s.scale)
        near(60f - halfHeight, s.y - halfHeight * s.scale)
    }

    @Test fun aResizeOnTheShownFrameKeepsThePageEdgeAndTwoPointLimits() = runTest {
        val vm = editing()
        vm.tap(Offset(190f, 60f))
        vm.edit.penDown(0, Offset(202f, 72f), pageSize, reach, FrameHit.Corner(2), frameMinSide = 24f)
        vm.edit.dragTo(Offset(302f, 172f))
        vm.edit.penUp(Offset(302f, 172f), moved = true, cancelled = false)
        near(200f, vm.only().x + halfWidth * vm.only().scale)
        vm.undo()
        vm.edit.clearSelection()
        vm.tap(Offset(190f, 60f))
        vm.edit.penDown(0, Offset(202f, 72f), pageSize, reach, FrameHit.Corner(2), frameMinSide = 24f)
        vm.edit.dragTo(Offset(150f, 20f))
        vm.edit.penUp(Offset(150f, 20f), moved = true, cancelled = false)
        near(2f, 2 * halfWidth * vm.only().scale)
    }

    @Test fun withTheSelectToolATapOnEmptyPageClearsTheSelection() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        vm.tool(EditTool.Select)
        vm.edit.penDown(0, Offset(50f, 60f), pageSize, reach, FrameHit.Outside)
        vm.edit.penUp(Offset(50f, 60f), moved = false, cancelled = false)
        assertEquals(vm.only().id, vm.selectedId())
        vm.tap(Offset(150f, 200f))
        assertNull(vm.selectedId())
        assertEquals(1, vm.symbols.value.size, "the Select tool places nothing")
    }

    @Test fun deleteRemovesTheSelectedSymbolAndUndoBringsItBackSelected() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        val s = vm.only()
        vm.tool(EditTool.Select)
        vm.tap(Offset(50f, 60f))
        vm.deleteSelection()
        settle()
        assertTrue(vm.symbols.value.isEmpty())
        assertTrue(symbolDao.rows.isEmpty())
        vm.undo()
        settle()
        assertEquals(s, vm.only())
        assertEquals(s.id, vm.selectedId())
        assertEquals(listOf(Triple(s.id, 50f, 60f)), savedSymbols())
        vm.redo()
        assertTrue(vm.symbols.value.isEmpty())
        assertNull(vm.selectedId())
    }

    @Test fun oneEraserSweepRemovesSeveralSymbolsAsOneStepAndLeavesInk() = runTest {
        val vm = editing()
        vm.addStrokes(listOf(stroke(1)))
        vm.tap(Offset(20f, 20f))
        vm.tap(Offset(40f, 20f))
        vm.tap(Offset(100f, 100f))
        vm.tool(EditTool.Eraser)
        vm.edit.beginErase()
        vm.edit.eraseAt(0, Offset(10f, 20f), reach)
        vm.edit.eraseAt(0, Offset(50f, 20f), reach) // the sweep between passes over both
        vm.edit.endErase(cancelled = false)
        settle()
        assertEquals(listOf(Offset(100f, 100f)), vm.symbols.value.map { Offset(it.x, it.y) })
        assertEquals(1, symbolDao.rows.size)
        assertEquals(listOf(1L), vm.strokes.value.map { it.id })
        vm.undo()
        settle()
        assertEquals(3, vm.symbols.value.size)
        assertEquals(3, symbolDao.rows.size)
        assertNull(vm.selectedId(), "no reselect with the Eraser tool")
        vm.undo()
        assertEquals(2, vm.symbols.value.size, "the next undo is the third place")
    }

    @Test fun anEraserThatLeavesThePageAndComesBackDoesNotSweepAcrossIt() = runTest {
        val vm = editing()
        vm.tap(Offset(100f, 20f))
        vm.tool(EditTool.Eraser)
        vm.edit.beginErase()
        vm.edit.eraseAt(0, Offset(10f, 20f), reach)
        vm.edit.eraserOffPage()
        vm.edit.eraseAt(0, Offset(190f, 20f), reach)
        vm.edit.endErase(cancelled = false)
        assertEquals(1, vm.symbols.value.size, "the Symbol midway was never touched")
        vm.edit.beginErase()
        vm.edit.eraseAt(0, Offset(10f, 20f), reach)
        vm.edit.eraseAt(0, Offset(190f, 20f), reach)
        vm.edit.endErase(cancelled = false)
        assertTrue(vm.symbols.value.isEmpty(), "on the Page all along, the sweep between takes it")
    }

    @Test fun aCancelledEraseChangesNothingAndSavesNothing() = runTest {
        val vm = editing()
        vm.tap(Offset(20f, 20f))
        settle()
        val writes = symbolDao.writes
        vm.tool(EditTool.Eraser)
        vm.edit.beginErase()
        vm.edit.eraseAt(0, Offset(20f, 20f), reach)
        assertTrue(vm.symbols.value.isEmpty(), "removal shows at once")
        vm.edit.endErase(cancelled = true)
        settle()
        assertEquals(1, vm.symbols.value.size)
        assertEquals(writes, symbolDao.writes)
        vm.undo()
        assertTrue(vm.symbols.value.isEmpty(), "the only step was the place")
    }

    @Test fun aCancelledDragChangesNothing() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        vm.drag(Offset(50f, 60f), Offset(90f, 90f), FrameHit.Inside, cancelled = true)
        assertEquals(Offset(50f, 60f), Offset(vm.only().x, vm.only().y))
        assertNull(vm.symbolSelection.value!!.preview)
        vm.undo()
        assertTrue(vm.symbols.value.isEmpty())
    }

    @Test fun aDragFromEmptyPageWithAGlyphKindPlacesNothing() = runTest {
        val vm = editing()
        vm.drag(Offset(50f, 60f), Offset(90f, 90f))
        assertTrue(vm.symbols.value.isEmpty())
        assertFalse(vm.canUndo.value)
    }

    @Test fun undoAndRedoOfPlaceAndMoveReselectPerTheTable() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        val id = vm.only().id
        vm.undo()
        assertNull(vm.selectedId())
        vm.redo()
        assertEquals(id, vm.selectedId())
        vm.drag(Offset(50f, 60f), Offset(70f, 60f), FrameHit.Inside)
        vm.undo()
        assertEquals(id, vm.selectedId())
        assertEquals(50f, vm.only().x)
        vm.redo()
        assertEquals(id, vm.selectedId())
        assertEquals(70f, vm.only().x)
    }

    @Test fun undoingAOneSymbolEraseReselectsItWithTheSelectTool() = runTest {
        val vm = editing()
        vm.tap(Offset(20f, 20f))
        vm.tool(EditTool.Eraser)
        vm.edit.beginErase()
        vm.edit.eraseAt(0, Offset(20f, 20f), reach)
        vm.edit.endErase(cancelled = false)
        vm.tool(EditTool.Select)
        vm.undo()
        assertEquals(vm.only().id, vm.selectedId())
    }

    @Test fun noReselectInInkModeOrOffScreen() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        vm.drag(Offset(50f, 60f), Offset(70f, 60f), FrameHit.Inside)
        vm.setMode(Mode.Ink)
        vm.undo()
        assertNull(vm.selectedId())
        vm.setMode(Mode.Edit)
        vm.onVisiblePages(mapOf(1 to pageSize))
        vm.redo()
        assertNull(vm.selectedId())
        assertEquals(70f, vm.only().x)
    }

    @Test fun undoWaitsDuringADragAndAnErase() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        vm.edit.penDown(0, Offset(50f, 60f), pageSize, reach, FrameHit.Inside)
        vm.edit.dragTo(Offset(70f, 60f))
        vm.undo()
        assertEquals(1, vm.symbols.value.size)
        vm.edit.penUp(Offset(70f, 60f), moved = true, cancelled = false)
        vm.tool(EditTool.Eraser)
        vm.edit.beginErase()
        vm.undo()
        vm.redo()
        vm.edit.endErase(cancelled = false)
        assertEquals(70f, vm.only().x, "neither undo nor redo ran")
    }

    @Test fun aPageTurnHidingThePageClearsTheSelection() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        vm.onVisiblePages(mapOf(0 to pageSize, 1 to pageSize))
        assertEquals(vm.only().id, vm.selectedId())
        vm.onVisiblePages(mapOf(1 to pageSize))
        assertNull(vm.selectedId())
    }

    @Test fun inkAndSymbolStepsInterleaveInOneHistory() = runTest {
        val vm = editing()
        vm.addStrokes(listOf(stroke(1)))
        vm.tap(Offset(50f, 60f))
        vm.addStrokes(listOf(stroke(2)))
        vm.undo()
        assertEquals(listOf(1L), vm.strokes.value.map { it.id })
        assertEquals(1, vm.symbols.value.size)
        vm.undo()
        assertTrue(vm.symbols.value.isEmpty())
        assertEquals(listOf(1L), vm.strokes.value.map { it.id })
        vm.undo()
        assertTrue(vm.strokes.value.isEmpty())
    }

    @Test fun aLoadRacingAPlacementKeepsThePlacedSymbol() = runTest {
        symbolDao.rows += row(5, "fingering.2", 20f, 20f)
        symbolDao.readAfterMillis = 500
        val vm = viewModel()
        runCurrent()
        vm.onVisiblePages(mapOf(0 to pageSize))
        vm.setMode(Mode.Edit)
        vm.tap(Offset(50f, 60f))
        settle()
        assertEquals(listOf(5L, 100L), vm.symbols.value.map { it.id })
        assertEquals(100L, vm.selectedId())
    }

    @Test fun anUnknownStoredKindIsNotShownAndNeverDeleted() = runTest {
        symbolDao.rows += row(5, "dynamic.mf", 20f, 20f)
        symbolDao.rows += row(6, "fingering.2", 20f, 20f)
        val vm = editing()
        assertEquals(listOf(6L), vm.symbols.value.map { it.id })
        vm.tool(EditTool.Eraser)
        vm.edit.beginErase()
        vm.edit.eraseAt(0, Offset(20f, 20f), reach)
        vm.edit.endErase(cancelled = false)
        settle()
        assertEquals(listOf(5L), symbolDao.rows.map { it.id })
    }

    @Test fun setModeClearsBothSelectionsAndASheetOpensInInkMode() = runTest {
        val vm = viewModel()
        settle()
        assertEquals(Mode.Ink, vm.mode.value)
        vm.onVisiblePages(mapOf(0 to pageSize))
        vm.addStrokes(listOf(stroke(1)))
        vm.setTools(vm.tools.value.copy(chosen = ToolChoice.Lasso))
        vm.select(0, listOf(1L))
        assertEquals(listOf(1L), vm.selection.value?.ids)
        vm.setMode(Mode.Edit)
        assertNull(vm.selection.value)
        vm.tap(Offset(50f, 60f))
        assertEquals(vm.only().id, vm.selectedId())
        vm.setMode(Mode.Ink)
        assertNull(vm.selectedId())
        assertEquals(Mode.Ink, viewModel().mode.value)
    }

    @Test fun inInkModeThePenPlacesNoSymbol() = runTest {
        val vm = viewModel()
        settle()
        vm.onVisiblePages(mapOf(0 to pageSize))
        vm.tap(Offset(50f, 60f))
        assertTrue(vm.symbols.value.isEmpty())
    }

    @Test fun changingTheEditToolClearsTheSymbolSelection() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        vm.tool(EditTool.Select)
        assertNull(vm.selectedId())
    }

    @Test fun aFailedSymbolWriteSetsSaveFailed() = runTest {
        symbolDao.failWrites = true
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        settle()
        assertTrue(vm.saveFailed.value)
    }

    @Test fun fromStoredKnowsTheFiveFingeringsOnly() {
        assertEquals(SymbolKind.Fingering3, SymbolKind.fromStored("fingering.3"))
        assertNull(SymbolKind.fromStored("fingering.5"))
        assertEquals(SymbolKind.entries, SymbolGroup.Fingering.kinds)
        assertTrue(SymbolGroup.Slur.kinds.isEmpty())
        // The group's box is a hand copy of Fingering 1's.
        assertEquals(SymbolKind.Fingering1.box, SymbolGroup.Fingering.representativeBox)
        assertEquals(SymbolKind.Fingering1.codepoint, SymbolGroup.Fingering.representative)
    }
}
