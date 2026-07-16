package com.retrovault.input

import com.retrovault.emulator.RetroPad
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * `.pulsarskin` v1 (P27): a portable touch-overlay layout. A skin file is a ZIP containing
 * `skin.json` (this schema); an `assets/` folder is RESERVED for per-control art in a future
 * version. Positions are normalized to the view (cx/cy in 0..1), sizes are scale factors over
 * the default layout — so one skin round-trips across devices and screen sizes.
 *
 * Custom buttons are the PPSSPP CustomButton model: one on-screen button pressing any combination
 * of RetroPad buttons, in one of three modes — plain press (combo), toggle (tap latches), or
 * turbo (autofire while held).
 */
@Serializable
data class SkinControl(
    val cx: Float,
    val cy: Float,
    val scale: Float = 1.0f,
    val visible: Boolean = true,
)

@Serializable
data class SkinCustomButton(
    val label: String,
    val cx: Float,
    val cy: Float,
    val scale: Float = 1.0f,
    /** RetroPad button names, e.g. ["A","B"] — pressed together. */
    val buttons: List<String>,
    /** "press" | "toggle" | "turbo" */
    val mode: String = "press",
)

@Serializable
data class PulsarSkin(
    val format: String = "pulsarskin",
    val version: Int = 1,
    val name: String = "Custom skin",
    val author: String = "",
    val opacity: Float = 0.75f,
    /** Overrides for the built-in controls, keyed: dpad, stick, face, l, r, select, start. */
    val controls: Map<String, SkinControl> = emptyMap(),
    val customButtons: List<SkinCustomButton> = emptyList(),
) {
    companion object {
        val CONTROL_KEYS = setOf("dpad", "stick", "face", "l", "r", "select", "start")
        val MODES = setOf("press", "toggle", "turbo")
        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

        private val BUTTON_NAMES = mapOf(
            "B" to RetroPad.B, "Y" to RetroPad.Y, "SELECT" to RetroPad.SELECT,
            "START" to RetroPad.START, "UP" to RetroPad.UP, "DOWN" to RetroPad.DOWN,
            "LEFT" to RetroPad.LEFT, "RIGHT" to RetroPad.RIGHT, "A" to RetroPad.A,
            "X" to RetroPad.X, "L" to RetroPad.L, "R" to RetroPad.R,
            "L2" to RetroPad.L2, "R2" to RetroPad.R2, "L3" to RetroPad.L3, "R3" to RetroPad.R3,
        )

        fun maskFor(names: List<String>): Int =
            names.fold(0) { acc, n -> acc or (BUTTON_NAMES[n.uppercase()] ?: 0) }

        fun parse(text: String): PulsarSkin? = runCatching {
            val skin = json.decodeFromString<PulsarSkin>(text)
            if (skin.format != "pulsarskin" || skin.version != 1) return null
            if (!validate(skin)) return null
            skin
        }.getOrNull()

        fun validate(skin: PulsarSkin): Boolean {
            if (skin.controls.keys.any { it !in CONTROL_KEYS }) return false
            val posOk = skin.controls.values.all {
                it.cx in 0f..1f && it.cy in 0f..1f && it.scale in 0.25f..3f
            }
            val customOk = skin.customButtons.all {
                it.cx in 0f..1f && it.cy in 0f..1f && it.scale in 0.25f..3f &&
                    it.mode in MODES && it.buttons.isNotEmpty() && maskFor(it.buttons) != 0
            }
            return posOk && customOk
        }

        fun toJson(skin: PulsarSkin): String = json.encodeToString(skin)

        /** Read a .pulsarskin ZIP (skin.json entry) — or a bare skin.json file. */
        fun readFile(file: File): PulsarSkin? = runCatching {
            if (file.extension.equals("json", ignoreCase = true)) return parse(file.readText())
            ZipInputStream(file.inputStream().buffered()).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "skin.json") {
                        return parse(zip.readBytes().toString(Charsets.UTF_8))
                    }
                    entry = zip.nextEntry
                }
            }
            null
        }.getOrNull()

        /** Write [skin] as a .pulsarskin ZIP. Returns false on IO failure. */
        fun writeFile(skin: PulsarSkin, dest: File): Boolean = runCatching {
            dest.parentFile?.mkdirs()
            ZipOutputStream(dest.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("skin.json"))
                zip.write(toJson(skin).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            true
        }.getOrDefault(false)
    }
}
