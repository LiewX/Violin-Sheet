package com.violinsheet.feature.annotation.edit.symbol

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.violinsheet.core.data.symbol.Symbol
import com.violinsheet.core.ink.SelectionFrame
import com.violinsheet.core.ink.StrokeTransform
import com.violinsheet.core.ink.rectInsideLasso

/**
 * The selected Symbols [ids], all on Page [pageIndex]: their [frame] (the union of their glyph boxes, unturned) and,
 * during a move or resize, the [preview] transform not yet baked in. Ids come from the app's shared id generator, so
 * Covers (#18) can join the same set (renamed EditSelection then).
 */
data class SymbolSelection(val pageIndex: Int, val ids: Set<Long>, val frame: SelectionFrame, val preview: StrokeTransform?) {
    /** One Symbol: only then can it be resized, and only then are corner handles drawn. */
    val single: Boolean get() = ids.size == 1
}

/** Every Symbol held is of a known kind (loaded filters the rest out). */
internal fun boxOf(symbol: Symbol): Rect =
    checkNotNull(SymbolKind.fromStored(symbol.kind)) { "unknown kind ${symbol.kind}" }.boxOnPage(symbol.x, symbol.y, symbol.scale)

/** Inclusive of the edges, unlike Rect.contains. */
internal fun Rect.holds(p: Offset) = p.x in left..right && p.y in top..bottom

/** The union of the glyph boxes of [symbols], which must not be empty. */
internal fun boxAround(symbols: List<Symbol>): Rect = symbols.map(::boxOf).reduce { a, b ->
    Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
}

/** The unturned frame around [symbols] (not empty). */
internal fun frameAround(symbols: List<Symbol>): SelectionFrame {
    val box = boxAround(symbols)
    return SelectionFrame(box.center, box.size / 2f, 0f)
}

/** [symbols] selected, all on the first one's Page; [symbols] must not be empty. */
internal fun selectionOf(symbols: List<Symbol>): SymbolSelection =
    SymbolSelection(symbols.first().pageIndex, symbols.mapTo(LinkedHashSet()) { it.id }, frameAround(symbols), null)

internal fun selectionOf(symbol: Symbol): SymbolSelection = selectionOf(listOf(symbol))

/** The Symbols of [symbols] on Page [pageIndex] whose glyph box is wholly inside [loop] (Page points). */
internal fun symbolsInLoop(symbols: List<Symbol>, pageIndex: Int, loop: List<Offset>): List<Symbol> =
    symbols.filter { it.pageIndex == pageIndex && rectInsideLasso(boxOf(it), loop) }
