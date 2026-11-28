package com.violinsheet.feature.annotation

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
import com.violinsheet.core.ink.DragKind
import com.violinsheet.core.ink.EraseMode
import com.violinsheet.core.ink.Eraser
import com.violinsheet.core.ink.FrameHit
import com.violinsheet.core.ink.InkPoint
import com.violinsheet.core.ink.InkStroke
import com.violinsheet.core.ink.StrokeTool
import com.violinsheet.feature.annotation.edit.Mode
import com.violinsheet.feature.annotation.edit.symbol.FakeSymbolDao
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A finger tap on the Sheet ends the selection (#27 part 4), but never a pen gesture that is running. */
@OptIn(ExperimentalCoroutinesApi::class)
class FingerTapTest {
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

    private val symbolDao = FakeSymbolDao()
    private var nextId = 100L
    private val pageSize = Size(200f, 300f)
    private val reach = 2f

    @BeforeTest fun mainDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest fun resetMainDispatcher() = Dispatchers.resetMain()

    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    private fun TestScope.viewModel(): AnnotationViewModel {
        val factory = viewModelFactory {
            initializer {
                AnnotationViewModel(
                    7L, InkRepository(NoInk(), backgroundScope), SymbolRepository(symbolDao, backgroundScope),
                    SettingsStore(NoSettings(), backgroundScope), EraserMemory(),
                ) { nextId++ }
            }
        }
        val vm = ViewModelProvider.create(ViewModelStore(), factory)[AnnotationViewModel::class]
        settle()
        vm.onVisiblePages(mapOf(0 to pageSize))
        return vm
    }

    /** Ink mode, Lasso tool, stroke 2000 along y = 50 selected. */
    private fun TestScope.inkSelected(): AnnotationViewModel {
        val vm = viewModel()
        vm.addStrokes(listOf(InkStroke(2000, 0, 0xFF000000.toInt(), 1f, List(101) { InkPoint(it.toFloat(), 50f, 0.5f, it.toLong()) }, StrokeTool.Pen)))
        vm.setTools(vm.tools.value.copy(chosen = ToolChoice.Lasso))
        settle()
        vm.select(0, listOf(2000L))
        assertEquals(listOf(2000L), vm.selection.value?.ids)
        return vm
    }

    /** Edit mode, Symbol tool, one fingering placed at (50, 60) and selected. */
    private fun TestScope.symbolSelected(): AnnotationViewModel {
        val vm = viewModel()
        vm.setMode(Mode.Edit)
        vm.edit.penDown(0, Offset(50f, 60f), pageSize, reach, FrameHit.Outside)
        vm.edit.penUp(Offset(50f, 60f), moved = false, cancelled = false)
        settle()
        assertNotNull(vm.symbolSelection.value)
        return vm
    }

    @Test fun aTapClearsAnInkLassoSelection() = runTest {
        val vm = inkSelected()
        vm.onFingerTap()
        assertNull(vm.selection.value)
    }

    @Test fun aTapClearsASymbolSelection() = runTest {
        val vm = symbolSelected()
        vm.onFingerTap()
        assertNull(vm.symbolSelection.value)
        assertEquals(1, vm.symbols.value.size, "only the selection ends; the Symbol stays")
    }

    @Test fun anEdgeTapClearsNothingItself() = runTest {
        val ink = inkSelected()
        ink.onFingerTap(atEdge = true)
        assertEquals(listOf(2000L), ink.selection.value?.ids)
        val edit = symbolSelected()
        edit.onFingerTap(atEdge = true)
        assertNotNull(edit.symbolSelection.value)
    }

    @Test fun aTurnThatKeepsTheSelectionsPageOnScreenKeepsTheSelection() = runTest {
        val vm = viewModel()
        vm.onVisiblePages(mapOf(0 to pageSize, 1 to pageSize)) // a landscape spread
        vm.setMode(Mode.Edit)
        vm.edit.penDown(1, Offset(50f, 60f), pageSize, reach, FrameHit.Outside)
        vm.edit.penUp(Offset(50f, 60f), moved = false, cancelled = false)
        settle()
        assertEquals(1, vm.symbolSelection.value?.pageIndex)
        // A right-edge tap with advance-by-one: the tap, then the turn to Pages 1-2.
        vm.onFingerTap(atEdge = true)
        vm.onVisiblePages(mapOf(1 to pageSize, 2 to pageSize))
        assertNotNull(vm.symbolSelection.value, "its Page is still shown")
        vm.onFingerTap(atEdge = true)
        vm.onVisiblePages(mapOf(2 to pageSize, 3 to pageSize))
        assertNull(vm.symbolSelection.value, "its Page left the screen")
    }

    @Test fun aTapWithNoSelectionDoesNothing() = runTest {
        val vm = viewModel()
        vm.onFingerTap()
        assertNull(vm.selection.value)
        assertNull(vm.symbolSelection.value)
    }

    @Test fun aTapDuringAnInkLassoDragKeepsTheDragAndSelection() = runTest {
        val vm = inkSelected()
        vm.beginDrag(DragKind.Move, Offset(50f, 50f), pageSize)
        vm.dragTo(Offset(60f, 70f))
        vm.onFingerTap()
        vm.endDrag(cancelled = false)
        settle()
        assertEquals(Offset(60f, 70f), vm.selection.value?.frame?.center)
        assertEquals(InkPoint(10f, 70f, 0.5f, 0), vm.strokes.value.single().points.first())
    }

    @Test fun aTapDuringASymbolDragKeepsTheDragAndSelection() = runTest {
        val vm = symbolSelected()
        val id = vm.symbolSelection.value!!.ids.single()
        vm.edit.penDown(0, Offset(50f, 60f), pageSize, reach, FrameHit.Inside)
        vm.edit.dragTo(Offset(80f, 90f))
        vm.onFingerTap()
        assertEquals(id, vm.symbolSelection.value?.ids?.single())
        vm.edit.penUp(Offset(80f, 90f), moved = true, cancelled = false)
        settle()
        val s = vm.symbols.value.single()
        assertEquals(Offset(80f, 90f), Offset(s.x, s.y))
        assertEquals(id, vm.symbolSelection.value?.ids?.single())
    }

    @Test fun aTapDuringAPlacementKeepsIt() = runTest {
        val vm = viewModel()
        vm.setMode(Mode.Edit)
        vm.edit.penDown(0, Offset(50f, 60f), pageSize, reach, FrameHit.Outside)
        vm.edit.dragTo(Offset(70f, 80f))
        vm.onFingerTap()
        assertNotNull(vm.symbolPlacing.value)
        vm.edit.penUp(Offset(70f, 80f), moved = true, cancelled = false)
        settle()
        val s = vm.symbols.value.single()
        assertEquals(Offset(70f, 80f), Offset(s.x, s.y))
        assertEquals(s.id, vm.symbolSelection.value?.ids?.single())
    }

    @Test fun aTapDuringASymbolEraseLetsItFinish() = runTest {
        val vm = symbolSelected()
        vm.edit.beginErase()
        vm.onFingerTap()
        vm.edit.eraseAt(0, Offset(50f, 60f), reach)
        vm.edit.endErase(cancelled = false)
        settle()
        assertTrue(vm.symbols.value.isEmpty())
        vm.undo()
        assertEquals(1, vm.symbols.value.size, "the erase is one undo step")
    }

    @Test fun aTapDuringAnInkEraseLetsItFinish() = runTest {
        val vm = inkSelected()
        vm.beginErase(Eraser(EraseMode.Stroke, 4f))
        vm.onFingerTap()
        vm.eraseAt(0, Offset(50f, 48f))
        vm.eraseAt(0, Offset(50f, 52f))
        vm.endErase(cancelled = false)
        settle()
        assertTrue(vm.strokes.value.isEmpty())
    }
}
