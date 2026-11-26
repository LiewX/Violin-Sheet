package com.violinsheet.feature.annotation

import com.violinsheet.core.ink.DrawTool
import com.violinsheet.core.ink.Eraser
import com.violinsheet.core.ink.InkTool

/** The Ink mode tools in the toolbar. */
enum class ToolChoice { Pen, Highlighter, Eraser }

/** Which tool the toolbar has chosen, and each tool's current colour, size and mode. */
data class InkTools(val chosen: ToolChoice, val pen: DrawTool, val highlighter: DrawTool, val eraser: Eraser) {
    val current: InkTool
        get() = when (chosen) {
            ToolChoice.Pen -> pen
            ToolChoice.Highlighter -> highlighter
            ToolChoice.Eraser -> eraser
        }
}
