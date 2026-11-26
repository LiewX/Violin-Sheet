package com.violinsheet.feature.settings

import androidx.compose.foundation.layout.Column
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
                        // The type picks the control. Toggle, slider and colour controls come with their first Setting.
                        when (setting) {
                            is ChoiceSetting -> ChoiceRow(setting, store)
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
