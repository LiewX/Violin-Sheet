package com.violinsheet.feature.settings

/**
 * A Palette's colours (ARGB, in order) as the Settings page edits them. Every edit keeps at least one colour and one
 * swatch per colour. A Sheet starts on the first colour. Strokes keep their own stored colour: nothing here touches them.
 */
data class PaletteEdit(val colours: List<Int>) {
    init {
        require(colours.isNotEmpty()) { "a palette needs a colour" }
    }

    /** The last colour has no Delete. */
    val canDelete: Boolean get() = colours.size > 1

    /** [colour] removed, unless it is the last one. */
    fun deleted(colour: Int): PaletteEdit = if (canDelete) PaletteEdit(colours - colour) else this

    /**
     * Undo of deleting [colour] from [index]: it goes back in its place among the colours there now (edits made since
     * are kept). Nothing changes if it is back already.
     */
    fun undeleted(colour: Int, index: Int): PaletteEdit =
        if (colour in colours) this else PaletteEdit(colours.toMutableList().apply { add(index.coerceIn(0, size), colour) })

    /** The colour at [from] moved to [to]. */
    fun moved(from: Int, to: Int): PaletteEdit = PaletteEdit(colours.toMutableList().apply { add(to.coerceIn(0, lastIndex), removeAt(from)) })

    /** [colour] appended, unless it is already there. */
    fun added(colour: Int): PaletteEdit = if (colour in colours) this else PaletteEdit(colours + colour)

    /** [old] replaced in its place by [new]; if [new] is already another colour there, the two merge. */
    fun replaced(old: Int, new: Int): PaletteEdit = when {
        new == old -> this
        new in colours -> PaletteEdit(colours - old)
        else -> PaletteEdit(colours.map { if (it == old) new else it })
    }

    /** The [factory] colours back in their order, then the colours the user added. */
    fun restored(factory: List<Int>): PaletteEdit = PaletteEdit((factory + colours).distinct())
}
