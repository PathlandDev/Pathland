package com.pathland.demo.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The music player's lyric model: the embedded SRT cues are parsed and the
 * newest lyric line resolves per play position (karaoke-accumulated cues, with
 * the {@code ♪} glyphs stripped).
 */
class LyricsTest {

    @Test
    void parsesEmbeddedSrtAndResolvesTheCurrentLine() {
        var cues = Lyrics.cuesFor("Building on Solid Ground");
        assertFalse(cues.isEmpty(), "the demo ships lyrics for the first track");
        Lyrics.Cue first = cues.get(0);
        assertEquals(0f, first.start());
        assertTrue(first.end() > 0f);
        // At the start of a multi-line cue the FIRST line shows; the line
        // advances through the cue (proportional sub-timing) toward the last.
        String atStart = Lyrics.lineAt("Building on Solid Ground", first.start() + 0.001f);
        String atEnd = Lyrics.lineAt("Building on Solid Ground", first.end() - 0.001f);
        assertFalse(atStart.isBlank());
        assertFalse(atEnd.isBlank());
        assertFalse(atStart.contains("♪"), "music-note glyphs are stripped");
        if (first.lines().size() > 1) {
            assertNotEquals(atStart, atEnd, "the current line advances within the cue");
        }
    }

    @Test
    void theCurrentLineAdvancesWithinACue() {
        var cues = Lyrics.cuesFor("Pathland Crossing");
        Lyrics.Cue cue = cues.stream().filter(c -> c.lines().size() > 1)
                .findFirst().orElse(null);
        assertNotNull(cue, "the song has a multi-line cue");
        String atStart = Lyrics.lineAt("Pathland Crossing", cue.start() + 0.001f);
        String atEnd = Lyrics.lineAt("Pathland Crossing", cue.end() - 0.001f);
        assertNotEquals(atStart, atEnd, "the current line advances within the cue");
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