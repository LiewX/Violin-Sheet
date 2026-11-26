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
)

/** One input sample of a stroke. [pressure] is 0–1, or -1 when the tool reports none. */
data class InkPoint(val x: Float, val y: Float, val pressure: Float, val elapsedMillis: Long)

/** The pen as currently chosen in the toolbar. */
data class Pen(val colour: Int, val size: Float)
