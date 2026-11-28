package com.violinsheet.feature.annotation.edit.symbol

import com.violinsheet.core.data.symbol.Symbol
import com.violinsheet.feature.annotation.Command

/**
 * Edit mode's one undo step: the Symbols [before] become [after] (place: none → one; move, resize or recolour: the
 * same ids changed; delete or erase: some → none). The list stays sorted by id, so no places are kept. After apply
 * the Symbols of [after] are selected again, after revert those of [before] (SymbolEditing.reselect: when all on one
 * shown Page and the tool allows; design §5, 7a.5).
 */
internal class ReplaceSymbols(
    private val before: List<Symbol>,
    private val after: List<Symbol>,
    private val editing: SymbolEditing,
) : Command {
    override fun apply() {
        editing.swap(before, after)
        editing.reselect(after)
    }

    override fun revert() {
        editing.swap(after, before)
        editing.reselect(before)
    }
}
