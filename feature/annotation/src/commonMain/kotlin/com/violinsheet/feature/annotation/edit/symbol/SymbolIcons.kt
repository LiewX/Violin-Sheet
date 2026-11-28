package com.violinsheet.feature.annotation.edit.symbol

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// The toolbar's Symbols, drawn in their printed look, never as text labels (design Decision 11).

/** The square a toolbar Symbol is fitted into. */
private val ICON_FIT = 24.dp

/** At most this staff space, so a narrow glyph (a fingering) isn't blown up to the full square. */
private val ICON_MAX_SPACE = 18.dp

/**
 * The Bravura glyph [codepoint], whose box is [box] (staff spaces, y down, from the glyph origin), fitted into the icon
 * square and centred on its ink, not on the font's tall line box. Drawn, not a Text, so no text node reaches the
 * accessibility tree: the name is the enclosing button's contentDescription.
 */
@Composable
internal fun SymbolGlyph(codepoint: Int, box: Rect, font: FontFamily, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val colour = LocalContentColor.current
    Canvas(modifier.size(ICON_FIT)) {
        val fit = ICON_FIT.toPx()
        val space = minOf(fit / box.width, fit / box.height, ICON_MAX_SPACE.toPx())
        // SMuFL: the em is four staff spaces.
        val style = TextStyle(fontFamily = font, fontSize = (space * 4).toSp(), color = colour)
        val layout = measurer.measure(codepoint.toChar().toString(), style, overflow = TextOverflow.Visible)
        drawText(layout, topLeft = Offset(center.x - box.center.x * space, center.y - layout.firstBaseline - box.center.y * space))
    }
}

/** [group]'s representative printed Symbol: its glyph, or the Slur arc or Hairpin wedge. */
@Composable
internal fun SymbolGroupIcon(group: SymbolGroup, font: FontFamily) {
    val codepoint = group.representative
    val box = group.representativeBox
    when {
        codepoint != null && box != null -> SymbolGlyph(codepoint, box, font)
        group == SymbolGroup.Hairpin -> Icon(HairpinIcon, contentDescription = null)
        else -> Icon(SlurIcon, contentDescription = null)
    }
}

/** A slur: a thin crescent, thick in the middle like an engraved one. For #16 to reuse. */
val SlurIcon = ImageVector.Builder("Slur", 24.dp, 24.dp, 24f, 24f)
    .addPath(addPathNodes("M2,16C6,7 18,7 22,16C18,10.5 6,10.5 2,16Z"), fill = SolidColor(Color.Black))
    .build()

/** A crescendo hairpin: two lines opening to the right. For #17 to reuse (mirrored for a diminuendo). */
val HairpinIcon = ImageVector.Builder("Hairpin", 24.dp, 24.dp, 24f, 24f)
    .addPath(
        addPathNodes("M21,7L3,12L21,17"),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    .build()
