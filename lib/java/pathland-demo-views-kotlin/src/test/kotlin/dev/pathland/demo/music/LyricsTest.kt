package dev.pathland.demo.music

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The music player's lyric model: SRT parsing and karaoke block resolution. */
class LyricsTest {

    @Test
    fun parsesEmbeddedSrtAndResolvesTheCurrentBlock() {
        val cues = Lyrics.cuesFor("Building on Solid Ground")
        assertFalse(cues.isEmpty(), "the demo ships lyrics for the first track")
        val first = cues[0]
        assertEquals(0f, first.start)
        assertTrue(first.end > 0f)
        val block = Lyrics.blockFor("Building on Solid Ground", first.start + 0.001f)
        assertFalse(block.isEmpty())
        for (line in block) {
            assertFalse(line.isBlank())
            assertFalse(line.contains("♪"), "music-note glyphs are stripped")
        }
    }

    @Test
    fun theBlockRollsForwardAtCueBoundaries() {
        val cues = Lyrics.cuesFor("Pathland Crossing")
        assertTrue(cues.size >= 2, "the song has multiple cues")
        val a = Lyrics.blockFor("Pathland Crossing", cues[0].start + 0.001f)
        val b = Lyrics.blockFor("Pathland Crossing", cues[1].start + 0.001f)
        assertFalse(a.isEmpty())
        assertFalse(b.isEmpty())
        assertNotEquals(a, b, "a cue boundary rolls the karaoke block forward")
    }

    @Test
    fun theCurrentLineAdvancesThroughTheFirstCuesNewLines() {
        val cues = Lyrics.cuesFor("Pathland Crossing")
        assertTrue(cues[0].lines.size > 1, "the first cue adds multiple new lines")
        assertEquals(0, Lyrics.currentIndex("Pathland Crossing", cues[0].start + 0.001f))
        val last = Lyrics.blockFor("Pathland Crossing", cues[0].start + 0.001f).size - 1
        assertEquals(last, Lyrics.currentIndex("Pathland Crossing", cues[0].end - 0.001f))
    }

    @Test
    fun aRollingCueHoldsItsNewLineForTheWholeWindow() {
        val cues = Lyrics.cuesFor("Pathland Crossing")
        assertTrue(cues.size >= 2, "the song has multiple cues")
        val cue = cues[1]
        val last = Lyrics.blockFor("Pathland Crossing", cue.start + 0.001f).size - 1
        val mid = cue.start + (cue.end - cue.start) / 2f
        assertEquals(last, Lyrics.currentIndex("Pathland Crossing", cue.start + 0.001f))
        assertEquals(last, Lyrics.currentIndex("Pathland Crossing", mid))
    }

    @Test
    fun currentIndexIsMinusOneOutOfRangeOrWithoutLyrics() {
        assertEquals(-1, Lyrics.currentIndex("Building on Solid Ground", 999999f))
        assertEquals(-1, Lyrics.currentIndex("No Such Track", 0f))
    }

    @Test
    fun previousLineWithinACueIsTheLineBefore() {
        val cues = Lyrics.cuesFor("Pathland Crossing")
        val cue = cues.firstOrNull { it.lines.size > 1 }
        assertNotNull(cue, "the song has a multi-line cue")
        val half = cue!!.start + (cue.end - cue.start) / 2f
        val current = Lyrics.currentIndex("Pathland Crossing", half)
        assertTrue(current > 0, "mid-cue the current line is past the first")
        val prev = Lyrics.previousLine("Pathland Crossing", half)
        assertEquals(Lyrics.blockFor("Pathland Crossing", half)[current - 1], prev)
    }

    @Test
    fun previousLineSpansCueBoundaries() {
        val cues = Lyrics.cuesFor("Pathland Crossing")
        assertTrue(cues.size >= 2, "the song has multiple cues")
        val prev = Lyrics.previousLine("Pathland Crossing", cues[1].start + 0.001f)
        val firstBlock = Lyrics.blockFor("Pathland Crossing", cues[0].start + 0.001f)
        assertFalse(prev.isBlank())
        assertEquals(firstBlock.last(), prev)
    }

    @Test
    fun previousLineIsStableAcrossARollingCueWindow() {
        val cues = Lyrics.cuesFor("Pathland Crossing")
        assertTrue(cues.size >= 2, "the song has multiple cues")
        val cue = cues[1]
        val start = cue.start + 0.001f
        val mid = cue.start + (cue.end - cue.start) / 2f
        val atStart = Lyrics.previousLine("Pathland Crossing", start)
        val atMid = Lyrics.previousLine("Pathland Crossing", mid)
        assertFalse(atStart.isBlank())
        assertEquals(atStart, atMid)
    }

    @Test
    fun previousLineEmptyOnlyAtTheVeryFirstLine() {
        val cues = Lyrics.cuesFor("Building on Solid Ground")
        assertEquals("", Lyrics.previousLine("Building on Solid Ground", cues[0].start + 0.001f))
        assertEquals("", Lyrics.previousLine("Building on Solid Ground", 999999f))
        assertEquals("", Lyrics.previousLine("No Such Track", 0f))
    }

    @Test
    fun returnsEmptyOutOfRangeOrWithoutLyrics() {
        assertTrue(Lyrics.blockFor("Building on Solid Ground", 999999f).isEmpty())
        assertTrue(Lyrics.blockFor("No Such Track", 0f).isEmpty())
        assertTrue(Lyrics.cuesFor("No Such Track").isEmpty())
    }

    @Test
    fun newTracksShipLyricsExtractedFromTheMp4s() {
        assertFalse(Lyrics.blockFor("Across the Open Land", 5f).isEmpty())
        assertFalse(Lyrics.blockFor("Sixteen Bytes", 5f).isEmpty())
    }
}
