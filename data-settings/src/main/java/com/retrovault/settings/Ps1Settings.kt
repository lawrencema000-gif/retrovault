package com.retrovault.settings

/**
 * Curated PS1 settings registry, mapped to SwanStation libretro core options. Every key/value
 * string below was extracted verbatim from the pinned SwanStation source
 * (`libretro_core_options.h` @ f9010221) and adversarially re-verified — a typo'd key silently
 * no-ops. Booleans are the canonical "true"/"false" strings the core parses.
 *
 * The controller TYPE is deliberately NOT here: SwanStation selects it via
 * `retro_set_controller_port_device` (no core option exists) — see [PS1_CONTROLLER] which is an
 * app-level setting consumed by the session, not a core variable.
 */
object Ps1Settings {

    val RENDERER = SettingDef.Choice(
        key = "ps1.video.renderer",
        title = "PS1 renderer",
        description = "Hardware is sharp and fast; Software is most accurate for glitchy games.",
        category = Category.VIDEO,
        options = listOf(
            "Auto" to "Hardware (auto)",
            "OpenGL" to "Hardware (OpenGL)",
            "Vulkan" to "Hardware (Vulkan)",
            "Software" to "Software (accurate)",
        ),
        defaultOption = "Auto",
        coreVariable = "swanstation_GPU_Renderer",
    )

    val RESOLUTION_SCALE = SettingDef.Choice(
        key = "ps1.video.resolution_scale",
        title = "PS1 internal resolution",
        description = "Render scale over native 240p. Higher is sharper but heavier on the GPU.",
        category = Category.VIDEO,
        options = listOf(
            "1" to "1× (native)", "2" to "2×", "3" to "3×",
            "4" to "4×", "5" to "5×", "8" to "8×",
        ),
        defaultOption = "2",
        coreVariable = "swanstation_GPU_ResolutionScale",
    )

    val PGXP = SettingDef.Toggle(
        key = "ps1.video.pgxp",
        title = "PS1 geometry correction (PGXP)",
        description = "Removes the wobbly polygons and warped textures of original hardware.",
        category = Category.VIDEO,
        defaultValue = false,
        coreVariable = "swanstation_GPU_PGXPEnable",
    )

    val TRUE_COLOR = SettingDef.Toggle(
        key = "ps1.video.true_color",
        title = "PS1 true color",
        description = "Smoother gradients (24-bit color, no dithering). A few games look off.",
        category = Category.VIDEO,
        defaultValue = false,
        coreVariable = "swanstation_GPU_TrueColor",
    )

    val WIDESCREEN = SettingDef.Toggle(
        key = "ps1.video.widescreen",
        title = "PS1 widescreen hack",
        description = "Widens the view in 3D games. Pair with the 16:9 aspect ratio below.",
        category = Category.VIDEO,
        defaultValue = false,
        coreVariable = "swanstation_GPU_WidescreenHack",
    )

    val ASPECT_RATIO = SettingDef.Choice(
        key = "ps1.video.aspect_ratio",
        title = "PS1 aspect ratio",
        description = "How the picture is proportioned. Auto matches the original output.",
        category = Category.VIDEO,
        options = listOf(
            "4:3" to "4:3 (original)",
            "16:9" to "16:9 (with widescreen hack)",
            "Auto" to "Corrected (NTSC)",
            "Native" to "Corrected (region native)",
        ),
        defaultOption = "4:3",
        coreVariable = "swanstation_Display_AspectRatio",
    )

    /**
     * App-level: which pad the emulated console sees on port 1. Applied by the session via
     * `retro_set_controller_port_device` (digital = 1, DualShock = 261) — NOT a core variable.
     * DualShock also sets `swanstation_Controller1_ForceAnalog` so the sticks work immediately.
     */
    val PS1_CONTROLLER = SettingDef.Choice(
        key = "ps1.controls.controller_type",
        title = "PS1 controller",
        description = "DualShock adds the two analog sticks; Digital is the original pad.",
        category = Category.CONTROLS,
        options = listOf("digital" to "Digital pad", "dualshock" to "DualShock (analog)"),
        defaultOption = "dualshock",
        coreVariable = null,
    )

    /** Everything the resolver should surface + push for PS1. */
    val ALL: List<SettingDef> = listOf(
        RENDERER, RESOLUTION_SCALE, PGXP, TRUE_COLOR, WIDESCREEN, ASPECT_RATIO, PS1_CONTROLLER,
    )

    /** `retro_set_controller_port_device` ids (SwanStation subclass constants, verified). */
    const val DEVICE_DIGITAL = 1        // RETRO_DEVICE_JOYPAD
    const val DEVICE_DUALSHOCK = 261    // RETRO_DEVICE_SUBCLASS(ANALOG, 0)
}
