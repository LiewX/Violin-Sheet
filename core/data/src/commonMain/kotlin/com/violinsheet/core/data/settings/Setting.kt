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
