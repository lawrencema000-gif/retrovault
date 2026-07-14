package com.retrovault.settings

import android.content.Context
import com.retrovault.emulator.LibretroBridge

/**
 * The 4-layer resolution engine. Precedence (lowest → highest):
 *   DEFAULT (SettingDef) → GAMEDB (per-serial compat flags, provider lands in P12)
 *   → DEVICE (device-class corrections) → USER_GLOBAL → USER_GAME.
 * Every resolved value carries its [Origin] so the UI can badge where it came from.
 */
class SettingsResolver(
    private val context: Context,
    /**
     * GameDB layer provider. The default looks the key up in the baked snapshot when it IS
     * a serial; callers that know the real serial for a non-serial gameKey (store games)
     * pass a closure over it — see EmulatorActivity.
     */
    private val gameDbProvider: (String?) -> Map<String, String> = { key ->
        GameDb.settingsFor(context, key)
    },
) {
    private val store = SettingsStore(context)

    fun resolve(def: SettingDef, gameKey: String? = null): ResolvedSetting {
        if (gameKey != null) {
            store.read(gameKey)[def.key]?.let { return ResolvedSetting(def, it, Origin.USER_GAME) }
        }
        store.read(null)[def.key]?.let { return ResolvedSetting(def, it, Origin.USER_GLOBAL) }
        DeviceClass.layerValues()[def.key]?.let { return ResolvedSetting(def, it, Origin.DEVICE) }
        gameDbProvider(gameKey)[def.key]?.let { return ResolvedSetting(def, it, Origin.GAMEDB) }
        return ResolvedSetting(def, def.default, Origin.DEFAULT)
    }

    fun resolveAll(gameKey: String? = null): List<ResolvedSetting> =
        (PspSettings.ALL + Ps1Settings.ALL).map { resolve(it, gameKey) }

    fun setUserValue(def: SettingDef, value: String, gameKey: String? = null) {
        store.set(gameKey, def.key, value)
    }

    /** Drop the user's override at this scope; the value falls back through the layers. */
    fun clearUserValue(def: SettingDef, gameKey: String? = null) {
        store.clear(gameKey, def.key)
    }

    /**
     * Push every core-variable-backed setting into the running (or about-to-run) session.
     * Values apply live via the GET_VARIABLE_UPDATE handshake.
     */
    fun applyToCore(gameKey: String?) {
        if (!LibretroBridge.available) return
        for (resolved in resolveAll(gameKey)) {
            val def = resolved.def
            val coreVar = def.coreVariable ?: continue
            val coreValue = when (def) {
                is SettingDef.Toggle ->
                    if (resolved.asBoolean) def.coreOn else def.coreOff
                is SettingDef.Choice ->
                    def.coreValueOf?.invoke(resolved.value) ?: resolved.value
            }
            LibretroBridge.nativeSetCoreVariable(coreVar, coreValue)
        }
    }

    /**
     * Push the app-level display settings (rotation / scale mode / post-shader stack) into the
     * host present pass. These are not core options — they drive [LibretroBridge.nativeSetDisplayConfig]
     * directly and take effect on the next presented frame.
     */
    fun applyDisplay(gameKey: String?) {
        if (!LibretroBridge.available) return
        fun value(def: SettingDef) = resolve(def, gameKey).value

        val rotation = value(PspSettings.DISPLAY_ROTATION).toIntOrNull() ?: 0
        val scaleMode = when (value(PspSettings.DISPLAY_SCALE_MODE)) {
            "stretch" -> 1
            "integer" -> 2
            else -> 0 // fit
        }
        // Shader choice -> ordered post-shader id stack (see PostShader ids in the native host).
        val (pass1, pass2) = when (value(PspSettings.DISPLAY_SHADER)) {
            "sharp_bilinear" -> 3 to 0
            "fsr_sharpen" -> 2 to 0
            "scanline_crt" -> 1 to 0
            "crt_fsr" -> 2 to 1   // sharpen at native res, then CRT scanlines at output res
            else -> 0 to 0        // none
        }
        val scanline = when (value(PspSettings.DISPLAY_SCANLINE_INTENSITY)) {
            "off" -> 0f
            "low" -> 0.2f
            "high" -> 0.55f
            else -> 0.35f // med
        }
        val sharpen = when (value(PspSettings.DISPLAY_SHARPEN_AMOUNT)) {
            "off" -> 0f
            "low" -> 0.35f
            "high" -> 0.75f
            else -> 0.5f // med
        }
        val mask = if (scanline > 0f) 0.1f else 0f
        LibretroBridge.nativeSetDisplayConfig(rotation, scaleMode, pass1, pass2, scanline, mask, sharpen)
    }
}
