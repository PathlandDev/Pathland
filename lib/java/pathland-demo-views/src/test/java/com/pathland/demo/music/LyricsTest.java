package com.pathland.demo.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The music player's lyric model: the embedded SRT cues are parsed and the
 * current karaoke block resolves per play position (a rolling window of cleaned
 * lines — the last line is the one being sung, the {@code ♪} glyphs stripped).
 */
class LyricsTest {

    @Test
    void parsesEmbeddedSrtAndResolvesTheCurrentBlock() {
        var cues = Lyrics.cuesFor("Building on Solid Ground");
        assertFalse(cues.isEmpty(), "the demo ships lyrics for the first track");
        Lyrics.Cue first = cues.get(0);
        assertEquals(0f, first.start());
        assertTrue(first.end() > 0f);
        var block = Lyrics.blockFor("Building on Solid Ground", first.start() + 0.001f);
        assertFalse(block.isEmpty());
        for (String line : block) {
            assertFalse(line.isBlank());
            assertFalse(line.contains("♪"), "music-note glyphs are stripped");
        }
    }

    @Test
    void theBlockRollsForwardAtCueBoundaries() {
        var cues = Lyrics.cuesFor("Pathland Crossing");
        assertTrue(cues.size() >= 2, "the song has multiple cues");
        var a = Lyrics.blockFor("Pathland Crossing", cues.get(0).start() + 0.001f);
        var b = Lyrics.blockFor("Pathland Crossing", cues.get(1).start() + 0.001f);
        assertFalse(a.isEmpty());
        assertFalse(b.isEmpty());
        assertNotEquals(a, b, "a cue boundary rolls the karaoke block forward");
    }

    @Test
    void returnsEmptyOutOfRangeOrWithoutLyrics() {
        assertTrue(Lyrics.blockFor("Building on Solid Ground", 999999f).isEmpty(),
                "no cue covers the position");
        assertTrue(Lyrics.blockFor("No Such Track", 0f).isEmpty(),
                "a track without a lyrics file has no cues");
        assertTrue(Lyrics.cuesFor("No Such Track").isEmpty());
    }

    @Test
    void newTracksShipLyricsExtractedFromTheMp4s() {
        assertFalse(Lyrics.blockFor("Across the Open Land", 5f).isEmpty(),
                "Across the Open Land lyrics were extracted from its mp4");
        assertFalse(Lyrics.blockFor("Sixteen Bytes", 5f).isEmpty(),
                "Sixteen Bytes lyrics were extracted from its mp4");
    }
}