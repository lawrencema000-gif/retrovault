package com.retrovault.settings

/**
 * Curated PSP settings registry, mapped to PPSSPP libretro core options. Values follow the
 * option strings PPSSPP v1.20.4 accepts.
 */
object PspSettings {

    val INTERNAL_RESOLUTION = SettingDef.Choice(
        key = "psp.video.internal_resolution",
        title = "Internal resolution",
        description = "PSP render resolution. Higher is sharper but heavier on the GPU.",
        category = Category.VIDEO,
        options = listOf(
            "480x272" to "1× (480×272, native)",
            "960x544" to "2× (960×544)",
            "1440x816" to "3× (1440×816)",
            "1920x1088" to "4× (1920×1088)",
            "2880x1632" to "6× (2880×1632)",
        ),
        defaultOption = "960x544",
        coreVariable = "ppsspp_internal_resolution",
    )

    val FRAMESKIP = SettingDef.Choice(
        key = "psp.video.frameskip",
        title = "Frameskip",
        description = "Skip rendering frames to keep game speed on weak devices.",
        category = Category.VIDEO,
        options = listOf("0" to "Off", "1" to "1", "2" to "2", "3" to "3"),
        defaultOption = "0",
        coreVariable = "ppsspp_frameskip",
    )

    val TEXTURE_FILTERING = SettingDef.Choice(
        key = "psp.video.texture_filtering",
        title = "Texture filtering",
        description = "How textures are sampled when scaled.",
        category = Category.VIDEO,
        options = listOf(
            "Auto" to "Auto",
            "Nearest" to "Nearest (sharp pixels)",
            "Linear" to "Linear (smooth)",
        ),
        defaultOption = "Auto",
        coreVariable = "ppsspp_texture_filtering",
    )

    val TEXTURE_SCALING = SettingDef.Choice(
        key = "psp.video.texture_scaling_level",
        title = "Texture upscaling",
        description = "Smooths and sharpens game textures. Can slow down weaker phones.",
        category = Category.VIDEO,
        options = listOf("1" to "Off", "2" to "2×", "3" to "3×", "5" to "5×"),
        defaultOption = "1",
        coreVariable = "ppsspp_texture_scaling_level",
    )

    // ---- display polish (P19): host present pass, not core options (coreVariable = null) ----

    val DISPLAY_ROTATION = SettingDef.Choice(
        key = "app.display.rotation",
        title = "Screen rotation",
        description = "Rotate the picture (some arcade-style games play vertically).",
        category = Category.VIDEO,
        options = listOf("0" to "None", "90" to "90° CW", "180" to "180°", "270" to "270° CW"),
        defaultOption = "0",
        coreVariable = null,
    )

    val DISPLAY_SCALE_MODE = SettingDef.Choice(
        key = "app.display.scale_mode",
        title = "Scaling",
        description = "Fit keeps the aspect (letterbox); Integer is pixel-perfect; Stretch fills.",
        category = Category.VIDEO,
        options = listOf("fit" to "Fit (letterbox)", "integer" to "Integer (pixel-perfect)", "stretch" to "Stretch"),
        defaultOption = "fit",
        coreVariable = null,
    )

    val DISPLAY_SHADER = SettingDef.Choice(
        key = "app.display.shader",
        title = "Display shader",
        description = "Post-processing over the final image (sharpen, CRT scanlines, or both).",
        category = Category.VIDEO,
        options = listOf(
            "none" to "None",
            "sharp_bilinear" to "Sharp bilinear",
            "fsr_sharpen" to "FSR sharpen",
            "scanline_crt" to "CRT scanlines",
            "crt_fsr" to "CRT + FSR",
        ),
        defaultOption = "none",
        coreVariable = null,
    )

    val DISPLAY_SCANLINE_INTENSITY = SettingDef.Choice(
        key = "app.display.scanline_intensity",
        title = "Scanline strength",
        description = "How dark the CRT scanlines are (when a CRT shader is active).",
        category = Category.VIDEO,
        options = listOf("off" to "Off", "low" to "Low", "med" to "Medium", "high" to "High"),
        defaultOption = "med",
        coreVariable = null,
    )

