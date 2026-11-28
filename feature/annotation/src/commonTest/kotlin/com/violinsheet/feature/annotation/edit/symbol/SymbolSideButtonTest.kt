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
import com.violinsheet.feature.annotation.edit.EditTool
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The side button erases in Edit mode with any tool (#27 part 3): the pen layer sends beginErase whatever tool is
 * chosen (editToolForGesture, EditPenLayerTest). Also: a tool or mode switch mid-placement drops the placement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SymbolSideButtonTest {
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

    private fun AnnotationViewModel.down(at: Offset) = edit.penDown(0, at, pageSize, reach, FrameHit.Outside)

    private fun AnnotationViewModel.tap(at: Offset) {
        down(at)
        edit.penUp(at, moved = false, cancelled = false)
    }

    private fun AnnotationViewModel.tool(tool: EditTool) = setEditTools(editTools.value.copy(chosen = tool))

    /** A side-button swipe along y = 60 from x [fromX] to [toX], as the pen layer sends it. */
    private fun AnnotationViewModel.buttonSwipe(fromX: Float, toX: Float) {
        edit.beginErase()
        edit.eraseAt(0, Offset(fromX, 60f), reach)
        edit.eraseAt(0, Offset(toX, 60f), reach)
        edit.endErase(cancelled = false)
    }

    @Test fun withTheSymbolToolASelectionIsClearedAndTheSwipeErasesInOneStep() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        vm.tap(Offset(100f, 60f))
        vm.tap(Offset(150f, 150f))
        assertEquals(vm.symbols.value.last().id, vm.symbolSelection.value?.ids?.single())
        vm.edit.beginErase()
        assertNull(vm.symbolSelection.value, "the selection ends at pen down")
        vm.edit.eraseAt(0, Offset(40f, 60f), reach)
        vm.edit.eraseAt(0, Offset(110f, 60f), reach)
        vm.edit.endErase(cancelled = false)
        settle()
        assertEquals(listOf(Offset(150f, 150f)), vm.symbols.value.map { Offset(it.x, it.y) })
        assertEquals(1, symbolDao.rows.size)
        assertEquals(EditTool.Symbol, vm.editTools.value.chosen, "releasing returns to the tool")
        vm.undo()
        settle()
        assertEquals(3, vm.symbols.value.size, "one undo step brings both back")
        assertEquals(3, symbolDao.rows.size)
    }

    @Test fun undoOfAnErasedSymbolReselectsItWithTheSymbolTool() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        val id = vm.symbols.value.single().id
        vm.buttonSwipe(40f, 60f)
        assertTrue(vm.symbols.value.isEmpty())
        vm.undo()
        assertEquals(id, vm.symbolSelection.value?.ids?.single())
    }

    @Test fun theSideButtonErasesWithTheLassoTool() = runTest {
        val vm = editing()
        vm.tap(Offset(50f, 60f))
        vm.tool(EditTool.Lasso)
        vm.tap(Offset(50f, 60f))
        assertEquals(vm.symbols.value.single().id, vm.symbolSelection.value?.ids?.single())
        vm.buttonSwipe(40f, 60f)
        settle()
        assertTrue(vm.symbols.value.isEmpty())
        assertNull(vm.symbolSelection.value)
        assertTrue(symbolDao.rows.isEmpty())
    }

    @Test fun aButtonEraseDropsAPlacementInProgress() = runTest {
        val vm = editing()
        vm.down(Offset(50f, 60f))
        vm.edit.beginErase()
        assertNull(vm.symbolPlacing.value)
        vm.edit.endErase(cancelled = false)
        settle()
        assertTrue(vm.symbols.value.isEmpty())
        assertEquals(0, symbolDao.writes)
        assertFalse(vm.canUndo.value)
    }

    @Test fun aToolSwitchMidPlacementDropsItAndTheLiftPlacesNothing() = runTest {
        val vm = editing()
        vm.down(Offset(50f, 60f))
        vm.tool(EditTool.Lasso)
        assertNull(vm.symbolPlacing.value)
        vm.edit.dragTo(Offset(70f, 80f))
        vm.edit.penUp(Offset(70f, 80f), moved = true, cancelled = false)
        settle()
        assertTrue(vm.symbols.value.isEmpty())
        assertEquals(0, symbolDao.writes)
        assertFalse(vm.canUndo.value)
    }

    @Test fun aModeSwitchMidPlacementDropsIt() = runTest {
        val vm = editing()
        vm.down(Offset(50f, 60f))
        vm.setMode(Mode.Ink)
        assertNull(vm.symbolPlacing.value)
        vm.edit.penUp(Offset(50f, 60f), moved = true, cancelled = false) // the layer's abandon on leaving Edit mode
        settle()
        assertTrue(vm.symbols.value.isEmpty())
        assertEquals(0, symbolDao.writes)
        assertFalse(vm.canUndo.value)
    }

    @Test fun choosingTheSameToolMidPlacementKeepsIt() = runTest {
        val vm = editing()
        vm.down(Offset(50f, 60f))
        vm.tool(EditTool.Symbol)
        vm.edit.penUp(Offset(50f, 60f), moved = false, cancelled = false)
        assertEquals(1, vm.symbols.value.size)
    }
}
