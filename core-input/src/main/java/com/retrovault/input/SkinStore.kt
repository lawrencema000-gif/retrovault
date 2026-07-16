package com.retrovault.input

import android.content.Context
import java.io.File

/**
 * Installed `.pulsarskin` files + the active selection (P27). Skins live in
 * `filesDir/skins/<name>.pulsarskin`; "" (empty) means the built-in default layout.
 */
class SkinStore(context: Context) {

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "skins").apply { mkdirs() }
    private val prefs = appContext.getSharedPreferences("pulsar-skins", Context.MODE_PRIVATE)

    var activeSkinName: String
        get() = prefs.getString(KEY_ACTIVE, "") ?: ""
        set(value) { prefs.edit().putString(KEY_ACTIVE, value).apply() }

    fun installed(): List<String> =
        dir.listFiles()?.filter { it.extension == EXT }?.map { it.nameWithoutExtension }?.sorted()
            ?: emptyList()

    /** The skin to apply in-game, or null for the built-in default layout. */
    fun activeSkin(): PulsarSkin? {
        val name = activeSkinName
        if (name.isEmpty()) return null
        return PulsarSkin.readFile(File(dir, "$name.$EXT"))
    }

    /** Install a parsed skin under its declared name; returns the stored name or null. */
    fun install(skin: PulsarSkin): String? {
        val safe = skin.name.replace(Regex("[^A-Za-z0-9 ._-]"), "_").trim().ifEmpty { "skin" }
        return if (PulsarSkin.writeFile(skin, File(dir, "$safe.$EXT"))) safe else null
    }

    fun delete(name: String) {
        File(dir, "$name.$EXT").delete()
        if (activeSkinName == name) activeSkinName = ""
    }

    companion object {
        private const val KEY_ACTIVE = "active"
        const val EXT = "pulsarskin"
    }
}
