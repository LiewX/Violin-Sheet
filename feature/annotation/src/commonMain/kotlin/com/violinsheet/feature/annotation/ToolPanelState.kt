package com.violinsheet.feature.annotation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

/** What the panel above the toolbar holds (SPECS: Ink mode, The panel). */
sealed interface Panel {
    /** The lasso's selection rule: opened by tapping the Lasso tool again. */
    data object Lasso : Panel

    /** The colour picker for a new Palette colour. */
    data object AddColour : Panel

    /** The colour picker for the Palette colour [argb], the selected one. */
    data class EditColour(val argb: Int) : Panel

    /** The slider for Size preset [index] (0 small, 1 medium, 2 large), the selected one. */
    data class EditPreset(val index: Int) : Panel

    /** Edit mode's six Symbol groups: opened by the Symbol slot's group switch. */
    data object SymbolGroup : Panel
}

/**
 * What Save in the colour panel [panel] does with [colour]: the panel opened from the selected swatch edits that
 * Palette colour (replacing it app-wide, never adding one); the one from + Add colour adds it.
 */
fun InkTools.withColourSaved(panel: Panel, colour: Int): InkTools = when (panel) {
    is Panel.EditColour -> withColourReplaced(panel.argb, colour)
    Panel.AddColour -> withColourAdded(colour)
    Panel.Lasso, is Panel.EditPreset, Panel.SymbolGroup -> this
}

/**
 * Which panel is open and the colour being picked in it, unsaved until Save. A touch anywhere outside the panel
 * closes it ([closesToolPanel]) and still does its job; tapping what opened a panel again closes it.
 */
@Stable
class ToolPanelState {
    var open by mutableStateOf<Panel?>(null)
        private set

    /** The colour the picker shows: saved only by Save. */
    var draft by mutableIntStateOf(0)

    /** The panel's bounds in root coordinates while it is shown. */
    internal var bounds: Rect? = null
    internal var origin = Offset.Zero

    /** The panel this gesture's first touch closed: the tap that follows, on what opened it, must not reopen it. */
    private var dismissed: Panel? = null

    /** Opens [panel] with the picker on [colour], or closes it if it is open. */
    fun toggle(panel: Panel, colour: Int = draft) {
        val justClosed = dismissed == panel
        dismissed = null
        if (justClosed) return
        if (open == panel) {
            close()
        } else {
            draft = colour
            open = panel
        }
    }

    /** The open panel now shows [panel] instead (a re-sorted preset moved), keeping what it holds. */
    fun follow(panel: Panel) {
        if (open != null) open = panel
    }

    fun close() {
        open = null
    }

    internal fun pressedAt(rootPoint: Offset) {
        dismissed = null
        val shown = open ?: return
        if (bounds?.contains(rootPoint) == true) return
        dismissed = shown
        close()
    }
}

/**
 * Watches every touch on the screen it is set on, finger or pen, without taking it: a gesture's first touch outside
 * the open panel closes it, so a stroke, a toolbar button or a page turn still happens. A second pointer (a palm
 * landing while the pen works the panel) leaves it open.
 */
fun Modifier.closesToolPanel(panel: ToolPanelState): Modifier =
    onGloballyPositioned { panel.origin = it.positionInRoot() }.pointerInput(panel) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Press) continue
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size == 1) panel.pressedAt(panel.origin + pressed.single().position)
            }
        }
    }
