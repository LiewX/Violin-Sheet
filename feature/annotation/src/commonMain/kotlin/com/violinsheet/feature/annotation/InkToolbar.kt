package com.violinsheet.feature.annotation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.violinsheet.core.ink.Pen

/**
 * The slim palette at the bottom edge: pen colour and size presets. No dialogs (CLAUDE.md: UI/UX). [saveFailed]
 * shows a warning that the last stroke wasn't saved.
 */
@Composable
fun InkToolbar(pen: Pen, onPen: (Pen) -> Unit, saveFailed: Boolean = false) {
    Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 3.dp, shadowElevation = 3.dp) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (saveFailed) Text("Last stroke not saved", color = MaterialTheme.colorScheme.error)
            PEN_COLOURS.forEach { (name, colour) ->
                Choice(selected = pen.colour == colour, label = "$name pen", onClick = { onPen(pen.copy(colour = colour)) }) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(Color(colour)))
                }
            }
            VerticalDivider(Modifier.padding(horizontal = 4.dp).size(width = 1.dp, height = 28.dp))
            PEN_SIZES.forEach { size ->
                Choice(selected = pen.size == size, label = "Pen size $size pt", onClick = { onPen(pen.copy(size = size)) }) {
                    // Dot grows with the size, so the presets read at a glance.
                    Box(Modifier.size((4 + size * 4).dp).clip(CircleShape).background(Color(pen.colour)))
                }
            }
        }
    }
}

@Composable
private fun Choice(selected: Boolean, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(40.dp)
            .clip(CircleShape)
            .border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
            .selectable(selected = selected, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}
