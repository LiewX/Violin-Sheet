package com.violinsheet.feature.annotation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One undoable edit. It may cover several Annotations at once (a lasso action moves many strokes). */
interface Command {
    /** Makes the edit, the first time and again on redo. */
    fun apply()

    /** Reverses what [apply] did. */
    fun revert()
}

/**
 * A Sheet's undo history (CLAUDE.md: Architecture). Lives only while the Sheet is open, so it has no depth cap.
 * Not thread-safe: use it from the main thread.
 */
class UndoStack {
    private val done = ArrayDeque<Command>()
    private val undone = ArrayDeque<Command>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    /** Applies [command] and records it. A new edit drops what could be redone. */
    fun perform(command: Command) {
        command.apply()
        record(command)
    }

    /** Records [command], already applied as it happened (an erase gesture), without applying it again. */
    fun record(command: Command) {
        done.addLast(command)
        undone.clear()
        changed()
    }

    fun undo() {
        val command = done.removeLastOrNull() ?: return
        command.revert()
        undone.addLast(command)
        changed()
    }

    fun redo() {
        val command = undone.removeLastOrNull() ?: return
        command.apply()
        done.addLast(command)
        changed()
    }

    private fun changed() {
        _canUndo.value = done.isNotEmpty()
        _canRedo.value = undone.isNotEmpty()
    }
}
