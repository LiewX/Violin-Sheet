package com.violinsheet.feature.annotation.edit

import com.violinsheet.core.data.symbol.Symbol
import com.violinsheet.feature.annotation.Command

/**
 * Edit mode's one undo step: the Symbols [before] become [after] (place: none → one; move or resize: one → the same
 * id changed; delete or erase: some → none). The list stays sorted by id, so no places are kept. After apply the one
 * Symbol of [after] is selected again, after revert the one of [before] (reselect, design §5); never several.
 */
internal class ReplaceSymbols(
    private val before: List<Symbol>,
    private val after: List<Symbol>,
    private val editing: SymbolEditing,
) : Command {
    override fun apply() {
        editing.swap(before, after)
        editing.reselect(after.singleOrNull())
    }

    override fun revert() {
        editing.swap(after, before)
        editing.reselect(before.singleOrNull())
    }
}
