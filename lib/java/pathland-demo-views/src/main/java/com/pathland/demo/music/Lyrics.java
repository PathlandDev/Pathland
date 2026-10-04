package com.pathland.demo.music;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The music player's subtitle/lyric model: parses a track's embedded SRT and
 * resolves the current karaoke block for a play position. The cues are a
 * rolling window — each shows the repeated chorus lines and appends the newest
 * (last) line, which is the one being sung — so the display advances
 * monotonically as the block rolls forward at each cue boundary.
 *
 * <p>The SRTs ship as shared demo assets ({@code DemoAssets}) named by the track
 * title with underscores ({@code Building on Solid Ground} →
 * {@code Building_on_Solid_Ground.srt}); a track without a lyrics file simply has
 * no cues, so the subtitle pill stays hidden.
 */
public final class Lyrics {

    /** A single subtitle cue (seconds; inclusive start, exclusive end). */
    record Cue(float start, float end, List<String> lines) {}

    private static final Pattern CUE_TIME = Pattern.compile(
            "(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})");

    private static final Map<String, List<Cue>> CACHE = new ConcurrentHashMap<>();

    private Lyrics() {
    }

    /** The lyrics file name for a track title (spaces → underscores). */
    public static String fileFor(String title) {
        return title.replace(' ', '_') + ".srt";
    }

    /**
     * The **current karaoke block** at {@code seconds}: the covering cue's
     * lines (cleaned, {@code ♪} stripped, non-empty) in display order — the last
     * line is the one being sung, the earlier lines are the chorus context. An
     * empty list when out of range / the track has no lyrics file / the cue is
     * blank.
     */
    public static List<String> blockFor(String title, float seconds) {
        for (Cue cue : cuesFor(title)) {
            if (seconds >= cue.start() && seconds < cue.end()) {
                return cue.lines().stream()
                        .map(Lyrics::clean)
                        .filter(l -> !l.isEmpty())
                        .toList();
            }
        }
        return List.of();
    }

    /**
     * The index of the line **currently being sung** within the covering cue's
     * cleaned block: the block's lines are sung in order across the cue's
     * {@code [start, end)} window, so the current index is the proportional
     * sub-position ({@code 0} at the start, {@code n-1} at the end). {@code -1}
     * when out of range / no lyrics / a blank block.
     */
    public static int currentIndex(String title, float seconds) {
        for (Cue cue : cuesFor(title)) {
            if (seconds >= cue.start() && seconds < cue.end()) {
                int n = (int) cue.lines().stream()
                        .map(Lyrics::clean)
                        .filter(l -> !l.isEmpty())
                        .count();
                if (n == 0) {
                    return -1;
                }
                float window = cue.end() - cue.start();
                float t = window > 0 ? (seconds - cue.start()) / window : 0f;
                return Math.min(n - 1, (int) (t * n));
            }
        }
        return -1;
    }

    /** The parsed cues for a title (cached); empty when no lyrics file. */
    public static List<Cue> cuesFor(String title) {
        return CACHE.computeIfAbsent(title, Lyrics::load);
    }

    private static List<Cue> load(String title) {
        List<Cue> cues = new ArrayList<>();
        String name = "/META-INF/resources/_pathland/assets/lyrics/" + fileFor(title);
        try (InputStream in = Lyrics.class.getResourceAsStream(name)) {
            if (in == null) {
                return cues;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<String> text = new ArrayList<>();
            boolean inCue = false;
            float start = 0f;
            float end = 0f;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    if (inCue) {
                        cues.add(new Cue(start, end, List.copyOf(text)));
                        text.clear();
                        inCue = false;
                    }
                    continue;
                }
                Matcher m = CUE_TIME.matcher(line);
                if (m.find()) {
                    start = toSeconds(m.group(1), m.group(2), m.group(3), m.group(4));
                    end = toSeconds(m.group(5), m.group(6), m.group(7), m.group(8));
                    inCue = true;
                } else if (inCue) {
                    text.add(line);
                }
            }
            if (inCue) {
                cues.add(new Cue(start, end, List.copyOf(text)));
            }
        } catch (Exception e) {
            // A malformed lyrics file yields no cues; the pill stays hidden.
        }
        return cues;
    }

    /** Strip the {@code ♪} music-note glyphs and collapse the whitespace. */
    private static String clean(String line) {
        return line.replace("♪", "").replaceAll("\\s+", " ").trim();
    }

    private static float toSeconds(String h, String m, String s, String ms) {
        return Integer.parseInt(h) * 3600f + Integer.parseInt(m) * 60f
                + Integer.parseInt(s) + Integer.parseInt(ms) / 1000f;
    }
}