package com.violinsheet.feature.viewer

import com.violinsheet.core.data.settings.ChoiceSetting
import com.violinsheet.core.data.settings.ChoiceSetting.Choice

/** Settings declared by the viewer (SPECS: Settings). Add one here and it appears on the Settings page. */
object ViewerSettings {
    const val ADVANCE_TWO = "two"
    const val ADVANCE_ONE = "one"

    /** Where a Sheet's "advance by one" toggle starts until the Sheet sets its own. */
    val landscapeAdvance = ChoiceSetting(
        key = "reading.landscapeAdvance",
        group = "Reading",
        title = "Landscape turns advance",
        choices = listOf(Choice(ADVANCE_TWO, "Two Pages"), Choice(ADVANCE_ONE, "One Page")),
        default = ADVANCE_TWO,
    )

    val all = listOf(landscapeAdvance)
}
