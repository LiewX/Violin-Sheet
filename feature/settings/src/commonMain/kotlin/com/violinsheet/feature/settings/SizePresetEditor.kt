package com.violinsheet.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.violinsheet.core.data.settings.SizePresetsSetting
import com.violinsheet.core.data.settings.SizePresetsSetting.SizePreview
import com.violinsheet.core.data.settings.SettingsStore

/**
 * A tool's three Size presets: one box each, all one height, with a preview at its size and its value. Tapping a box
 * opens its slider; on release the three re-sort (SizePresetsSetting.settle). A Sheet starts on the middle one.
 */
@Composable
internal fun SizePresetEditor(setting: SizePresetsSetting, store: SettingsStore) {
    val stored by remember(setting) { store.observe(setting) }.collectAsState(null)
    var open by remember { mutableStateOf<Int?>(null) }
    // While the slider moves: the three as shown (not re-sorted yet) and where the moved one started.
    var live by remember { mutableStateOf<List<Float>?>(null) }
    var from by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(stored) { live = null }
    val sizes = live ?: stored ?: return

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.widthIn(max = 720.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(setting.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(150.dp))
            sizes.forEachIndexed { i, size ->
                PresetBox(
                    label = "${setting.title} ${formatPoints(size)} ${setting.unit}",
                    text = "${formatPoints(size)} ${setting.unit}",
                    preview = setting.preview,
                    previewSize = previewSize(setting.preview, size),
                    isOpen = i == open,
                    modifier = Modifier.weight(1f),
                ) { open = if (open == i) null else i }
            }
        }
        val i = open ?: return@Column
        Row(Modifier.widthIn(max = 720.dp).padding(start = 162.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Editing: ${SizePresetsSetting.NAMES[i]} preset", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            StepSlider(
                value = sizes[i],
                range = setting.range,
                step = setting.step,
                onValueChange = { value ->
                    if (from == null) from = sizes[i]
                    live = sizes.toMutableList().apply { set(i, value) }
                },
                onValueChangeFinished = {
                    val start = from ?: return@StepSlider
                    val settled = setting.settle(sizes, i, start)
                    live = settled.sizes
                    from = null
                    open = settled.indexOf(i)
                    store.set(setting, settled.sizes)
                },
                modifier = Modifier.weight(1f).semantics { contentDescription = "${setting.title} slider" },
            )
        }
    }
}

/** How big a preview of [size] points is drawn, so the three read apart and stay inside the 34 dp box. */
private fun previewSize(preview: SizePreview, size: Float): Dp = when (preview) {
    SizePreview.Line -> (size * 1.3f).coerceIn(1f, 24f)
    SizePreview.Band -> size.coerceIn(2f, 26f)
    SizePreview.Circle -> (size * 0.65f).coerceIn(4f, 26f)
}.dp

/** Highlighter yellow, translucent as a highlighter stroke is. */
private val BAND = Color(0x99FFEB3B.toInt())

@Composable
private fun PresetBox(
    label: String,
    text: String,
    preview: SizePreview,
    previewSize: Dp,
    isOpen: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val colours = MaterialTheme.colorScheme
    Row(
        modifier.height(34.dp).clip(shape)
            .background(if (isOpen) colours.secondaryContainer else colours.surface)
            .border(1.dp, colours.outline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = label }
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            when (preview) {
                SizePreview.Line -> Box(Modifier.fillMaxWidth().height(previewSize).clip(CircleShape).background(colours.onSurface))
                SizePreview.Band -> Box(Modifier.fillMaxWidth().height(previewSize).background(BAND))
                SizePreview.Circle -> Box(Modifier.size(previewSize).border(1.dp, colours.onSurfaceVariant, CircleShape))
            }
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
