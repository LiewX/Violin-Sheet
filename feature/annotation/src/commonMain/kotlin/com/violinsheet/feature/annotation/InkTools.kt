package com.violinsheet.feature.annotation

import com.violinsheet.core.ink.DrawTool
import com.violinsheet.core.ink.Eraser
import com.violinsheet.core.ink.InkTool
import com.violinsheet.core.ink.Lasso

/** The Ink mode tools in the toolbar. */
enum class ToolChoice { Pen, Highlighter, Eraser, Lasso }

/**
 * Which tool the toolbar has chosen, and each tool's current colour, size and mode. [includePartlyEnclosed] is the
 * lasso's rule (InkSettings.includePartlyEnclosed).
 */
data class InkTools(
    val chosen: ToolChoice,
    val pen: DrawTool,
    val highlighter: DrawTool,
    val eraser: Eraser,
    val includePartlyEnclosed: Boolean = false,
) {
    val current: InkTool
        get() = when (chosen) {
            ToolChoice.Pen -> pen
            ToolChoice.Highlighter -> highlighter
            ToolChoice.Eraser -> eraser
            ToolChoice.Lasso -> Lasso
        }
}