    val DISPLAY_SHARPEN_AMOUNT = SettingDef.Choice(
        key = "app.display.sharpen_amount",
        title = "Sharpen strength",
        description = "Edge sharpening amount (when an FSR/sharpen shader is active).",
        category = Category.VIDEO,
        options = listOf("off" to "Off", "low" to "Low", "med" to "Medium", "high" to "High"),
        defaultOption = "med",
        coreVariable = null,
    )

    val ANISOTROPIC = SettingDef.Choice(
        key = "psp.video.anisotropic",
        title = "Anisotropic filtering",
        description = "Sharpens textures viewed at an angle (floors, roads). Cheap on modern GPUs.",
        category = Category.VIDEO,
        options = listOf("0" to "Off", "1" to "2×", "2" to "4×", "3" to "8×", "4" to "16×"),
        defaultOption = "0",
        coreVariable = "ppsspp_anisotropic_filtering",
    )

    val CPU_CORE = SettingDef.Choice(
        key = "psp.emulation.cpu_core",
        title = "CPU core",
        description = "Leave on Dynarec unless a game crashes; Interpreter is very slow (debugging only).",
        category = Category.EMULATION,
        options = listOf(
            "JIT" to "Dynarec (JIT)",
            "IR JIT" to "IR interpreter (IR JIT)",
            "Interpreter" to "Interpreter (slow, debug)",
        ),
        defaultOption = "JIT",
        coreVariable = "ppsspp_cpu_core",
    )

    val FAST_MEMORY = SettingDef.Toggle(
        key = "psp.emulation.fast_memory",
        title = "Fast memory",
        description = "Faster but less safe memory access — turn off if a game crashes.",
        category = Category.EMULATION,
        defaultValue = true,
        coreVariable = "ppsspp_fast_memory",
    )

    val IGNORE_BAD_MEMORY = SettingDef.Toggle(
        key = "psp.emulation.ignore_bad_memory",
        title = "Ignore bad memory accesses",
        description = "Keep running past invalid memory access instead of crashing.",
        category = Category.EMULATION,
        defaultValue = true,
        coreVariable = "ppsspp_ignore_bad_memory_access",
    )

    val GRAPHICS_BACKEND = SettingDef.Choice(
        key = "app.video.backend",
        title = "Graphics backend",
        description = "OpenGL ES 3 is used today; Vulkan arrives in a later update.",
        category = Category.VIDEO,
        options = listOf("gles3" to "OpenGL ES 3", "vulkan" to "Vulkan (not available yet)"),
        defaultOption = "gles3",
        coreVariable = null,
    )

    val WIFI_ONLY_DOWNLOADS = SettingDef.Toggle(
        key = "app.system.wifi_only_downloads",
        title = "Wi-Fi only downloads",
        description = "Queue store downloads until an unmetered connection is available.",
        category = Category.SYSTEM,
        defaultValue = true,
        coreVariable = null,
    )

    val REWIND_ENABLED = SettingDef.Toggle(
        key = "app.emulation.rewind",
        title = "Rewind",
        description = "Keep periodic snapshots so you can step back (uses ~256 MB of RAM).",
        category = Category.EMULATION,
        defaultValue = true,
        coreVariable = null,
    )

    val HAPTICS = SettingDef.Toggle(
        key = "app.controls.haptics",
        title = "Touch haptics",
        description = "Vibration feedback for on-screen buttons.",
        category = Category.CONTROLS,
        defaultValue = true,
        coreVariable = null,
    )

    val ALL: List<SettingDef> = listOf(
        INTERNAL_RESOLUTION, FRAMESKIP, TEXTURE_FILTERING, TEXTURE_SCALING, ANISOTROPIC,
        DISPLAY_ROTATION, DISPLAY_SCALE_MODE, DISPLAY_SHADER,
        DISPLAY_SCANLINE_INTENSITY, DISPLAY_SHARPEN_AMOUNT,
        CPU_CORE, FAST_MEMORY, IGNORE_BAD_MEMORY,
        GRAPHICS_BACKEND, WIFI_ONLY_DOWNLOADS, REWIND_ENABLED, HAPTICS,
    )

    fun byKey(key: String): SettingDef? = ALL.firstOrNull { it.key == key }
}
