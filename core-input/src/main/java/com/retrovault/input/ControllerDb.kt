package com.retrovault.input

import android.content.Context
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.retrovault.emulator.RetroPad

/**
 * SDL `gamecontrollerdb.txt` support (community DB, zlib license; bundled in assets).
 *
 * Rows: `guid,name,a:b0,dpup:h0.1,leftx:a0,…,platform:Android`. Matching uses the
 * vendor/product ids embedded in the SDL GUID (bytes 4-5 / 8-9, little-endian hex) rather
 * than reproducing SDL's full GUID algorithm — vid/pid is what actually identifies a model.
 *
 * SDL element indices are translated to Android sources:
 * - `bN` — SDL's Android backend maps keycodes to the standard SDL_GameControllerButton
 *   order (a=0 b=1 x=2 y=3 back=4 guide=5 start=6 leftstick=7 rightstick=8 leftshoulder=9
 *   rightshoulder=10 dpup=11 dpdown=12 dpleft=13 dpright=14) — inverted here back to keycodes.
 * - `aN` — the device's joystick motion ranges sorted by axis id, hats excluded.
 * - `hN.M` — hat 0 = AXIS_HAT_X/Y with direction from the mask (1=up 2=right 4=down 8=left).
 */
object ControllerDb {

    private const val ASSET = "gamecontrollerdb.txt"

    // SDL_GameControllerButton index (Android backend) → Android keycode
    private val SDL_BUTTON_TO_KEYCODE = intArrayOf(
        KeyEvent.KEYCODE_BUTTON_A,      // 0 a
        KeyEvent.KEYCODE_BUTTON_B,      // 1 b
        KeyEvent.KEYCODE_BUTTON_X,      // 2 x
        KeyEvent.KEYCODE_BUTTON_Y,      // 3 y
        KeyEvent.KEYCODE_BUTTON_SELECT, // 4 back
        KeyEvent.KEYCODE_BUTTON_MODE,   // 5 guide
        KeyEvent.KEYCODE_BUTTON_START,  // 6 start
        KeyEvent.KEYCODE_BUTTON_THUMBL, // 7 leftstick
        KeyEvent.KEYCODE_BUTTON_THUMBR, // 8 rightstick
        KeyEvent.KEYCODE_BUTTON_L1,     // 9 leftshoulder
        KeyEvent.KEYCODE_BUTTON_R1,     // 10 rightshoulder
        KeyEvent.KEYCODE_DPAD_UP,       // 11
        KeyEvent.KEYCODE_DPAD_DOWN,     // 12
        KeyEvent.KEYCODE_DPAD_LEFT,     // 13
        KeyEvent.KEYCODE_DPAD_RIGHT,    // 14
    )

    // SDL control name → target
    private val TARGETS: Map<String, InputTarget> = mapOf(
        "a" to InputTarget.Button(RetroPad.B),          // SDL a = bottom = Cross
        "b" to InputTarget.Button(RetroPad.A),          // right = Circle
        "x" to InputTarget.Button(RetroPad.Y),          // left = Square
        "y" to InputTarget.Button(RetroPad.X),          // top = Triangle
        "back" to InputTarget.Button(RetroPad.SELECT),
        "start" to InputTarget.Button(RetroPad.START),
        "leftshoulder" to InputTarget.Button(RetroPad.L),
        "rightshoulder" to InputTarget.Button(RetroPad.R),
        "lefttrigger" to InputTarget.Button(RetroPad.L2),
        "righttrigger" to InputTarget.Button(RetroPad.R2),
        "dpup" to InputTarget.Button(RetroPad.UP),
        "dpdown" to InputTarget.Button(RetroPad.DOWN),
        "dpleft" to InputTarget.Button(RetroPad.LEFT),
        "dpright" to InputTarget.Button(RetroPad.RIGHT),
        // PSP has one stick; right stick intentionally unmapped by default.
        "leftx" to InputTarget.Analog(0, 0, +1),
        "lefty" to InputTarget.Analog(0, 1, +1),
    )

    data class DbEntry(val guid: String, val name: String, val fields: Map<String, String>) {
        val vendorId: Int get() = leHex(guid, 8)
        val productId: Int get() = leHex(guid, 16)

        private fun leHex(g: String, pos: Int): Int {
            if (g.length < pos + 4) return 0
            val lo = g.substring(pos, pos + 2).toIntOrNull(16) ?: 0
            val hi = g.substring(pos + 2, pos + 4).toIntOrNull(16) ?: 0
            return (hi shl 8) or lo
        }
    }

    @Volatile
    private var entries: List<DbEntry>? = null

