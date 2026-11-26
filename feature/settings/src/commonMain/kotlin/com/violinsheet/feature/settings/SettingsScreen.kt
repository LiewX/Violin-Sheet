package com.violinsheet.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.violinsheet.core.data.settings.ChoiceSetting
import com.violinsheet.core.data.settings.ColourSetting
import com.violinsheet.core.data.settings.SettingsRegistry
import com.violinsheet.core.data.settings.SettingsStore
import com.violinsheet.core.data.settings.SliderSetting

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
    var open by remember { mutableStateOf(false) }
    // Follows the pen while dragging; saved when the drag ends, then dropped once the stored value arrives.
    var dragging by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(value) { dragging = null }
    val shown = dragging ?: value
    val customColour = shown?.takeIf { it !in setting.presets }
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
            // The custom swatch: the custom colour when one is set, else a rainbow ring. Opens the picker.
            Box(
                Modifier.size(40.dp)
                    .clip(CircleShape)
                    .background(customColour?.let { SolidColor(Color(it)) } ?: CustomColourBrush)
                    .border(if (customColour != null) 3.dp else 1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .selectable(selected = customColour != null, onClick = { open = !open })
                    .semantics { contentDescription = "Custom ${setting.title}" },
            )
        }
        if (open) {
            ColourPicker(
                argb = shown ?: setting.default,
                onColour = { dragging = it },
                onColourFinished = { dragging?.let { store.set(setting, it) } },
                modifier = Modifier.padding(top = 12.dp),
            )
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
        Text("${setting.title}: ${formatPoints(shown)} ${setting.unit}", style = MaterialTheme.typography.bodyLarge)
        StepSlider(
            value = shown,
            range = setting.range,
            step = setting.step,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { store.set(setting, it) }
                dragging = null
            },
            modifier = Modifier.widthIn(max = 480.dp),
        )
    }
}
