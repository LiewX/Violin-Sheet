package com.violinsheet.core.ink

/** What the pen does, as chosen in the toolbar. */
sealed interface InkTool

/** Draws strokes of [tool] in [colour] (ARGB) and [size] (Page points). */
data class DrawTool(val tool: StrokeTool, val colour: Int, val size: Float) : InkTool

/** Erases Ink with a circle [size] Page points across. */
data class Eraser(val mode: EraseMode, val size: Float) : InkTool

/** Circles strokes to select them, then moves, resizes, rotates or deletes the selection (LassoInput). */
data object Lasso : InkTool

enum class EraseMode {
    /** Any stroke the eraser touches goes whole. */
    Stroke,

    /** Only the part under the eraser goes; the rest of the stroke stays, in pieces. */
    Area,
}

/**
 * The tool one pen gesture uses, decided at pen down. While the pen's erase action is held (the S Pen side button on
 * Android) the gesture erases with [eraser], whatever is chosen; the chosen tool is back for the next gesture.
 */
fun toolForGesture(chosen: InkTool, eraser: Eraser, eraseHeld: Boolean): InkTool = if (eraseHeld) eraser else chosen
