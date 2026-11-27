package com.violinsheet.core.data.settings

/**
 * An app-wide default that a new Sheet starts from, declared by the feature it belongs to (GLOSSARY: Setting).
 * The Settings page is built from these declarations; the type picks the control.
 */
sealed interface Setting<T> {
    /** Stable storage key, "<group>.<name>". Never change it once shipped: stored values are found by it. */
    val key: String

    /** Heading the Setting is listed under on the Settings page, named after its feature. */
    val group: String
    val title: String
    val default: T

    fun encode(value: T): String

    /** The stored value, or [default] if it is missing or no longer valid. */
    fun decode(stored: String?): T
}

/** One of a fixed set of [choices], shown as a segmented row. */
data class ChoiceSetting(
    override val key: String,
    override val group: String,
    override val title: String,
    val choices: List<Choice>,
    override val default: String,
) : Setting<String> {
    /** [id] is what is stored; [label] is what the user sees. */
    data class Choice(val id: String, val label: String)

    init {
        require(choices.any { it.id == default }) { "$key: default \"$default\" is not one of its choices" }
    }

    override fun encode(value: String): String {
        require(choices.any { it.id == value }) { "$key: \"$value\" is not one of its choices" }
        return value
    }

    override fun decode(stored: String?) = stored?.takeIf { id -> choices.any { it.id == id } } ?: default
}

/** On or off, shown as a switch. */
data class ToggleSetting(
    override val key: String,
    override val group: String,
    override val title: String,
    override val default: Boolean,
) : Setting<Boolean> {
    override fun encode(value: Boolean) = value.toString()

    override fun decode(stored: String?) = stored?.toBooleanStrictOrNull() ?: default
}

/** A colour (ARGB), shown as swatches of [presets]. */
data class ColourSetting(
    override val key: String,
    override val group: String,
    override val title: String,
    val presets: List<Int>,
    override val default: Int,
) : Setting<Int> {
    override fun encode(value: Int) = value.toString()

    override fun decode(stored: String?) = stored?.toIntOrNull() ?: default
}

/** A number in [range], moved in steps of [step], shown as a slider labelled in [unit]. */
data class SliderSetting(
    override val key: String,
    override val group: String,
    override val title: String,
    val range: ClosedFloatingPointRange<Float>,
    val step: Float,
    val unit: String,
    override val default: Float,
) : Setting<Float> {
    init {
        require(default in range) { "$key: default $default is outside $range" }
    }

    override fun encode(value: Float) = value.coerceIn(range).toString()

    override fun decode(stored: String?) = stored?.toFloatOrNull()?.coerceIn(range) ?: default
}
