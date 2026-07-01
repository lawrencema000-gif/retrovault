package com.retrovault.emulator

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.retrovault.core.model.GameSystem

/**
 * Gates demanding systems to capable hardware. PSP and PS1 run on essentially anything modern;
 * PS2 (ARMSX2) is Vulkan-class and needs a flagship, so it's gated to avoid 1-star reviews from
 * unsupported devices.
 */
object DeviceCapabilities {

    fun totalRamBytes(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem
    }

    /** ~6 GB RAM + Android 10+ as a coarse floor for playable PS2. */
    fun supportsPs2(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            totalRamBytes(context) >= 6L * 1024 * 1024 * 1024

    fun supports(context: Context, system: GameSystem): Boolean =
        if (system == GameSystem.PS2) supportsPs2(context) else true
}
