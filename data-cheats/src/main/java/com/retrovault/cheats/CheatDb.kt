package com.retrovault.cheats

/**
 * One cheat parsed from a CWCheat `cheat.db`: display [name], the raw code text handed to the
 * core (`_L` lines joined by newlines), and whether the DB marked it on by default.
 */
data class Cheat(
    val name: String,
    val code: String,
    val defaultOn: Boolean,
)

/**
 * CWCheat `cheat.db` parser. Format (one file covers thousands of games):
 * ```
 * _S ULUS-10041          # game section (serial; may be dashless "ULUS10041")
 * _G Game Title
 * _C0 Cheat Name         # _C0 = off by default, _C1 = on by default
 * _L 0x........ 0x....... # one or more code lines
 * ```
 *
 * Legal note: cheat databases are user-supplied and NEVER bundled in the app; this only
 * parses what the user imports.
 */
object CheatDb {

    /** Parse the whole DB into canonical-serial → cheats. */
    fun parse(text: String): Map<String, List<Cheat>> {
        val out = LinkedHashMap<String, MutableList<Cheat>>()
        var serial: String? = null
        var cheatName: String? = null
        var cheatDefaultOn = false
        val codeLines = StringBuilder()

        fun flushCheat() {
            val s = serial ?: return
            val name = cheatName ?: return
            if (codeLines.isNotEmpty()) {
                out.getOrPut(s) { mutableListOf() }
                    .add(Cheat(name, codeLines.toString().trim(), cheatDefaultOn))
            }
            cheatName = null
            codeLines.setLength(0)
        }

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("_S") -> {
                    flushCheat()
                    serial = canonicalSerial(line.substring(2).trim())
                }
                line.startsWith("_G") -> flushCheat() // game title — ignored for lookup
                line.startsWith("_C") -> {
                    flushCheat()
                    // "_C0 Name" / "_C1 Name"
                    val body = line.substring(2).trim()
                    cheatDefaultOn = body.firstOrNull() == '1'
                    cheatName = body.dropWhile { it.isDigit() }.trim().ifEmpty { "Cheat" }
                }
                line.startsWith("_L") -> {
                    if (cheatName != null) {
                        if (codeLines.isNotEmpty()) codeLines.append('\n')
                        codeLines.append(line.substring(2).trim())
                    }
                }
            }
        }
        flushCheat()
        return out
    }

    /** Just the cheats for one serial (case/format-normalized), or empty. */
    fun cheatsFor(text: String, serial: String): List<Cheat> =
        parse(text)[canonicalSerial(serial)] ?: emptyList()

    /** "ULUS10041" or "ULUS-10041" → "ULUS-10041". */
    fun canonicalSerial(raw: String): String {
        val s = raw.uppercase().replace("_", "").trim()
        if (s.contains('-')) return s
        val m = Regex("^([A-Z]{2,4})([0-9]{3,6})$").find(s) ?: return s
        return "${m.groupValues[1]}-${m.groupValues[2]}"
    }
}
