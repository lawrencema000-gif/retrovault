package com.retrovault.download

import android.content.Context
import com.retrovault.core.model.GameSystem

/**
 * Whether the user has imported a BIOS for a system. PS1/PS2 require a user-supplied Sony BIOS;
 * PSP needs none. The app never bundles or downloads BIOS.
 */
object BiosStatus {
    fun isInstalled(context: Context, system: GameSystem): Boolean =
        RomStorage.biosDir(context, system).listFiles()?.any { it.isFile && it.length() > 0 } == true
}
