package com.violinsheet.feature.annotation.edit.symbol

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
import com.violinsheet.feature.annotation.AnnotationViewModel
import com.violinsheet.feature.annotation.EraserMemory
import com.violinsheet.feature.annotation.edit.Mode
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Drag-to-place (#27 part 2): pen down on empty Page with the Symbol tool shows the Symbol; the lift places it. */
@OptIn(ExperimentalCoroutinesApi::class)
class SymbolPlacingTest {
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

    /** Fingering 1's glyph box on the Page at scale 1 (Bravura: 0.468 × 1.016 staff spaces of 5 pt). */
    private val halfWidth = 0.468f * 5 / 2
    private val halfHeight = 1.016f * 5 / 2

    @BeforeTest fun mainDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest fun resetMainDispatcher() = Dispatchers.resetMain()

    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    /** A loaded ViewModel in Edit mode with the Symbol tool, Page 0 on screen. */
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
        vm.onVisiblePages(mapOf(0 to pageSize))
        vm.setMode(Mode.Edit)
        return vm
    }

    private fun AnnotationViewModel.down(at: Offset, hit: FrameHit = FrameHit.Outside) = edit.penDown(0, at, pageSize, reach, hit)

    private fun AnnotationViewModel.tap(at: Offset) {
        down(at)
        edit.penUp(at, moved = false, cancelled = false)
    }

    private fun AnnotationViewModel.preview() = assertNotNull(symbolPlacing.value)

    private fun near(expected: Float, actual: Float) = assertTrue(abs(expected - actual) < 1e-3f, "expected $expected, got $actual")

    @Test fun penDownOnEmptyPageShowsTheSymbolAtThePenAndSavesNothing() = runTest {
        val vm = editing()
        vm.down(Offset(50f, 60f))
        settle()
        val shown = vm.preview()
        assertEquals("fingering.1", shown.kind)
        assertEquals(Offset(50f, 60f), Offset(shown.x, shown.y))
        assertTrue(vm.symbols.value.isEmpty())
        assertFalse(vm.canUndo.value)
        assertEquals(0, symbolDao.writes)
    }

    @Test fun thePreviewIsKeptInsideThePageAtPenDown() = runTest {
        val vm = editing()
        vm.down(Offset(199.5f, 0.5f))
        near(200f - halfWidth, vm.preview().x)
        near(halfHeight, vm.preview().y)
    }

    @Test fun aDragFromEmptyPagePlacesAtTheLiftSelectedInOneStep() = runTest {
        val vm = editing()
        vm.down(Offset(50f, 60f))
        vm.edit.dragTo(Offset(70f, 80f))
        assertEquals(Offset(70f, 80f), Offset(vm.preview().x, vm.preview().y))
        vm.edit.dragTo(Offset(90f, 90f))
        assertTrue(vm.symbols.value.isEmpty())
        vm.edit.penUp(Offset(90f, 90f), moved = true, cancelled = false)
        settle()
        assertNull(vm.symbolPlacing.value)
        val s = vm.symbols.value.single()
        assertEquals(Offset(90f, 90f), Offset(s.x, s.y))
        assertEquals(s.id, vm.symbolSelection.value?.ids?.single())
        assertEquals(listOf(Triple(s.id, 90f, 90f)), symbolDao.rows.map { Triple(it.id, it.x, it.y) })
        assertEquals(1, symbolDao.writes)
        vm.undo()
        assertTrue(vm.symbols.value.isEmpty())
        assertNull(vm.symbolSelection.value)
        assertFalse(vm.canUndo.value)
        vm.redo()
        assertEquals(s, vm.symbols.value.single())
        assertEquals(s.id, vm.symbolSelection.value?.ids?.single())
    }

    @Test fun aDragPastThePageEdgeStopsThePlacementAtTheEdge() = runTest {
        val vm = editing()
        vm.down(Offset(50f, 60f))
        vm.edit.dragTo(Offset(260f, -40f))
        near(200f - halfWidth, vm.preview().x)
        near(halfHeight, vm.preview().y)
        vm.edit.penUp(Offset(260f, -40f), moved = true, cancelled = false)
        val s = vm.symbols.value.single()
        assertEquals(0, s.pageIndex)
        near(200f - halfWidth, s.x)
        near(halfHeight, s.y)
    }

    @Test fun aTapPlacesAtTheDownPoint() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        val s = vm.symbols.value.single()
        assertEquals(Offset(50f, 60f), Offset(s.x, s.y))
        assertNull(vm.symbolPlacing.value)
    }

    @Test fun aLostLiftPlacesWhereThePreviewWas() = runTest {
        val vm = editing()
        vm.down(Offset(50f, 60f))
        vm.edit.dragTo(Offset(70f, 80f))
        // EditPenLayer.abandon: the lift was never seen, so the last position stands and moved is assumed.
        vm.edit.penUp(Offset(75f, 85f), moved = true, cancelled = false)
        val s = vm.symbols.value.single()
        assertEquals(Offset(70f, 80f), Offset(s.x, s.y))
    }

    @Test fun aSystemCancelDropsThePreviewAndSavesNothing() = runTest {
        val vm = editing()
        vm.down(Offset(50f, 60f))
        vm.edit.dragTo(Offset(70f, 80f))
        vm.edit.penUp(Offset(70f, 80f), moved = true, cancelled = true)
        settle()
        assertNull(vm.symbolPlacing.value)
        assertTrue(vm.symbols.value.isEmpty())
        assertFalse(vm.canUndo.value)
        assertEquals(0, symbolDao.writes)
    }

    @Test fun undoWaitsWhilePlacing() = runTest {
        val vm = editing()
        vm.tap(Offset(20f, 20f))
        vm.down(Offset(50f, 60f))
        vm.undo()
        assertEquals(1, vm.symbols.value.size, "undo waited")
        assertNotNull(vm.symbolPlacing.value)
        vm.edit.penUp(Offset(50f, 60f), moved = false, cancelled = false)
        assertEquals(2, vm.symbols.value.size)
    }

    @Test fun penDownOnASymbolMovesItAndStartsNoPlacement() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        vm.edit.clearSelection()
        vm.down(Offset(50f, 60f))
        assertNull(vm.symbolPlacing.value)
        vm.edit.dragTo(Offset(70f, 60f))
        vm.edit.penUp(Offset(70f, 60f), moved = true, cancelled = false)
        val s = vm.symbols.value.single()
        assertEquals(70f, s.x)
        assertEquals(s.id, vm.symbolSelection.value?.ids?.single())
    }

    @Test fun penDownOnEmptyPageWithASymbolSelectedClearsItAndATapPlacesAnotherSelected() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        vm.down(Offset(100f, 60f))
        assertNull(vm.symbolSelection.value)
        vm.edit.penUp(Offset(100f, 60f), moved = false, cancelled = false)
        assertEquals(2, vm.symbols.value.size)
        assertEquals(vm.symbols.value.last().id, vm.symbolSelection.value?.ids?.single())
    }
}
