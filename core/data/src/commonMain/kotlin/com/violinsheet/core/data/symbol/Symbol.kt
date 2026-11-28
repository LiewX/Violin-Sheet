package com.violinsheet.core.data.symbol

/**
 * A placed Symbol, as stored. Positions are in Page points.
 *
 * @property id From the app's shared stroke ids, so unique app-wide; order by id = placing order.
 * @property kind The stored kind name (e.g. `fingering.3`). The feature resolves it; an unknown kind is kept as is.
 * @property x Centre of the Symbol's glyph box.
 * @property y Centre of the Symbol's glyph box.
 * @property scale Factor on the Page's Symbol scale (1 = standard size).
 * @property colour ARGB.
 * @property endX Second endpoint of a tie, slur or hairpin; null until #16/#17.
 * @property endY Second endpoint of a tie, slur or hairpin; null until #16/#17.
 * @property bend Arch height (sign = above/below) or hairpin opening; null until #16/#17.
 */
data class Symbol(
    val id: Long,
    val pageIndex: Int,
    val kind: String,
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val colour: Int,
    val endX: Float? = null,
    val endY: Float? = null,
    val bend: Float? = null,
)
