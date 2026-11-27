package com.violinsheet.feature.annotation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// The toolbar's icons, built here because material-icons-core lacks them and material-icons-extended is large.

// The Material "undo" arrow.
internal val UndoArrow = ImageVector.Builder("Undo", 24.dp, 24.dp, 24f, 24f).addPath(
    addPathNodes(
        "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 " +
            "3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z",
    ),
    fill = SolidColor(Color.Black),
).build()

internal fun toolIcon(choice: ToolChoice) = when (choice) {
    ToolChoice.Pen -> PenIcon
    ToolChoice.Highlighter -> HighlighterIcon
    ToolChoice.Eraser -> EraserIcon
    ToolChoice.Lasso -> LassoIcon
}

private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(addPathNodes(path), fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd)
    .build()

// Material "edit".
private val PenIcon = icon(
    "Pen",
    "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34" +
        "c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z",
)

// A chisel-tip marker.
private val HighlighterIcon = icon(
    "Highlighter",
    "M18.5,1.15C17.97,1.15 17.46,1.34 17.07,1.73L11.26,7.55L16.91,13.2L22.73,7.39C23.5,6.61 23.5,5.35 22.73,4.56" +
        "L19.89,1.73C19.5,1.34 19,1.15 18.5,1.15ZM10.3,8.5L4.34,14.46C3.56,15.24 3.56,16.5 4.36,17.31L0.67,21H6.33" +
        "L7.19,20.14C7.97,20.9 9.22,20.89 10,20.12L15.95,14.16Z",
)

// A block eraser, its rubber end hollow.
private val EraserIcon = icon(
    "Eraser",
    "M16.24,3.56L21.19,8.5C21.97,9.29 21.97,10.55 21.19,11.34L12,20.53C10.44,22.09 7.91,22.09 6.34,20.53L2.81,17" +
        "C2.03,16.22 2.03,14.95 2.81,14.17L13.41,3.56C14.2,2.78 15.46,2.78 16.24,3.56ZM4.22,15.58L7.76,19.11" +
        "C8.54,19.9 9.8,19.9 10.59,19.11L14.12,15.58L9.17,10.63L4.22,15.58Z",
)

// A lasso loop with its tail.
private val LassoIcon = icon(
    "Lasso",
    "M3,9a9,6 0 1,0 18,0a9,6 0 1,0 -18,0zM5,9a7,4 0 1,0 14,0a7,4 0 1,0 -14,0z" +
        "M6.3,14.1L8.2,14.6L7.2,21L5.3,20.6Z",
)

// Material "delete".
internal val DeleteIcon = icon(
    "Delete",
    "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z",
)
