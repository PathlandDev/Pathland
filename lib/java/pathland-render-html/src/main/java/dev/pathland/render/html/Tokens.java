package dev.pathland.render.html;

import dev.pathland.core.protocol.ValueTypes;
import java.util.Map;
import java.util.TreeMap;

/** Design-token overrides collected from a snapshot batch (spec/TOKENS.md). */
final class Tokens {

    /** A resolved token value: either a wire 32-bit payload or a string. */
    static final class Value {
        final int type;
        final int bits;
        final String string;

        private Value(int type, int bits, String string) {
            this.type = type;
            this.bits = bits;
            this.string = string;
        }

        static Value ofString(String value) {
            return new Value(ValueTypes.STRING, 0, value);
        }

        static Value fromWire(int type, int value) {
            return switch (type) {
                case ValueTypes.COLOR, ValueTypes.U32, ValueTypes.F32, ValueTypes.U8,
                        ValueTypes.I32 -> new Value(type, value, null);
                default -> null;
            };
        }
    }

    private record Entry(int type, Value value) {}

    private final TreeMap<String, Entry> base = new TreeMap<>();
    private final TreeMap<String, Entry> dark = new TreeMap<>();

    /** {@code META::SET_COLOR_SCHEME} override: 1 = light, 2 = dark, null = platform. */
    Integer schemeOverride;

    void set(String path, int type, Value value) {
        if (path.startsWith(TokenSpec.DARK_PREFIX)) {
            dark.put(path.substring(TokenSpec.DARK_PREFIX.length()), new Entry(type, value));
        } else {
            base.put(path, new Entry(type, value));
        }
    }

    void reset() {
        base.clear();
        dark.clear();
    }

    private String declarations(boolean darkScope) {
        StringBuilder out = new StringBuilder();
        TreeMap<String, Entry> map = darkScope ? dark : base;
        for (Map.Entry<String, Entry> entry : map.entrySet()) {
            out.append(tokenToCssVar(entry.getKey()))
                    .append(':')
                    .append(tokenCssValue(entry.getKey(), entry.getValue().type, entry.getValue().value))
                    .append(';');
        }
        return out.toString();
    }

    String css() {
        StringBuilder css = new StringBuilder();
        String baseDecls = declarations(false);
        if (!baseDecls.isEmpty()) {
            css.append(":root{").append(baseDecls).append('}');
        }
        if (!baseDecls.isEmpty() || !dark.isEmpty()) {
            String darkDecls = declarations(true);
            String scoped = baseDecls + darkDecls;
            css.append("@media (prefers-color-scheme: dark){:root:not([data-pathland-scheme]){")
                    .append(scoped)
                    .append("}}");
            css.append(":root[data-pathland-scheme=\"dark\"]{").append(scoped).append('}');
        }
        StringBuilder forced = new StringBuilder();
        for (Map.Entry<String, Entry> entry : base.entrySet()) {
            forced.append(forcedDarkVar(entry.getKey()))
                    .append(':')
                    .append(tokenCssValue(entry.getKey(), entry.getValue().type, entry.getValue().value))
                    .append(';');
        }
        for (Map.Entry<String, Entry> entry : dark.entrySet()) {
            forced.append(forcedDarkVar(entry.getKey()))
                    .append(':')
                    .append(tokenCssValue(entry.getKey(), entry.getValue().type, entry.getValue().value))
                    .append(';');
        }
        if (!forced.isEmpty()) {
            css.append(":root{").append(forced).append('}');
        }
        return css.toString();
    }

    static String forcedDarkVar(String path) {
        String bare = path.startsWith(TokenSpec.DARK_PREFIX)
                ? path.substring(TokenSpec.DARK_PREFIX.length())
                : path;
        return TokenSpec.FORCED_DARK_VAR_PREFIX + Format.cssVarName(bare);
    }

    static String tokenToCssVar(String path) {
        String bare = path.startsWith(TokenSpec.DARK_PREFIX)
                ? path.substring(TokenSpec.DARK_PREFIX.length())
                : path;
        return TokenSpec.VAR_PREFIX + Format.cssVarName(bare);
    }

    static boolean isLengthToken(String path) {
        for (String prefix : TokenSpec.LENGTH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        for (String[] rule : TokenSpec.LENGTH_PREFIX_SUFFIX) {
            if (path.startsWith(rule[0]) && path.endsWith(rule[1])) {
                return true;
            }
        }
        for (String exact : TokenSpec.LENGTH_EXACT) {
            if (exact.equals(path)) {
                return true;
            }
        }
        if (path.startsWith("elevation.")) {
            for (String suffix : TokenSpec.LENGTH_ELEVATION_SUFFIXES) {
                if (path.endsWith(suffix)) {
                    return true;
                }
            }
        }
        return false;
    }

    static String tokenCssValue(String path, int valueType, Value value) {
        return switch (value.type) {
            case ValueTypes.STRING -> "'" + value.string.replace("\\", "\\\\").replace("'", "\\'") + "'";
            case ValueTypes.COLOR -> Format.rgba(value.bits);
            case ValueTypes.F32 -> {
                float v = Float.intBitsToFloat(value.bits);
                yield isLengthToken(path) ? Format.f32(v) + "px" : Format.f32(v);
            }
            case ValueTypes.I32 -> Integer.toString(value.bits);
            case ValueTypes.U8 -> Integer.toString(value.bits & 0xFF);
            default -> Integer.toString(value.bits);
        };
    }

    static String resolveTokenRef(String path) {
        boolean forcedDark = path.startsWith(TokenSpec.DARK_PREFIX);
        String bare = forcedDark ? path.substring(TokenSpec.DARK_PREFIX.length()) : path;
        if (bare.startsWith(TokenSpec.SPACE_FAMILY)) {
            String rest = bare.substring(TokenSpec.SPACE_FAMILY.length());
            if (isNumber(rest)) {
                String baseVar = forcedDark ? "--pl-dark-space-base" : "--pl-space-base";
                return "calc(var(" + baseVar + ") * " + rest + ")";
            }
        }
        if (forcedDark) {
            return "var(" + TokenSpec.FORCED_DARK_VAR_PREFIX + Format.cssVarName(bare) + ", unset)";
        }
        return "var(" + tokenToCssVar(bare) + ", unset)";
    }

    private static boolean isNumber(String value) {
        if (value.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
