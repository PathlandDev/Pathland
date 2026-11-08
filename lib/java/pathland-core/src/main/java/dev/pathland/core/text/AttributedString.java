package dev.pathland.core.text;

import dev.pathland.core.protocol.Properties;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Text plus range-scoped style runs and links — the app-side model for
 * {@code PARAMETER::SET_ATTRIBUTED_TEXT}.
 *
 * <p>Ranges are <b>UTF-8 byte offsets</b> (the wire convention). The {@link Builder}
 * takes character indices and converts them at {@link Builder#build()}; markdown
 * payloads decode directly into byte offsets.
 */
public final class AttributedString {
    private static final Set<Integer> SUPPORTED = new HashSet<>(Arrays.asList(
            Properties.FONT_SIZE,
            Properties.FONT_WEIGHT,
            Properties.COLOR,
            Properties.FONT_STYLE,
            Properties.FONT_DESIGN,
            Properties.UNDERLINE,
            Properties.STRIKETHROUGH));

    private final String text;
    private final List<StyleRun> runs;
    private final List<LinkRun> links;

    private AttributedString(String text, List<StyleRun> runs, List<LinkRun> links) {
        this.text = text;
        this.runs = runs;
        this.links = links;
        int length = text.getBytes(StandardCharsets.UTF_8).length;
        for (StyleRun run : runs) {
            if (!(run.start() >= 0 && run.start() <= run.end() && run.end() <= length)) {
                throw new IllegalArgumentException(
                        "style run " + run.start() + ".." + run.end()
                                + " is outside the text (" + length + " bytes)");
            }
        }
        for (LinkRun link : links) {
            if (!(link.start() >= 0 && link.start() <= link.end() && link.end() <= length)) {
                throw new IllegalArgumentException(
                        "link " + link.start() + ".." + link.end()
                                + " is outside the text (" + length + " bytes)");
            }
        }
    }

    public String text() {
        return text;
    }

    public List<StyleRun> runs() {
        return runs;
    }

    public List<LinkRun> links() {
        return links;
    }

    public String getText() {
        return text;
    }

    public List<StyleRun> getRuns() {
        return runs;
    }

    public List<LinkRun> getLinks() {
        return links;
    }

    /** Whether this carries no style runs or links (plain text). */
    public boolean isPlain() {
        return runs.isEmpty() && links.isEmpty();
    }

    /** Encode the {@code SET_ATTRIBUTED_TEXT} payload (without the arena length wrapper). */
    public byte[] encode() {
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeIntLE(out, textBytes.length);
        out.writeBytes(textBytes);
        writeIntLE(out, runs.size());
        for (StyleRun run : runs) {
            writeIntLE(out, run.start());
            writeIntLE(out, run.end());
            List<TextAttribute> supported = new ArrayList<>();
            for (TextAttribute attribute : run.attributes()) {
                if (isSupported(attribute)) {
                    supported.add(attribute);
                }
            }
            writeIntLE(out, supported.size());
            for (TextAttribute attribute : supported) {
                writeShortLE(out, attribute.property());
                out.write(attribute.valueType() & 0xFF);
                out.write(0);
                writeIntLE(out, attribute.value());
            }
        }
        writeIntLE(out, links.size());
        for (LinkRun link : links) {
            byte[] url = link.url().getBytes(StandardCharsets.UTF_8);
            writeIntLE(out, link.start());
            writeIntLE(out, link.end());
            writeIntLE(out, url.length);
            out.writeBytes(url);
        }
        return out.toByteArray();
    }

    private static boolean isSupported(TextAttribute attribute) {
        return SUPPORTED.contains(attribute.property());
    }

    public static AttributedString plain(String text) {
        return new AttributedString(text, Collections.emptyList(), Collections.emptyList());
    }

    public static AttributedString of(String text, List<StyleRun> runs, List<LinkRun> links) {
        return new AttributedString(text, runs, links);
    }

    /** Start a range-tracking builder (inputs are character indices). */
    public static Builder builder() {
        return new Builder();
    }

    /** Decode a native markdown payload (UTF-8 byte offsets already). */
    public static AttributedString decode(byte[] payload) {
        Cursor cursor = new Cursor(payload);
        int textLen = cursor.i32();
        String text = new String(cursor.bytes(textLen), StandardCharsets.UTF_8);
        int runCount = cursor.i32();
        List<StyleRun> runs = new ArrayList<>(runCount);
        for (int i = 0; i < runCount; i++) {
            int start = cursor.i32();
            int end = cursor.i32();
            int attrCount = cursor.i32();
            List<TextAttribute> attributes = new ArrayList<>(attrCount);
            for (int j = 0; j < attrCount; j++) {
                int property = cursor.i16();
                int valueType = cursor.u8();
                cursor.u8(); // reserved
                int value = cursor.i32();
                TextAttribute attribute = new TextAttribute(property, valueType, value);
                if (isSupported(attribute)) {
                    attributes.add(attribute);
                }
            }
            runs.add(new StyleRun(start, end, attributes));
        }
        int linkCount = cursor.i32();
        List<LinkRun> links = new ArrayList<>(linkCount);
        for (int i = 0; i < linkCount; i++) {
            int start = cursor.i32();
            int end = cursor.i32();
            int urlLen = cursor.i32();
            String url = new String(cursor.bytes(urlLen), StandardCharsets.UTF_8);
            links.add(new LinkRun(start, end, url));
        }
        return new AttributedString(text, runs, links);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AttributedString)) {
            return false;
        }
        AttributedString that = (AttributedString) other;
        return Objects.equals(text, that.text)
                && Objects.equals(runs, that.runs)
                && Objects.equals(links, that.links);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, runs, links);
    }

    @Override
    public String toString() {
        return "AttributedString(text=" + text + ", runs=" + runs + ", links=" + links + ")";
    }

    private static void writeIntLE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    private static void writeShortLE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
    }

    private static final class Cursor {
        private final byte[] payload;
        private int p;

        Cursor(byte[] payload) {
            this.payload = payload;
        }

        void need(int n) {
            if (p + n > payload.length) {
                throw new IllegalArgumentException("attributed text payload truncated");
            }
        }

        int u8() {
            need(1);
            return payload[p++] & 0xFF;
        }

        int i16() {
            need(2);
            int value = (payload[p] & 0xFF) | ((payload[p + 1] & 0xFF) << 8);
            p += 2;
            return value;
        }

        int i32() {
            need(4);
            int value = (payload[p] & 0xFF)
                    | ((payload[p + 1] & 0xFF) << 8)
                    | ((payload[p + 2] & 0xFF) << 16)
                    | ((payload[p + 3] & 0xFF) << 24);
            p += 4;
            return value;
        }

        byte[] bytes(int n) {
            need(n);
            byte[] value = Arrays.copyOfRange(payload, p, p + n);
            p += n;
            return value;
        }
    }

    /** Range-tracking builder: append text, then mark character ranges. */
    public static final class Builder {
        private final StringBuilder text = new StringBuilder();
        private final List<StyleRun> runs = new ArrayList<>();
        private final List<LinkRun> links = new ArrayList<>();

        private Builder() {
        }

        public Builder append(String value) {
            text.append(value);
            return this;
        }

        public Builder bold(int start, int end) {
            return style(start, end, TextAttribute.fontWeight(700f));
        }

        public Builder italic(int start, int end) {
            return style(start, end, TextAttribute.italic());
        }

        public Builder monospaced(int start, int end) {
            return style(start, end, TextAttribute.fontDesign(3));
        }

        public Builder underline(int start, int end) {
            return style(start, end, TextAttribute.underline());
        }

        public Builder strikethrough(int start, int end) {
            return style(start, end, TextAttribute.strikethrough());
        }

        public Builder fontSize(int start, int end, float points) {
            return style(start, end, TextAttribute.fontSize(points));
        }

        public Builder color(int start, int end, int argb) {
            return style(start, end, TextAttribute.color(argb));
        }

        public Builder link(int start, int end, String url) {
            links.add(new LinkRun(byteOffset(start), byteOffset(end), url));
            return this;
        }

        private Builder style(int start, int end, TextAttribute attribute) {
            runs.add(new StyleRun(byteOffset(start), byteOffset(end),
                    Collections.singletonList(attribute)));
            return this;
        }

        private int byteOffset(int charIndex) {
            String value = text.toString();
            if (!(charIndex >= 0 && charIndex <= value.length())) {
                throw new IllegalArgumentException(
                        "character index " + charIndex + " is out of range");
            }
            return value.substring(0, charIndex).getBytes(StandardCharsets.UTF_8).length;
        }

        public AttributedString build() {
            return new AttributedString(
                    text.toString(), new ArrayList<>(runs), new ArrayList<>(links));
        }
    }
}
