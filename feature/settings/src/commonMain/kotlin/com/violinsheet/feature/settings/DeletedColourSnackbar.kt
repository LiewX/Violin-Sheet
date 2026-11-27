package com.violinsheet.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The Undo bar's content after deleting [colour] from a Palette: the colour's swatch and [message], no hex code. */
internal data class ColourDeleted(val colour: Int, override val message: String) : SnackbarVisuals {
    override val actionLabel get() = "Undo"
    override val withDismissAction get() = false
    override val duration get() = SnackbarDuration.Long
}

/** A snackbar showing a [ColourDeleted]'s swatch before its text; any other message as plain text. */
@Composable
internal fun DeletedColourSnackbar(data: SnackbarData) {
    val visuals = data.visuals
    Snackbar(
        modifier = Modifier.padding(12.dp),
        action = visuals.actionLabel?.let { label ->
            {
                TextButton(onClick = data::performAction, colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor)) { Text(label) }
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (visuals is ColourDeleted) {
                Box(Modifier.size(24.dp).background(Color(visuals.colour), CircleShape).border(1.dp, Color.White, CircleShape))
            }
            Text(visuals.message)
        }
    }
}
