package com.violinsheet.feature.annotation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.dp

/**
 * The toolbar's tool-specific part. It is as wide as the widest of all tools' [content], measured at runtime, so
 * switching tools never moves the buttons before it. Only the [chosen] tool's content is placed, so the others take
 * no input.
 *
 * All four contents stay composed, so [content] must not contain effects (LaunchedEffect, DisposableEffect,
 * SideEffect): put effects outside the slot. SubcomposeLayout throws under intrinsic measurement, so don't wrap the
 * toolbar in IntrinsicSize.
 */
@Composable
internal fun ToolSlot(chosen: ToolChoice, content: @Composable (ToolChoice) -> Unit) {
    SubcomposeLayout { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val measured = ToolChoice.entries.associateWith { choice ->
            subcompose(choice) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content(choice) }
            }.map { it.measure(loose) }
        }
        val width = measured.values.maxOf { list -> list.maxOfOrNull { it.width } ?: 0 }
        val height = measured.values.maxOf { list -> list.maxOfOrNull { it.height } ?: 0 }
        layout(width, height) {
            measured.getValue(chosen).forEach { it.place(0, (height - it.height) / 2) }
        }
    }
}
