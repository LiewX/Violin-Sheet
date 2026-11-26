package com.violinsheet.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.violinsheet.core.data.settings.ChoiceSetting
import com.violinsheet.core.data.settings.ColourSetting
import com.violinsheet.core.data.settings.SliderSetting
import com.violinsheet.core.data.settings.SettingsRegistry
import com.violinsheet.core.data.settings.SettingsStore

/** The Settings page, built from the declarations in [registry]: one section per feature, one control per Setting. */
@OptIn(ExperimentalMaterial3Api::class) // TopAppBar
@Composable
fun SettingsScreen(registry: SettingsRegistry, store: SettingsStore, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            registry.groups.forEach { (group, settings) ->
                item(key = "group:$group") {
                    Text(group, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 24.dp, 16.dp, 8.dp))
                }
                settings.forEach { setting ->
                    item(key = "setting:${setting.key}") {
                        // The type picks the control. A toggle control comes with the first toggle Setting.
                        when (setting) {
                            is ChoiceSetting -> ChoiceRow(setting, store)
                            is ColourSetting -> ColourRow(setting, store)
                            is SliderSetting -> SliderRow(setting, store)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class) // SegmentedButton
@Composable
private fun ChoiceRow(setting: ChoiceSetting, store: SettingsStore) {
    // Nothing selected until the stored value loads, rather than flashing the default.
    val value by remember(setting) { store.observe(setting) }.collectAsState(null)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(setting.title, style = MaterialTheme.typography.bodyLarge)
        // Wide enough that labels like "Two Pages" stay on one line.
        SingleChoiceSegmentedButtonRow(Modifier.padding(top = 8.dp).widthIn(min = 120.dp * setting.choices.size)) {
            setting.choices.forEachIndexed { i, choice ->
                SegmentedButton(
                    selected = value == choice.id,
                    onClick = { store.set(setting, choice.id) },
                    shape = SegmentedButtonDefaults.itemShape(i, setting.choices.size),
                ) { Text(choice.label) }
            }
        }
    }
}

@Composable
private fun ColourRow(setting: ColourSetting, store: SettingsStore) {
    val value by remember(setting) { store.observe(setting) }.collectAsState(null)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(setting.title, style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            setting.presets.forEach { colour ->
                val selected = value == colour
                Box(
                    Modifier.size(40.dp)
                        .clip(CircleShape)
                        .background(Color(colour))
                        .border(if (selected) 3.dp else 1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .selectable(selected = selected, onClick = { store.set(setting, colour) }),
                )
            }
        }
    }
}

@Composable
private fun SliderRow(setting: SliderSetting, store: SettingsStore) {
    val stored by remember(setting) { store.observe(setting) }.collectAsState(null)
    // Follows the finger while dragging; saved when the drag ends.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: stored ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("${setting.title}: ${formatStep(shown)} ${setting.unit}", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = shown,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { store.set(setting, it) }
                dragging = null
            },
            valueRange = setting.range,
            steps = ((setting.range.endInclusive - setting.range.start) / setting.step).roundToInt() - 1,
            modifier = Modifier.widthIn(max = 480.dp),
        )
    }
}

/** Up to two decimals, without trailing zeros: 0.75 -> "0.75", 1.5 -> "1.5", 2.0 -> "2". */
private fun formatStep(value: Float): String {
    val hundredths = (value * 100).roundToInt()
    val decimals = (hundredths % 100).toString().padStart(2, '0').trimEnd('0')
    return if (decimals.isEmpty()) "${hundredths / 100}" else "${hundredths / 100}.$decimals"
}
