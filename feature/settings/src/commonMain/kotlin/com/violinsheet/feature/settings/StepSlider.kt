package com.violinsheet.feature.settings

import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt

/**
 * A slider over [range] in whole [step]s. Values go to [onValueChange] snapped to the step, so 1.4999999 arrives as
 * 1.5 and equals a preset of 1.5 exactly.
 */
@Composable
fun StepSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: () -> Unit = {},
) {
    val count = ((range.endInclusive - range.start) / step).roundToInt()
    Slider(
        value = value,
        onValueChange = { onValueChange((((it - range.start) / step).roundToInt() * step + range.start).coerceIn(range)) },
        onValueChangeFinished = onValueChangeFinished,
        valueRange = range,
        steps = count - 1,
        modifier = modifier,
    )
}

/** Up to two decimals, without trailing zeros: 0.75 -> "0.75", 1.5 -> "1.5", 2.0 -> "2". */
fun formatPoints(value: Float): String {
    val hundredths = (value * 100).roundToInt()
    val decimals = (hundredths % 100).toString().padStart(2, '0').trimEnd('0')
    return if (decimals.isEmpty()) "${hundredths / 100}" else "${hundredths / 100}.$decimals"
}

