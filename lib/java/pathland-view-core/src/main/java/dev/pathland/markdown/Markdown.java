package dev.pathland.markdown;

import dev.pathland.view.core.AttributedString;
import dev.pathland.view.core.LinkRun;
import dev.pathland.view.core.StyleRun;
import dev.pathland.view.core.TextAttribute;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pathland's shared inline Markdown parser — a pure-Java port of the
 * {@code pathland-markdown} Rust crate.
 *
 * <p>Parses the inline subset — {@code **bold**} / {@code __bold__},
 * {@code *italic*} / {@code _italic_}, {@code `code`},
 * {@code ~~strikethrough~~}, {@code [label](url)}, and backslash escapes —
 * into a view-core {@link AttributedString}. It is pure JVM code with no native
 * dependency, so it runs on every platform (and under GraalVM native-image)
 * without a cdylib. The Rust crate remains the parser for the Rust DSL and other
 * non-JVM hosts; this implementation must stay behaviourally identical (UTF-8
 * byte offsets included).
 *
 * <p>Intraword underscores stay literal ({@code snake_case_name}), matching
 * CommonMark emphasis rules. Block-level Markdown, images, and reference links
 * are out of scope.
 */
public final class Markdown {

    private static final int CACHE_LIMIT = 512;
    private static final Map<String, AttributedString> CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, AttributedString> eldest) {
                    return size() > CACHE_LIMIT;
                }
            });

    private Markdown() {}

    /**
     * Whether the parser is available. Always {@code true} — the parser is pure
     * JVM code (kept for source compatibility with the former JNA binding).
     */
    public static boolean isAvailable() {
        return true;
    }

    /** Parse inline Markdown into an attributed string. */
    public static AttributedString parse(String text) {
        Output out = new Output();
        parseInto(text, out);
        return AttributedString.of(out.text.toString(), out.runs, out.links);
    }

    /**
     * Whether {@code text} contains any inline-Markdown marker. A cheap scan
     * lets plain strings skip the parser entirely.
     */
    public static boolean containsMarkdownSyntax(String text) {
        for (int i = 0; i < text.length(); i++) {
            switch (text.charAt(i)) {
                case '*':
                case '_':
                case '~':
                case '`':
                case '[':
                case '\\':
                    return true;
                default:
                    break;
            }
        }
        return false;
    }

    /**
     * Parse inline Markdown, falling back to plain text when the input has no
     * Markdown markers. Results are cached (bounded) so static literals and
     * repeated values parse once.
     */
    public static AttributedString parseOrPlain(String text) {
        if (!containsMarkdownSyntax(text)) {
            return AttributedString.plain(text);
        }
        AttributedString cached = CACHE.get(text);
        if (cached != null) {
            return cached;
        }
        AttributedString value;
        try {
            value = parse(text);
        } catch (RuntimeException e) {
            // Degrade to literal text rather than failing the whole view.
            value = AttributedString.plain(text);
        }
        CACHE.put(text, value);
        return value;
    }

    /** The accumulated output plus its running UTF-8 byte length. */
    private static final class Output {
        final StringBuilder text = new StringBuilder();
        final List<StyleRun> runs = new ArrayList<>();
        final List<LinkRun> links = new ArrayList<>();
        int bytes;

        void appendCodePoint(int codePoint) {
            text.appendCodePoint(codePoint);
            bytes += utf8Length(codePoint);
        }

        void appendChar(char value) {
            text.append(value);
            bytes += utf8Length(value);
        }

        void appendString(String value) {
            text.append(value);
            bytes += value.getBytes(StandardCharsets.UTF_8).length;
        }
    }

    private static void parseInto(String input, Output out) {
        int length = input.length();
        int i = 0;
        while (i < length) {
            char current = input.charAt(i);
            if (current == '\\') {
                if (i + 1 < length) {
                    int escaped = input.codePointAt(i + 1);
                    out.appendCodePoint(escaped);
                    i += 1 + Character.charCount(escaped);
                } else {
                    out.appendChar('\\');
                    i += 1;
                }
            } else if (current == '*' && i + 1 < length && input.charAt(i + 1) == '*') {
                int end = findDelimiter(input, i + 2, "**");
                if (end >= 0) {
                    int start = out.bytes;
                    parseInto(input.substring(i + 2, end), out);
                    out.runs.add(new StyleRun(start, out.bytes, List.of(TextAttribute.fontWeight(700f))));
                    i = end + 2;
                } else {
                    out.appendChar('*');
                    i += 1;
                }
            } else if (current == '*') {
                int end = findDelimiter(input, i + 1, "*");
                if (end >= 0) {
                    int start = out.bytes;
                    parseInto(input.substring(i + 1, end), out);
                    out.runs.add(new StyleRun(start, out.bytes, List.of(TextAttribute.italic())));
                    i = end + 1;
                } else {
                    out.appendChar('*');
                    i += 1;
                }
            } else if (current == '_' && i + 1 < length && input.charAt(i + 1) == '_'
                    && underscoreCanOpen(input, i)) {
                int end = findDelimiter(input, i + 2, "__");
                if (end >= 0) {
                    int start = out.bytes;
                    parseInto(input.substring(i + 2, end), out);
                    out.runs.add(new StyleRun(start, out.bytes, List.of(TextAttribute.fontWeight(700f))));
                    i = end + 2;
                } else {
                    out.appendChar('_');
                    i += 1;
                }
            } else if (current == '_' && underscoreCanOpen(input, i)) {
                int end = findDelimiter(input, i + 1, "_");
                if (end >= 0) {
                    int start = out.bytes;
                    parseInto(input.substring(i + 1, end), out);
                    out.runs.add(new StyleRun(start, out.bytes, List.of(TextAttribute.italic())));
                    i = end + 1;
                } else {
                    out.appendChar('_');
                    i += 1;
                }
            } else if (current == '~' && i + 1 < length && input.charAt(i + 1) == '~') {
                int end = findDelimiter(input, i + 2, "~~");
                if (end >= 0) {
                    int start = out.bytes;
                    parseInto(input.substring(i + 2, end), out);
                    out.runs.add(new StyleRun(start, out.bytes, List.of(TextAttribute.strikethrough(true))));
                    i = end + 2;
                } else {
                    out.appendChar('~');
                    i += 1;
                }
            } else if (current == '`') {
                int end = findDelimiter(input, i + 1, "`");
                if (end >= 0) {
                    int start = out.bytes;
                    out.appendString(input.substring(i + 1, end));
                    out.runs.add(new StyleRun(start, out.bytes, List.of(TextAttribute.fontDesign(3))));
                    i = end + 1;
                } else {
                    out.appendChar('`');
                    i += 1;
                }
            } else if (current == '[') {
                LinkParse link = parseLink(input, i);
                if (link != null) {
                    int start = out.bytes;
                    parseInto(input.substring(i + 1, link.labelEnd), out);
                    out.links.add(new LinkRun(start, out.bytes, link.url));
                    i = link.after;
                } else {
                    out.appendChar('[');
                    i += 1;
                }
            } else {
                int codePoint = input.codePointAt(i);
                out.appendCodePoint(codePoint);
                i += Character.charCount(codePoint);
            }
        }
    }

    /** Underscore emphasis is not allowed intraword ({@code snake_case} stays literal). */
    private static boolean underscoreCanOpen(String input, int at) {
        return at <= 0 || !isAlphanumeric(input.codePointBefore(at));
    }

    private static boolean underscoreCanClose(String input, int afterRun) {
        return afterRun >= input.length() || !isAlphanumeric(input.codePointAt(afterRun));
    }

    private static boolean isAlphanumeric(int codePoint) {
        return Character.isLetterOrDigit(codePoint);
    }

    /**
     * Find the next closing delimiter at or after {@code from}, returning its
     * character index, or {@code -1}. Escaped characters are skipped.
     *
     * <p>Asterisk runs are split so nesting works: for a run of three
     * ({@code ***}) the outer {@code **} close uses the last two stars and the
     * inner {@code *} close uses the first. A lone {@code *} never closes on a
     * {@code **} run (that is nested bold).
     */
    private static int findDelimiter(String input, int from, String needle) {
        int length = input.length();
        int i = from;
        while (i < length) {
            char current = input.charAt(i);
            if (current == '\\') {
                i += 1;
                if (i < length) {
                    i += Character.charCount(input.codePointAt(i));
                }
                continue;
            }
            switch (needle) {
                case "**": {
                    if (current == '*') {
                        int end = i;
                        while (end < length && input.charAt(end) == '*') {
                            end += 1;
                        }
                        if (end - i >= 2) {
                            return end - 2;
                        }
                    }
                    break;
                }
                case "*": {
                    if (current == '*') {
                        int end = i;
                        while (end < length && input.charAt(end) == '*') {
                            end += 1;
                        }
                        if (end - i == 1) {
                            return i;
                        }
                        i = end;
                        continue;
                    }
                    break;
                }
                case "__": {
                    if (current == '_') {
                        int end = i;
                        while (end < length && input.charAt(end) == '_') {
                            end += 1;
                        }
                        if (end - i >= 2 && underscoreCanClose(input, end)) {
                            return end - 2;
                        }
                    }
                    break;
                }
                case "_": {
                    if (current == '_') {
                        int end = i;
                        while (end < length && input.charAt(end) == '_') {
                            end += 1;
                        }
                        if (end - i == 1 && underscoreCanClose(input, end)) {
                            return i;
                        }
                        i = end;
                        continue;
                    }
                    break;
                }
                case "~~": {
                    if (current == '~') {
                        int end = i;
                        while (end < length && input.charAt(end) == '~') {
                            end += 1;
                        }
                        if (end - i >= 2) {
                            return end - 2;
                        }
                    }
                    break;
                }
                default: {
                    if (input.startsWith(needle, i)) {
                        return i;
                    }
                    break;
                }
            }
            i += Character.charCount(input.codePointAt(i));
        }
        return -1;
    }

    /** Parse {@code [label](url)} at {@code open}. */
    private static LinkParse parseLink(String input, int open) {
        int length = input.length();
        int i = open + 1;
        while (i < length) {
            char current = input.charAt(i);
            if (current == '\\') {
                i += 1;
                if (i < length) {
                    i += Character.charCount(input.codePointAt(i));
                }
                continue;
            }
            if (current == ']' && i + 1 < length && input.charAt(i + 1) == '(') {
                int labelEnd = i;
                int urlStart = i + 2;
                int j = urlStart;
                while (j < length) {
                    char urlChar = input.charAt(j);
                    if (urlChar == '\\') {
                        j += 1;
                        if (j < length) {
                            j += Character.charCount(input.codePointAt(j));
                        }
                        continue;
                    }
                    if (urlChar == ')') {
                        return new LinkParse(labelEnd, input.substring(urlStart, j), j + 1);
                    }
                    j += Character.charCount(input.codePointAt(j));
                }
                return null;
            }
            i += Character.charCount(input.codePointAt(i));
        }
        return null;
    }

    private static int utf8Length(int codePoint) {
        if (codePoint < 0x80) {
            return 1;
        }
        if (codePoint < 0x800) {
            return 2;
        }
        if (codePoint < 0x10000) {
            return 3;
        }
        return 4;
    }

    private static final class LinkParse {
        final int labelEnd;
        final String url;
        final int after;

        LinkParse(int labelEnd, String url, int after) {
            this.labelEnd = labelEnd;
            this.url = url;
            this.after = after;
        }
    }
}
