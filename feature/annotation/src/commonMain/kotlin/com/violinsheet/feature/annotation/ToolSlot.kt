package com.violinsheet.feature.annotation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.dp

/** Every mode's slot is this wide where the screen allows, so the bar keeps one width across modes (design §6). */
internal val SLOT_WIDTH = 540.dp

/**
 * The toolbar's tool-specific part. It is as wide as the widest of all [keys]' [content], measured at runtime, so
 * switching tools never moves the buttons before it. Only the [chosen] key's content is placed, so the others take
 * no input.
 *
 * All contents stay composed, so [content] must not contain effects (LaunchedEffect, DisposableEffect,
 * SideEffect): put effects outside the slot. SubcomposeLayout throws under intrinsic measurement, so don't wrap the
 * toolbar in IntrinsicSize.
 */
@Composable
internal fun <K : Any> ToolSlot(keys: List<K>, chosen: K, content: @Composable (K) -> Unit) {
    SubcomposeLayout { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val measured = keys.associateWith { key ->
            subcompose(key) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content(key) }
            }.map { it.measure(loose) }
        }
        val width = measured.values.maxOf { list -> list.maxOfOrNull { it.width } ?: 0 }
        val height = measured.values.maxOf { list -> list.maxOfOrNull { it.height } ?: 0 }
        layout(width, height) {
            measured[chosen]?.forEach { it.place(0, (height - it.height) / 2) }
        }
    }
}

/**
 * The frame a mode's [ToolSlot] sits in: [SLOT_WIDTH] wide, or what the bar has left after its fixed items on a narrow
 * (portrait) screen, scrolling sideways with faded edges there. It takes the leftover width, so it must be a direct
 * child of the bar's Row.
 */
@Composable
internal fun RowScope.SlotFrame(content: @Composable () -> Unit) {
    val scroll = rememberScrollState()
    Box(Modifier.weight(1f, fill = false).width(SLOT_WIDTH).fadedEdges(scroll)) {
        Box(Modifier.horizontalScroll(scroll)) { content() }
    }
}
