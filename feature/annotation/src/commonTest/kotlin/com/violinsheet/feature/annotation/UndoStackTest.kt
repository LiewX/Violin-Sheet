package com.violinsheet.feature.annotation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UndoStackTest {
    private val list = mutableListOf<String>()
    private val stack = UndoStack()

    /** Adds [items] to [list]; undo takes them all out. */
    private inner class Add(private vararg val items: String) : Command {
        override fun apply() {
            list += items
        }

        override fun revert() {
            list -= items.toSet()
        }
    }

    @Test fun undoAndRedoGoInReverseOrder() {
        stack.perform(Add("a"))
        stack.perform(Add("b"))
        stack.perform(Add("c"))

        stack.undo()
        stack.undo()
        assertEquals(listOf("a"), list)

        stack.redo()
        assertEquals(listOf("a", "b"), list)
        stack.redo()
        assertEquals(listOf("a", "b", "c"), list)
    }

    @Test fun newCommandClearsRedo() {
        stack.perform(Add("a"))
        stack.undo()
        stack.perform(Add("b"))

        assertFalse(stack.canRedo.value)
        stack.redo()
        assertEquals(listOf("b"), list)
    }

    @Test fun undoAndRedoOnEmptyStackDoNothing() {
        stack.undo()
        stack.redo()
        assertEquals(emptyList(), list)
        assertFalse(stack.canUndo.value)
        assertFalse(stack.canRedo.value)
    }

    @Test fun canUndoAndCanRedoTrackTheStack() {
        stack.perform(Add("a"))
        assertTrue(stack.canUndo.value)
        assertFalse(stack.canRedo.value)

        stack.undo()
        assertFalse(stack.canUndo.value)
        assertTrue(stack.canRedo.value)

        stack.redo()
        assertTrue(stack.canUndo.value)
        assertFalse(stack.canRedo.value)
    }

    @Test fun commandCoveringSeveralItemsReversesThemAll() {
        stack.perform(Add("a"))
        stack.perform(Add("b", "c", "d"))

        stack.undo()
        assertEquals(listOf("a"), list)
        stack.redo()
        assertEquals(listOf("a", "b", "c", "d"), list)
    }

    @Test fun recordedCommandIsUndoneButNotAppliedAgain() {
        stack.perform(Add("a"))
        stack.undo()
        list += "b" // already done, as an erase is while the pen moves
        stack.record(Add("b"))

        assertEquals(listOf("b"), list)
        assertTrue(stack.canUndo.value)
        assertFalse(stack.canRedo.value) // recording drops the redo, like a new edit
        stack.undo()
        assertEquals(emptyList(), list)
        stack.redo()
        assertEquals(listOf("b"), list)
    }
}
