package dev.pathland.core.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A range of the text with a set of scalar attributes (UTF-8 byte offsets). */
public final class StyleRun {
    private final int start;
    private final int end;
    private final List<TextAttribute> attributes;

    public StyleRun(int start, int end, List<TextAttribute> attributes) {
        this.start = start;
        this.end = end;
        this.attributes = attributes;
    }

    public int start() {
        return start;
    }

    public int end() {
        return end;
    }

    public List<TextAttribute> attributes() {
        return attributes;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public List<TextAttribute> getAttributes() {
        return attributes;
    }

    public StyleRun bold() {
        return adding(TextAttribute.fontWeight(700f));
    }

    public StyleRun italic() {
        return adding(TextAttribute.italic());
    }

    public StyleRun monospaced() {
        return adding(TextAttribute.fontDesign(3));
    }

    public StyleRun strikethrough() {
        return adding(TextAttribute.strikethrough());
    }

    public StyleRun underline() {
        return adding(TextAttribute.underline());
    }

    private StyleRun adding(TextAttribute attribute) {
        List<TextAttribute> next = new ArrayList<>(attributes);
        next.add(attribute);
        return new StyleRun(start, end, Collections.unmodifiableList(next));
    }

    public static StyleRun of(int start, int end) {
        return new StyleRun(start, end, Collections.emptyList());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StyleRun)) {
            return false;
        }
        StyleRun that = (StyleRun) other;
        return start == that.start && end == that.end
                && Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end, attributes);
    }

    @Override
    public String toString() {
        return "StyleRun(start=" + start + ", end=" + end
                + ", attributes=" + attributes + ")";
    }
}
