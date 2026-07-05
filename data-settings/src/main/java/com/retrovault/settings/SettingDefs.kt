package com.retrovault.settings

/** Which layer supplied a resolved value — surfaced as a badge in the settings UI. */
enum class Origin { DEFAULT, GAMEDB, DEVICE, USER_GLOBAL, USER_GAME }

enum class Category { VIDEO, AUDIO, EMULATION, CONTROLS, SYSTEM }

/**
 * A typed setting. Values are stored as strings across all layers (JSON-friendly, diffable);
 * [SettingDef] carries the type for UI + validation.
 */
sealed class SettingDef {
    abstract val key: String
    abstract val title: String
    abstract val description: String
    abstract val category: Category
    abstract val default: String

    /** libretro core option this setting drives, or null for app-level settings. */
    abstract val coreVariable: String?

    data class Toggle(
        override val key: String,
        override val title: String,
        override val description: String,
        override val category: Category,
        val defaultValue: Boolean,
        override val coreVariable: String? = null,
        /** core option strings for on/off (e.g. "enabled"/"disabled"). */
        val coreOn: String = "enabled",
        val coreOff: String = "disabled",
    ) : SettingDef() {
        override val default get() = defaultValue.toString()
    }

    data class Choice(
        override val key: String,
        override val title: String,
        override val description: String,
        override val category: Category,
        /** value → display label, in UI order. The stored value is the map key. */
        val options: List<Pair<String, String>>,
        val defaultOption: String,
        override val coreVariable: String? = null,
        /** stored value → core option string (identity when null). */
        val coreValueOf: ((String) -> String)? = null,
    ) : SettingDef() {
        override val default get() = defaultOption
    }
}

data class ResolvedSetting(val def: SettingDef, val value: String, val origin: Origin) {
    val asBoolean: Boolean get() = value.toBoolean()
}
