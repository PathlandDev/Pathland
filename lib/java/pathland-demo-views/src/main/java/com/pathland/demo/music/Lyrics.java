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
                return cleanedLines(cue);
            }
        }
        return List.of();
    }

    /**
     * The index of the line **currently being sung** within the covering cue's
     * cleaned block. Only the lines this cue ADDS over the previous cue are sung
     * during its window (the earlier block lines are the repeated chorus
     * context), so the current index advances proportionally across those new
     * lines — for a rolling cue that is its (constant) last line. {@code -1}
     * when out of range / no lyrics / a blank block.
     */
    public static int currentIndex(String title, float seconds) {
        return currentIndexIn(cuesFor(title), seconds);
    }

    /** The covering cue's current (being-sung) block index — shared by
     *  {@link #currentIndex} and {@link #previousLine} so the two-line pill is
     *  always consistent. {@code -1} when out of range / no lyrics / blank. */
    private static int currentIndexIn(List<Cue> cues, float seconds) {
        for (int ci = 0; ci < cues.size(); ci++) {
            Cue cue = cues.get(ci);
            if (seconds >= cue.start() && seconds < cue.end()) {
                List<String> lines = cleanedLines(cue);
                int n = lines.size();
                if (n == 0) {
                    return -1;
                }
                int newCount = newLineCount(cues, ci);
                if (newCount <= 0) {
                    return n - 1; // a hold cue: the newest line stays current
                }
                float window = cue.end() - cue.start();
                float t = window > 0 ? (seconds - cue.start()) / window : 0f;
                int newIndex = Math.min(newCount - 1, (int) (t * newCount));
                return n - newCount + newIndex;
            }
        }
        return -1;
    }

    /**
     * The number of lines this cue ADDS over the previous one — the lines
     * actually sung during its window. The blocks roll (each cue drops the
     * oldest and appends the newest), so the overlap is the longest suffix of
     * the previous block that matches a prefix of this one; the first cue's
     * whole block is new.
     */
    private static int newLineCount(List<Cue> cues, int ci) {
        List<String> cur = cleanedLines(cues.get(ci));
        if (ci == 0) {
            return cur.size();
        }
        List<String> prev = cleanedLines(cues.get(ci - 1));
        int max = Math.min(prev.size(), cur.size());
        int overlap = 0;
        for (int l = 1; l <= max; l++) {
            boolean match = true;
            for (int j = 0; j < l; j++) {
                if (!cur.get(j).equals(prev.get(prev.size() - l + j))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                overlap = l;
            }
        }
        return cur.size() - overlap;
    }

    /**
     * The line sung immediately before the current one — the dimmed context row
     * of the two-line pill. Within a cue it is the line before the current one;
     * at the first line of a block it is the previous cue's last line (the block
     * rolls), so the pill stays two lines across cue boundaries. {@code ""} only
     * for the very first line of the track / out of range / no lyrics.
     */
    public static String previousLine(String title, float seconds) {
        List<Cue> cues = cuesFor(title);
        for (int ci = 0; ci < cues.size(); ci++) {
            Cue cue = cues.get(ci);
            if (seconds >= cue.start() && seconds < cue.end()) {
                List<String> lines = cleanedLines(cue);
                if (lines.isEmpty()) {
                    return "";
                }
                int current = currentIndexIn(cues, seconds);
                if (current > 0) {
                    return lines.get(current - 1);
                }
                if (ci > 0) {
                    List<String> previous = cleanedLines(cues.get(ci - 1));
                    return previous.isEmpty() ? "" : previous.get(previous.size() - 1);
                }
                return "";
            }
        }
        return "";
    }

    /** A cue's display lines (clean, non-empty, {@code ♪} stripped). */
    private static List<String> cleanedLines(Cue cue) {
        return cue.lines().stream()
                .map(Lyrics::clean)
                .filter(l -> !l.isEmpty())
                .toList();
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