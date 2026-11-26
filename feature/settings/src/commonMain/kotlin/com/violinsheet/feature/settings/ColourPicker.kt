package com.violinsheet.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/** Hue in degrees (0..360), saturation and brightness (0..1). */
internal data class Hsv(val hue: Float, val saturation: Float, val value: Float)

/** [argb]'s colour as HSV; alpha is dropped. Greys get hue 0 and black saturation 0. */
internal fun argbToHsv(argb: Int): Hsv {
    val r = (argb shr 16 and 0xFF) / 255f
    val g = (argb shr 8 and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val top = max(r, max(g, b))
    val delta = top - min(r, min(g, b))
    val hue = when {
        delta == 0f -> 0f
        top == r -> 60f * (((g - b) / delta) % 6f)
        top == g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }
    return Hsv(if (hue < 0f) hue + 360f else hue, if (top == 0f) 0f else delta / top, top)
}

/** The ARGB colour for [hsv] with [alpha] (0..255) kept as given. */
internal fun hsvToArgb(hsv: Hsv, alpha: Int): Int =
    (alpha shl 24) or (Color.hsv(hsv.hue.coerceIn(0f, 360f), hsv.saturation.coerceIn(0f, 1f), hsv.value.coerceIn(0f, 1f)).toArgb() and 0xFFFFFF)

private val HUES = List(7) { Color.hsv(it * 60f, 1f, 1f) }

/**
 * An HSV colour picker: a saturation (across) and brightness (down) square, a hue strip beside it and a swatch of the
 * result. Changes only the RGB of [argb]: its alpha stays, so a translucent highlighter colour stays translucent.
 * [onColourFinished] runs when a drag or tap ends.
 */
@Composable
fun ColourPicker(argb: Int, onColour: (Int) -> Unit, modifier: Modifier = Modifier, onColourFinished: () -> Unit = {}) {
    // The HSV triple is held here: at zero saturation or brightness the RGB no longer says which hue was chosen.
    var hsv by remember { mutableStateOf(argbToHsv(argb)) }
    var lastEmitted by remember { mutableIntStateOf(argb) }
    if (argb != lastEmitted) {
        hsv = argbToHsv(argb)
        lastEmitted = argb
    }
    // The gesture handlers outlive a recomposition, so they read the latest of these.
    val alpha by rememberUpdatedState(argb ushr 24)
    val colourChanged by rememberUpdatedState(onColour)
    val finished by rememberUpdatedState(onColourFinished)
    fun change(new: Hsv) {
        hsv = new
        lastEmitted = hsvToArgb(new, alpha)
        colourChanged(lastEmitted)
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Canvas(
            Modifier.size(200.dp)
                .dragOrTap(onFinished = { finished() }) { p, size ->
                    change(hsv.copy(saturation = (p.x / size.width).coerceIn(0f, 1f), value = 1f - (p.y / size.height).coerceIn(0f, 1f)))
                },
        ) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hsv.hue, 1f, 1f))))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            drawMarker(Offset(hsv.saturation * size.width, (1f - hsv.value) * size.height))
        }
        Canvas(
            Modifier.width(32.dp).height(200.dp)
                .dragOrTap(onFinished = { finished() }) { p, size ->
                    change(hsv.copy(hue = (p.y / size.height).coerceIn(0f, 1f) * 360f))
                },
        ) {
            drawRect(Brush.verticalGradient(HUES))
            drawMarker(Offset(size.width / 2, hsv.hue / 360f * size.height))
        }
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(Color(hsvToArgb(hsv, alpha)))
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
    }
}

private fun DrawScope.drawMarker(at: Offset) {
    drawCircle(Color.White, radius = 9.dp.toPx(), center = at, style = Stroke(3.dp.toPx()))
    drawCircle(Color.Black, radius = 11.dp.toPx(), center = at, style = Stroke(1.dp.toPx()))
}

/** Reports the position on touch and while dragging (pen or finger), consuming the events so nothing underneath sees them. */
private fun Modifier.dragOrTap(onFinished: () -> Unit, onPoint: (Offset, IntSize) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            onPoint(down.position, size)
            drag(down.id) {
                it.consume()
                onPoint(it.position, size)
            }
            onFinished()
        }
    }
