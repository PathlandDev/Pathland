package dev.pathland.core.text;

import java.util.Objects;

/** A range of the text that is a hyperlink (UTF-8 byte offsets). */
public final class LinkRun {
    private final int start;
    private final int end;
    private final String url;

    public LinkRun(int start, int end, String url) {
        this.start = start;
        this.end = end;
        this.url = url;
    }

    public int start() {
        return start;
    }

    public int end() {
        return end;
    }

    public String url() {
        return url;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LinkRun)) {
            return false;
        }
        LinkRun that = (LinkRun) other;
        return start == that.start && end == that.end && Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end, url);
    }

    @Override
    public String toString() {
        return "LinkRun(start=" + start + ", end=" + end + ", url=" + url + ")";
    }
}
