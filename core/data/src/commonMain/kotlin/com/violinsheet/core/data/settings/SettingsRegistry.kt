package com.violinsheet.core.data.settings

/** All declared Settings, grouped for the page. Each feature module contributes its own list. */
class SettingsRegistry(declarations: List<List<Setting<*>>>) {
    val settings: List<Setting<*>> = declarations.flatten()

    init {
        val duplicates = settings.groupBy { it.key }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Settings declared twice: $duplicates" }
    }

    /** Settings by group, in declaration order. */
    val groups: Map<String, List<Setting<*>>> = settings.groupBy { it.group }
}
