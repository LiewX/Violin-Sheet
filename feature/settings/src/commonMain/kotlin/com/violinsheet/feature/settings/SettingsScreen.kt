package com.violinsheet.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.violinsheet.core.data.settings.ChoiceSetting
import com.violinsheet.core.data.settings.PaletteSetting
import com.violinsheet.core.data.settings.SizePresetsSetting
import com.violinsheet.core.data.settings.Setting
import com.violinsheet.core.data.settings.SettingsRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.violinsheet.core.data.settings.SettingsStore
import com.violinsheet.core.data.settings.ToggleSetting
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role

/** The Settings page, built from the declarations in [registry]: one section per feature, one control per Setting. */
@OptIn(ExperimentalMaterial3Api::class) // TopAppBar
@Composable
fun SettingsScreen(registry: SettingsRegistry, store: SettingsStore, onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) { DeletedColourSnackbar(it) } },
    ) { padding ->
        // Room under the last item (Restore) so the S Pen's Air command button doesn't cover it.
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 80.dp)) {
            registry.groups.forEach { (group, settings) ->
                item(key = "group:$group") {
                    Text(group, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 24.dp, 16.dp, 8.dp))
                }
                settings.forEach { setting ->
                    item(key = "setting:${setting.key}") {
                        // The type picks the control.
                        when (setting) {
                            is ChoiceSetting -> ChoiceRow(setting, store)
                            is PaletteSetting -> PaletteEditor(setting, store, snackbar)
                            is SizePresetsSetting -> SizePresetEditor(setting, store)
                            is ToggleSetting -> ToggleRow(setting, store)
                        }
                    }
                }
                val restorable = settings.filter { it is PaletteSetting || it is SizePresetsSetting }
                if (restorable.isNotEmpty()) item(key = "restore:$group") { RestoreDefaultsButton(restorable, store) }
            }
        }
    }
}

/**
 * Puts the factory Palettes and Size presets of [settings] back. Colours the user added stay, after the factory ones
 * (PaletteEdit.restored).
 */
@Composable
private fun RestoreDefaultsButton(settings: List<Setting<*>>, store: SettingsStore) {
    val scope = rememberCoroutineScope()
    OutlinedButton(
        onClick = {
            scope.launch {
                settings.forEach { setting ->
                    when (setting) {
                        is PaletteSetting -> store.set(setting, PaletteEdit(store.observe(setting).first()).restored(setting.default).colours)
                        is SizePresetsSetting -> store.set(setting, setting.default)
                        else -> Unit
                    }
                }
            }
        },
        modifier = Modifier.padding(16.dp, 12.dp),
    ) { Text("Restore default palettes and presets") }
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
private fun ToggleRow(setting: ToggleSetting, store: SettingsStore) {
    // Off and disabled until the stored value loads, rather than flashing the default.
    val value by remember(setting) { store.observe(setting) }.collectAsState(null)
    SwitchRow(setting.title, value ?: false, enabled = value != null) { store.set(setting, it) }
}

/** A labelled switch; the whole row toggles it. Shared with the toolbar's panel. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChecked)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

