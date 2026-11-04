package dev.pathland.demo.music.model

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * The music player's subtitle/lyric model: parses a track's embedded SRT and
 * resolves the current karaoke block for a play position. The cues are a rolling
 * window — each shows the repeated chorus lines and appends the newest (last)
 * line, the one being sung — so the display advances monotonically as the block
 * rolls forward at each cue boundary.
 *
 * The SRTs ship as shared demo assets named by the track title with underscores
 * (`Building on Solid Ground` → `Building_on_Solid_Ground.srt`); a track without
 * a lyrics file simply has no cues, so the subtitle pill stays hidden.
 */
object Lyrics {

    /** A single subtitle cue (seconds; inclusive start, exclusive end). */
    data class Cue(val start: Float, val end: Float, val lines: List<String>)

    private val CUE_TIME = Regex(
        "(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})",
    )

    private val CACHE = ConcurrentHashMap<String, List<Cue>>()

    /** The lyrics file name for a track title (spaces → underscores). */
    fun fileFor(title: String): String = title.replace(' ', '_') + ".srt"

    /**
     * The current karaoke block at [seconds]: the covering cue's lines (cleaned,
     * `♪` stripped, non-empty) in display order. Empty when out of range, no
     * lyrics file, or the cue is blank.
     */
    fun blockFor(title: String, seconds: Float): List<String> {
        for (cue in cuesFor(title)) {
            if (seconds >= cue.start && seconds < cue.end) {
                return cleanedLines(cue)
            }
        }
        return emptyList()
    }

    /**
     * The index of the line currently being sung within the covering cue's
     * cleaned block. Only the lines this cue adds over the previous cue are sung
     * during its window. `-1` when out of range, no lyrics, or a blank block.
     */
    fun currentIndex(title: String, seconds: Float): Int =
        currentIndexIn(cuesFor(title), seconds)

    private fun currentIndexIn(cues: List<Cue>, seconds: Float): Int {
        for (ci in cues.indices) {
            val cue = cues[ci]
            if (seconds >= cue.start && seconds < cue.end) {
                val lines = cleanedLines(cue)
                val n = lines.size
                if (n == 0) return -1
                val newCount = newLineCount(cues, ci)
                if (newCount <= 0) return n - 1
                val window = cue.end - cue.start
                val t = if (window > 0f) (seconds - cue.start) / window else 0f
                val newIndex = minOf(newCount - 1, (t * newCount).toInt())
                return n - newCount + newIndex
            }
        }
        return -1
    }

    /**
     * The number of lines this cue adds over the previous one — the lines
     * actually sung during its window. The blocks roll (each cue drops the oldest
     * and appends the newest), so the overlap is the longest suffix of the
     * previous block that matches a prefix of this one; the first cue's whole
     * block is new.
     */
    private fun newLineCount(cues: List<Cue>, ci: Int): Int {
        val cur = cleanedLines(cues[ci])
        if (ci == 0) return cur.size
        val prev = cleanedLines(cues[ci - 1])
        val max = minOf(prev.size, cur.size)
        var overlap = 0
        for (l in 1..max) {
            var match = true
            for (j in 0 until l) {
                if (cur[j] != prev[prev.size - l + j]) {
                    match = false
                    break
                }
            }
            if (match) overlap = l
        }
        return cur.size - overlap
    }

    /**
     * The line sung immediately before the current one — the dimmed context row
     * of the two-line pill. Empty only for the very first line of the track, out
     * of range, or no lyrics.
     */
    fun previousLine(title: String, seconds: Float): String {
        val cues = cuesFor(title)
        for (ci in cues.indices) {
            val cue = cues[ci]
            if (seconds >= cue.start && seconds < cue.end) {
                val lines = cleanedLines(cue)
                if (lines.isEmpty()) return ""
                val current = currentIndexIn(cues, seconds)
                if (current > 0) return lines[current - 1]
                if (ci > 0) {
                    val previous = cleanedLines(cues[ci - 1])
                    return previous.lastOrNull() ?: ""
                }
                return ""
            }
        }
        return ""
    }

    /** A cue's display lines (clean, non-empty, `♪` stripped). */
    private fun cleanedLines(cue: Cue): List<String> =
        cue.lines.map(::clean).filter { it.isNotEmpty() }

    /** The parsed cues for a title (cached); empty when no lyrics file. */
    fun cuesFor(title: String): List<Cue> = CACHE.computeIfAbsent(title, ::load)

    private fun load(title: String): List<Cue> {
        val cues = ArrayList<Cue>()
        val name = "/META-INF/resources/_pathland/assets/lyrics/" + fileFor(title)
        try {
            val input: InputStream = Lyrics::class.java.getResourceAsStream(name) ?: return cues
            input.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                val text = ArrayList<String>()
                var inCue = false
                var start = 0f
                var end = 0f
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val value = line!!
                    if (value.isBlank()) {
                        if (inCue) {
                            cues.add(Cue(start, end, text.toList()))
                            text.clear()
                            inCue = false
                        }
                        continue
                    }
                    val match = CUE_TIME.find(value)
                    if (match != null) {
                        val groups = match.groupValues
                        start = toSeconds(groups[1], groups[2], groups[3], groups[4])
                        end = toSeconds(groups[5], groups[6], groups[7], groups[8])
                        inCue = true
                    } else if (inCue) {
                        text.add(value)
                    }
                }
                if (inCue) {
                    cues.add(Cue(start, end, text.toList()))
                }
            }
        } catch (_: Exception) {
            // A malformed lyrics file yields no cues; the pill stays hidden.
        }
        return cues
    }

    /** Strip the `♪` music-note glyphs and collapse the whitespace. */
    private fun clean(line: String): String =
        line.replace("♪", "").replace(Regex("\\s+"), " ").trim()

    private fun toSeconds(h: String, m: String, s: String, ms: String): Float =
        h.toInt() * 3600f + m.toInt() * 60f + s.toInt() + ms.toInt() / 1000f
}
