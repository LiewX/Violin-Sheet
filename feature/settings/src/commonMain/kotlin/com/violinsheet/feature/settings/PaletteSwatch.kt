package com.violinsheet.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex

/** A swatch's place in the row: the 36 dp swatch with room around it. */
internal val SLOT = 46.dp

/**
 * The hold-and-drag of one swatch in a Palette's row: which one is lifted (one at a time, so a palm landing on another
 * swatch does nothing), how far it has moved, and each slot's centre, so a drop takes the nearest slot.
 */
@Stable
internal class SwatchDrag {
    var dragged by mutableStateOf<Int?>(null)
    var offset by mutableStateOf(Offset.Zero)
    val centres = mutableStateMapOf<Int, Offset>()

    /** The slot nearest where swatch [from] was dropped, among [count]. */
    fun target(from: Int, count: Int): Int {
        val at = (centres[from] ?: Offset.Zero) + offset
        return (0 until count).minBy { ((centres[it] ?: Offset.Zero) - at).getDistanceSquared() }
    }
}

/**
 * Swatch [index] of [colours] in [title]'s row. A tap calls [onTap]; hold and drag moves it ([onMove] from, to). The
 * tap is cancelled once a drag moves it, so a short press opens the panel and a hold-and-drag only reorders.
 */
@Composable
internal fun PaletteSwatch(
    title: String,
    colours: List<Int>,
    index: Int,
    drag: SwatchDrag,
    onTap: () -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val colour = colours[index]
    val lifted = drag.dragged == index
    val move by rememberUpdatedState(onMove)
    Box(
        Modifier.size(SLOT)
            .onGloballyPositioned { drag.centres[index] = it.positionInParent() + Offset(it.size.toSize().width / 2, it.size.toSize().height / 2) }
            .zIndex(if (lifted) 1f else 0f)
            .graphicsLayer {
                translationX = if (lifted) drag.offset.x else 0f
                translationY = if (lifted) drag.offset.y else 0f
                scaleX = if (lifted) 1.12f else 1f
                scaleY = scaleX
            }
            .pointerInput(colours, index) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { if (drag.dragged == null) { drag.dragged = index; drag.offset = Offset.Zero } },
                    onDrag = { change, amount -> if (drag.dragged == index) { change.consume(); drag.offset += amount } },
                    onDragEnd = {
                        if (drag.dragged == index) {
                            val to = drag.target(index, colours.size)
                            drag.dragged = null
                            if (to != index) move(index, to)
                        }
                    },
                    onDragCancel = { if (drag.dragged == index) drag.dragged = null },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(SLOT)
                .padding(5.dp)
                .then(if (lifted) Modifier.shadow(6.dp, CircleShape) else Modifier)
                .clip(CircleShape)
                .background(Color(colour))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable(role = Role.Button, onClick = onTap)
                .clearAndSetSemantics { contentDescription = "$title ${hexName(colour)}" },
        )
    }
}

