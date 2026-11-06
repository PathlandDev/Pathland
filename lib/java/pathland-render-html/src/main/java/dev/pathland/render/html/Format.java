package dev.pathland.render.html;

/** Small formatting/escaping helpers shared by the renderer. */
final class Format {

    private Format() {}

    /**
     * Rust's {@code Display} for {@code f32}: integral values omit the decimal
     * point ({@code 4.0} → {@code 4}); non-integral values use the shortest
     * round-tripping decimal form.
     */
    static String f32(float value) {
        if (Float.isNaN(value)) {
            return "NaN";
        }
        if (Float.isInfinite(value)) {
            return value > 0 ? "inf" : "-inf";
        }
        if (value == 0.0f) {
            return (Float.floatToRawIntBits(value) == 0x80000000) ? "-0" : "0";
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e16f) {
            return Long.toString((long) value);
        }
        return Float.toString(value);
    }

    /** A {@code 0xAARRGGBB} color as CSS {@code rgba(r,g,b,a)}. */
    static String rgba(int argb) {
        long value = argb & 0xFFFFFFFFL;
        long r = (value >>> 16) & 0xFF;
        long g = (value >>> 8) & 0xFF;
        long b = value & 0xFF;
        float a = ((value >>> 24) & 0xFF) / 255.0f;
        return "rgba(" + r + "," + g + "," + b + "," + f32(a) + ")";
    }

    /** Escape text for an HTML body. */
    static String escape(String input) {
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /** Escape a value for a double-quoted HTML attribute. */
    static String escapeHtmlAttr(String input) {
        return input
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * Escape a value for embedding inside a JSON string (no surrounding quotes).
     * {@code <}, {@code >}, and {@code &} are unicode-escaped so application text
     * can never close the surrounding {@code <script>} element. Kept byte-identical
     * to the Rust renderer's {@code document::json_escape}.
     */
    static String jsonEscape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '<' -> out.append("\\u003C");
                case '>' -> out.append("\\u003E");
                case '&' -> out.append("\\u0026");
                case '\u2028' -> out.append("\\u2028");
                case '\u2029' -> out.append("\\u2029");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04X", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        return out.toString();
    }

    /** A token path's CSS variable name component (non-safe characters → {@code -}). */
    static String cssVarName(String path) {
        StringBuilder out = new StringBuilder(path.length());
        for (int i = 0; i < path.length(); i++) {
            char ch = path.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')
                    || ch == '-' || ch == '_') {
                out.append(ch);
            } else {
                out.append('-');
            }
        }
        return out.toString();
    }

    static String styleAttr(String css) {
        return css.isEmpty() ? "" : " style=\"" + css + "\"";
    }

    static String classAttr(String classes) {
        String trimmed = classes.trim();
        return trimmed.isEmpty() ? "" : " class=\"" + trimmed + "\"";
    }
}
