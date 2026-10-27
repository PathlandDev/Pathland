package dev.pathland.view;

/**
 * A length value (spacing, padding, font size, radius, size) — a literal
 * {@code dp} value or a **design-token reference** (spec/TOKENS.md). Literal
 * lengths emit the property's numeric value type; token lengths emit
 * {@code DESIGN_TOKEN}.
 *
 * <pre>{@code
 * Padding.with(Length.token("space.2"));
 * FontSize.with(Length.token("font.body.size"));
 * }</pre>
 */
public record Length(float value, String token) implements TokenPath {

    /** A literal length in device pixels/points. */
    public static Length of(float value) {
        return new Length(value, null);
    }

    /** A design-token reference (e.g. {@code space.2}, {@code font.body.size}). */
    public static Length token(String path) {
        return new Length(0f, path);
    }

    /** Whether this is a token reference rather than a literal. */
    public boolean isToken() {
        return token != null;
    }

    @Override
    public String tokenPath() {
        return token;
    }
}
