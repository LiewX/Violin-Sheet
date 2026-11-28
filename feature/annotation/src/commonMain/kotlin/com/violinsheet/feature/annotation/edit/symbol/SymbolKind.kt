package com.violinsheet.feature.annotation.edit.symbol

import androidx.compose.ui.geometry.Rect

/**
 * A Page's staff space in Page points, the unit Symbol glyphs are sized in (SMuFL: em = 4 staff spaces). The standard
 * size until #15 gives each Page its own Symbol scale; a Symbol's `scale` is relative to it. A fingering digit is
 * about one staff space tall, so about 5 pt at scale 1.
 */
const val STANDARD_STAFF_SPACE = 5f

/** How a kind is placed and drawn: one glyph at a point. Ties, slurs and hairpins (#16, #17) add two endpoints. */
enum class SymbolShape { Glyph }

/**
 * A Symbol that can be placed: its [stored] name (Symbol.kind), its [group], its SMuFL [codepoint] in Bravura and its
 * glyph [box] in staff spaces, y down, from the glyph origin.
 */
enum class SymbolKind(val stored: String, val group: SymbolGroup, val codepoint: Int, val box: Rect, val shape: SymbolShape = SymbolShape.Glyph) {
    // Boxes from Bravura 1.482, redist/Bravura.json -> glyphBBoxes (bBoxSW, bBoxNE), y flipped: left = SW.x,
    // top = -NE.y, right = NE.x, bottom = -SW.y.
    Fingering0("fingering.0", SymbolGroup.Fingering, 0xED10, Rect(0.08f, -1.004f, 0.94f, 0.004f)),
    Fingering1("fingering.1", SymbolGroup.Fingering, 0xED11, Rect(0.08f, -1.016f, 0.548f, 0f)),
    Fingering2("fingering.2", SymbolGroup.Fingering, 0xED12, Rect(0.08f, -1.012f, 0.888f, 0.012f)),
    Fingering3("fingering.3", SymbolGroup.Fingering, 0xED13, Rect(0.08f, -1.008f, 0.82f, 0f)),
    Fingering4("fingering.4", SymbolGroup.Fingering, 0xED14, Rect(0.08f, -1.012f, 0.864f, -0.004f)),
    ;

    /** This kind's glyph box on the Page: [box] × staff space × [scale], centred on ([x], [y]), Page points. */
    fun boxOnPage(x: Float, y: Float, scale: Float, staffSpace: Float = STANDARD_STAFF_SPACE): Rect {
        val halfWidth = box.width * staffSpace * scale / 2
        val halfHeight = box.height * staffSpace * scale / 2
        return Rect(x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight)
    }

    companion object {
        private val byStored = entries.associateBy { it.stored }

        /** The kind stored as [stored]; null for one this version doesn't know (kept, never shown). */
        fun fromStored(stored: String): SymbolKind? = byStored[stored]
    }
}

/**
 * The Symbol groups, in the spec's order. [representative] is the SMuFL codepoint the toolbar shows for the group
 * (12-21-ui-decisions 2B), [representativeBox] its glyph box as in [SymbolKind.box]; both null for Slur and Hairpin,
 * which the toolbar draws as path icons.
 */
enum class SymbolGroup(val representative: Int?, val representativeBox: Rect?) {
    // Boxes from Bravura.json as for SymbolKind; Fingering's is Fingering1's, copied (referencing it would make the
    // two enums' initialisers depend on each other).
    Fingering(0xED11, Rect(0.08f, -1.016f, 0.548f, 0f)), // "1"
    Articulation(0xE4A0, Rect(0f, -0.98f, 1.356f, -0.004f)), // articAccentAbove
    Bowing(0xE610, Rect(0f, -1.272f, 1.248f, 0f)), // stringsDownBow
    Dynamic(0xE52D, Rect(-0.08f, -1.724f, 3.272f, 0.66f)), // dynamicMF
    Slur(null, null),
    Hairpin(null, null),
    ;

    /** The kinds in this group, in toolbar order; empty for a group not built yet. */
    val kinds: List<SymbolKind> get() = SymbolKind.entries.filter { it.group == this }
}
