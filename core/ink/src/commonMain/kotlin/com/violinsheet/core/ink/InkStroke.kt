package com.violinsheet.core.ink

/**
 * A finished Ink stroke: the shared stroke format (ADR 0002), not Android Ink API types, so the iPad can read it.
 * Points are in Page coordinates (PDF points from the Page's top-left), and [size] is in points on the Page, so a
 * stroke keeps its place and thickness relative to the notes at any zoom and layout.
 */
data class InkStroke(
    /** Given when the stroke is drawn (StrokeIds), increasing in drawing order; undo and saving refer to it. */
    val id: Long,
    val pageIndex: Int,
    /** ARGB. */
    val colour: Int,
    val size: Float,
    val points: List<InkPoint>,
    val tool: StrokeTool,
)

/** One input sample of a stroke. [pressure] is 0–1, or -1 when the tool reports none. */
data class InkPoint(val x: Float, val y: Float, val pressure: Float, val elapsedMillis: Long)

/** What drew a stroke, which decides how it looks. [stored] is its name in the database: never change it. */
enum class StrokeTool(val stored: String) {
    Pen("pen"),

    /** Multiplied onto the Page, so the printed notes under it stay black and sharp. */
    Highlighter("highlighter"),
    ;

    companion object {
        fun fromStored(stored: String): StrokeTool =
            entries.firstOrNull { it.stored == stored } ?: throw IllegalArgumentException("Unknown stroke tool \"$stored\"")
    }
}
