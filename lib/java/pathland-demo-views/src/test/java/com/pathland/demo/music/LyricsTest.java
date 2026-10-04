package com.pathland.demo.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The music player's lyric model: the embedded SRT cues are parsed and the
 * newest lyric line resolves per play position (karaoke-accumulated cues, with
 * the {@code ♪} glyphs stripped).
 */
class LyricsTest {

    @Test
    void parsesEmbeddedSrtAndResolvesTheNewestLine() {
        var cues = Lyrics.cuesFor("Building on Solid Ground");
        assertFalse(cues.isEmpty(), "the demo ships lyrics for the first track");
        Lyrics.Cue first = cues.get(0);
        assertEquals(0f, first.start());
        assertTrue(first.end() > 0f);
        String line = Lyrics.lineAt("Building on Solid Ground", first.start() + 0.001f);
        assertFalse(line.isBlank());
        assertFalse(line.contains("♪"), "music-note glyphs are stripped");
    }

    @Test
    void cueBoundariesAdvanceTheLyricLine() {
        var cues = Lyrics.cuesFor("Pathland Crossing");
        assertTrue(cues.size() >= 2, "the song has multiple cues");
        String a = Lyrics.lineAt("Pathland Crossing", cues.get(0).start() + 0.001f);
        String b = Lyrics.lineAt("Pathland Crossing", cues.get(1).start() + 0.001f);
        assertFalse(a.isBlank());
        assertFalse(b.isBlank());
        assertNotEquals(a, b, "a cue boundary changes the displayed line");
    }

    @Test
    void returnsEmptyOutOfRangeOrWithoutLyrics() {
        assertEquals("", Lyrics.lineAt("Building on Solid Ground", 999999f),
                "no cue covers the position");
        assertEquals("", Lyrics.lineAt("No Such Track", 0f),
                "a track without a lyrics file has no cues");
        assertTrue(Lyrics.cuesFor("No Such Track").isEmpty());
    }

    @Test
    void newTracksShipLyricsExtractedFromTheMp4s() {
        assertFalse(Lyrics.cuesFor("Across the Open Land").isEmpty(),
                "Across the Open Land lyrics were extracted from its mp4");
        assertFalse(Lyrics.cuesFor("Sixteen Bytes").isEmpty(),
                "Sixteen Bytes lyrics were extracted from its mp4");
    }
}