package com.violinsheet.feature.annotation.edit

import com.violinsheet.feature.annotation.edit.symbol.SymbolGroup
import com.violinsheet.feature.annotation.edit.symbol.SymbolKind

/** What the pen does on the Page. A Sheet always opens in [Ink] (SPECS). */
enum class Mode { Ink, Edit, Text }

/**
 * Edit mode's tools. [Lasso] selects by a pen tap (one Symbol) or a loop (every Symbol wholly inside it). [Cover] is
 * shown but does nothing until #18.
 */
enum class EditTool { Symbol, Lasso, Eraser, Cover }

/**
 * Edit mode's toolbar choices: the [chosen] tool, and the Symbol tool's [group] and [kind]. They last only while the
 * Sheet is open (SPECS: Settings), so they are not Settings.
 */
data class EditTools(
    val chosen: EditTool = EditTool.Symbol,
    val group: SymbolGroup = SymbolGroup.Fingering,
    val kind: SymbolKind = SymbolKind.Fingering1,
)
