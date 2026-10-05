package com.example.vortex_player

class LyricLine(val timeMs: Long, val text: String)

sealed class Lyrics {
    class Synced(val lines: List<LyricLine>) : Lyrics()
    class Plain(val lines: List<String>) : Lyrics()
    object Instrumental : Lyrics()
    object NotFound : Lyrics()
    object Offline : Lyrics()
}

object LrcParser {

    private val TIMESTAMP = Regex("""\[(\d+):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parse(text: String): Lyrics {
        val synced = mutableListOf<LyricLine>()
        val plain = mutableListOf<String>()
        for (raw in text.lineSequence()) {
            val stamps = TIMESTAMP.findAll(raw).toList()
            if (stamps.isEmpty()) {
                // Skips LRC metadata such as [ar:...] and section headers such as [Chorus].
                if (!raw.trimStart().startsWith("[")) plain.add(raw.trim())
                continue
            }
            val content = raw.replace(TIMESTAMP, "").trim()
            for (stamp in stamps) {
                val (min, sec, frac) = stamp.destructured
                val ms = if (frac.isEmpty()) 0L else frac.padEnd(3, '0').take(3).toLong()
                synced.add(LyricLine(min.toLong() * 60_000 + sec.toLong() * 1000 + ms, content))
            }
        }
        val trimmedPlain = plain.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
        return when {
            synced.isNotEmpty() -> Lyrics.Synced(synced.sortedBy { it.timeMs })
            trimmedPlain.isNotEmpty() -> Lyrics.Plain(trimmedPlain)
            else -> Lyrics.NotFound
        }
    }

    fun stripTimestamps(text: String): String =
        text.lineSequence().joinToString("\n") { it.replace(TIMESTAMP, "").trim() }
}
