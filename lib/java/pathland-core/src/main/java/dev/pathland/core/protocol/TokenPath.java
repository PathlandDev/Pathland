package dev.pathland.core.protocol;

/**
 * A value carrying a design-token path instead of a literal (spec/TOKENS.md).
 * The emitter sends such a value as the {@code DESIGN_TOKEN} value type.
 */
public interface TokenPath {
    /** The token path (e.g. {@code color.primary}, {@code space.2}). */
    String tokenPath();
}