    fun load(context: Context): List<DbEntry> {
        entries?.let { return it }
        val parsed = runCatching {
            context.assets.open(ASSET).bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") && it.contains("platform:Android") }
                    .mapNotNull { parseLine(it) }
                    .toList()
            }
        }.getOrDefault(emptyList())
        entries = parsed
        return parsed
    }

    fun parseLine(line: String): DbEntry? {
        val parts = line.split(',')
        if (parts.size < 3) return null
        val guid = parts[0].trim().lowercase()
        if (guid.length != 32) return null
        val name = parts[1].trim()
        val fields = HashMap<String, String>()
        for (i in 2 until parts.size) {
            val kv = parts[i].split(':', limit = 2)
            if (kv.size == 2) fields[kv[0].trim()] = kv[1].trim()
        }
        return DbEntry(guid, name, fields)
    }

    /** Best DB entry for a device: exact vid+pid match, else null (Android default applies). */
    fun match(context: Context, device: InputDevice): DbEntry? {
        if (device.vendorId == 0 && device.productId == 0) return null
        return load(context).firstOrNull {
            it.vendorId == device.vendorId && it.productId == device.productId
        }
    }

    /**
     * Translate a DB entry into a [MappingProfile] for [device]. Unknown/untranslatable
     * elements are skipped — the Android default fills any gaps at resolve time.
     */
    fun toProfile(entry: DbEntry, device: InputDevice?): MappingProfile {
        val axes = joystickAxes(device)
        val bindings = ArrayList<MappingProfile.Binding>()
        for ((control, spec) in entry.fields) {
            if (control == "platform" || control == "crc" || control == "hint") continue
            val target = TARGETS[control] ?: continue
            val sources = parseSpec(spec, axes, analog = target is InputTarget.Analog)
            for ((source, invert) in sources) {
                if (target is InputTarget.Analog && source is InputSource.Axis) {
                    // Identity mapping: deflection in srcDir drives the target in the same
                    // direction; "~" inversion flips it (src +1 → tgt −1 and vice versa).
                    val tgtDir = if (invert) -source.direction else source.direction
                    bindings += MappingProfile.Binding(
                        source, InputTarget.Analog(target.stick, target.axis, tgtDir)
                    )
                } else {
                    bindings += MappingProfile.Binding(source, target)
                }
            }
        }
        return MappingProfile(name = entry.name, bindings = bindings)
    }

    /** The device's non-hat joystick axes sorted by axis id — SDL's aN index order. */
    fun joystickAxes(device: InputDevice?): List<Int> =
        device?.motionRanges
            ?.filter { it.source and InputDevice.SOURCE_CLASS_JOYSTICK != 0 }
            ?.map { it.axis }
            ?.distinct()
            ?.filter { it != MotionEvent.AXIS_HAT_X && it != MotionEvent.AXIS_HAT_Y }
            ?.sorted()
            ?: listOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ)

    /** Parse one SDL element spec ("b1", "a3", "+a2", "-a1~", "h0.4") into sources. */
    private fun parseSpec(
        spec: String,
        axes: List<Int>,
        analog: Boolean,
    ): List<Pair<InputSource, Boolean>> {
        var s = spec
        var halfDir = 0
        if (s.startsWith("+")) { halfDir = +1; s = s.substring(1) }
        else if (s.startsWith("-")) { halfDir = -1; s = s.substring(1) }
        var invert = false
        if (s.endsWith("~")) { invert = true; s = s.dropLast(1) }

        return when {
            s.startsWith("b") -> {
                val idx = s.substring(1).toIntOrNull() ?: return emptyList()
                val keyCode = SDL_BUTTON_TO_KEYCODE.getOrNull(idx) ?: return emptyList()
                listOf(InputSource.Key(keyCode) to false)
            }
            s.startsWith("h") -> {
                val dot = s.indexOf('.')
                if (dot < 0) return emptyList()
                val mask = s.substring(dot + 1).toIntOrNull() ?: return emptyList()
                val source = when (mask) {
                    1 -> InputSource.Axis(MotionEvent.AXIS_HAT_Y, -1) // up
                    2 -> InputSource.Axis(MotionEvent.AXIS_HAT_X, +1) // right
                    4 -> InputSource.Axis(MotionEvent.AXIS_HAT_Y, +1) // down
                    8 -> InputSource.Axis(MotionEvent.AXIS_HAT_X, -1) // left
                    else -> return emptyList()
                }
                listOf(source to false)
            }
            s.startsWith("a") -> {
                val idx = s.substring(1).toIntOrNull() ?: return emptyList()
                val axis = axes.getOrNull(idx) ?: return emptyList()
                if (analog) {
                    // full axis drives an analog target; caller emits both directions
                    listOf(
                        InputSource.Axis(axis, -1) to invert,
                        InputSource.Axis(axis, +1) to invert,
                    )
                } else {
                    val dir = if (halfDir != 0) halfDir else +1
                    listOf(InputSource.Axis(axis, if (invert) -dir else dir) to false)
                }
            }
            else -> emptyList()
        }
    }
}
