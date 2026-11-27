package com.violinsheet.core.data.settings

import kotlin.math.abs
import kotlin.math.roundToInt

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

/**
 * A tool's app-wide Palette: an ordered list of ARGB colours, one swatch per colour, never empty. [default] is the
 * factory list, which a missing or unreadable stored value falls back to. A new Sheet starts on the first colour.
 */
data class PaletteSetting(
    override val key: String,
    override val group: String,
    override val title: String,
    override val default: List<Int>,
) : Setting<List<Int>> {
    init {
        require(default.isNotEmpty()) { "$key: a palette needs a colour" }
    }

    override fun encode(value: List<Int>): String {
        require(value.isNotEmpty()) { "$key: a palette needs a colour" }
        return value.distinct().joinToString(",")
    }

    override fun decode(stored: String?): List<Int> {
        val colours = stored?.takeIf { it.isNotEmpty() }?.split(',')?.map { it.toIntOrNull() ?: return default } ?: return default
        return colours.distinct()
    }
}

/**
 * A tool's three Size presets in [range], kept sorted small to large, edited in steps of [step] and labelled in [unit].
 * A missing or unreadable stored value falls back to [default]. A new Sheet starts on the middle one ([MEDIUM]);
 * [preview] is how the Settings page draws a size.
 */
data class SizePresetsSetting(
    override val key: String,
    override val group: String,
    override val title: String,
    val range: ClosedFloatingPointRange<Float>,
    val step: Float,
    val unit: String,
    override val default: List<Float>,
    val preview: SizePreview = SizePreview.Line,
) : Setting<List<Float>> {
    init {
        require(default.size == COUNT && default.all { it in range } && default == default.sorted()) {
            "$key: default $default is not three sorted sizes in $range"
        }
    }

    /** How the Settings page previews a size: a pen's line, a highlighter's translucent band, an eraser's circle. */
    enum class SizePreview { Line, Band, Circle }

    /** The presets after one was let go: [sizes] sorted, and [order] the index each came from. */
    data class Settled(val sizes: List<Float>, val order: List<Int>) {
        /** Where the preset that was at [old] went. */
        fun indexOf(old: Int) = order.indexOf(old)
    }

    /**
     * [sizes] after preset [index] was dragged from [from] and let go at `sizes[index]`. A drag may land on another
     * preset; on release it is nudged in steps back towards [from] (up if it didn't move, down at the range end)
     * until it is distinct, so the three stay apart. Then the three re-sort, equal sizes keeping their order.
     */
    fun settle(sizes: List<Float>, index: Int, from: Float): Settled {
        val others = sizes.filterIndexed { i, _ -> i != index }
        fun taken(size: Float) = others.any { abs(it - size) < step / 2 }
        var size = sizes[index]
        if (taken(size)) {
            val towardsFrom = if (from < size) -1 else 1
            size = sequenceOf(towardsFrom, -towardsFrom).flatMap { dir -> generateSequence(size + dir * step) { it + dir * step }.takeWhile { it in rangeWithSlack() } }
                .firstOrNull { !taken(it) }?.let(::snap) ?: size
        }
        val edited = sizes.toMutableList().apply { set(index, size) }
        val order = edited.indices.sortedBy { edited[it] }
        return Settled(order.map { edited[it] }, order)
    }

    private fun rangeWithSlack() = (range.start - step / 2)..(range.endInclusive + step / 2)

    private fun snap(size: Float) = (((size - range.start) / step).roundToInt() * step + range.start).coerceIn(range)

    override fun encode(value: List<Float>): String {
        require(value.size == COUNT) { "$key: needs $COUNT sizes, got $value" }
        return tidy(value).joinToString(",")
    }

    override fun decode(stored: String?): List<Float> {
        val sizes = stored?.split(',')?.map { it.toFloatOrNull() ?: return default } ?: return default
        return if (sizes.size == COUNT) tidy(sizes) else default
    }

    private fun tidy(sizes: List<Float>) = sizes.map { it.coerceIn(range) }.sorted()

    companion object {
        const val COUNT = 3

        /** The middle preset: the one a Sheet starts on. */
        const val MEDIUM = 1

        /** The presets' names by index, as labels read them. */
        val NAMES = listOf("small", "medium", "large")
    }
}

