package com.violinsheet.feature.annotation.edit

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import com.violinsheet.core.data.symbol.Symbol
import com.violinsheet.core.ink.PagePlacement

/** The em each glyph is laid out at once (screen px); drawing scales it, so a zoom never lays it out again. */
private const val REFERENCE_EM = 100f

/** SMuFL: the em is four staff spaces. */
private const val SPACES_PER_EM = 4f

/**
 * The [symbols] on the Pages at [placements], in their printed look (Bravura), each glyph box centred on the Symbol's
 * point, in its colour. The [selection]'s Symbol is drawn where its drag preview puts it. Symbols of a kind this
 * version doesn't know are never held (SymbolEditing.loaded), and are skipped here too.
 */
@Composable
internal fun SymbolLayer(placements: List<PagePlacement>, symbols: List<Symbol>, selection: SymbolSelection?, modifier: Modifier = Modifier) {
    val font = rememberSymbolFont()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // One layout per kind, made the first time it is drawn; a new font (loaded) or density starts afresh.
    val layouts = remember(font, measurer, density) { HashMap<SymbolKind, TextLayoutResult>() }
    val style = remember(font, density) { TextStyle(fontFamily = font, fontSize = with(density) { REFERENCE_EM.toSp() }) }
    Canvas(modifier) {
        for (symbol in symbols) {
            val kind = SymbolKind.fromStored(symbol.kind) ?: continue
            val page = placements.firstOrNull { it.pageIndex == symbol.pageIndex } ?: continue
            val layout = layouts.getOrPut(kind) {
                measurer.measure(kind.codepoint.toChar().toString(), style, overflow = TextOverflow.Visible)
            }
            val preview = selection?.takeIf { it.id == symbol.id }?.preview
            val centre = Offset(symbol.x, symbol.y).let { preview?.apply(it) ?: it }
            val scale = symbol.scale * (preview?.scale ?: 1f)
            // Screen px per layout px.
            val k = page.scale * STANDARD_STAFF_SPACE * scale * SPACES_PER_EM / REFERENCE_EM
            val spacePx = REFERENCE_EM / SPACES_PER_EM
            // The box centre in the layout: from the glyph origin (left end of the baseline), y down.
            val boxCentre = Offset(kind.box.center.x * spacePx, layout.firstBaseline + kind.box.center.y * spacePx)
            val screen = page.toScreen(centre)
            translate(screen.x - boxCentre.x * k, screen.y - boxCentre.y * k) {
                scale(k, k, pivot = Offset.Zero) {
                    drawText(layout, color = Color(symbol.colour))
                }
            }
        }
    }
}
