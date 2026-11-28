package com.violinsheet.feature.annotation.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import com.violinsheet.feature.annotation.resources.Bravura
import com.violinsheet.feature.annotation.resources.Res
import org.jetbrains.compose.resources.Font

/**
 * Bravura (Steinberg, SIL OFL 1.1, licence in composeResources/files/OFL.txt), the SMuFL engraving font Symbols are
 * drawn in, so they look printed (design §3).
 */
@Composable
fun rememberSymbolFont(): FontFamily {
    val font = Font(Res.font.Bravura)
    return remember(font) { FontFamily(font) }
}
