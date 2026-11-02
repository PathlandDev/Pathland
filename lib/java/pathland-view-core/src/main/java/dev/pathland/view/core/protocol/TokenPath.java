package dev.pathland.view.core.protocol;

import dev.pathland.view.core.Color;
import dev.pathland.view.core.FontFamily;
import dev.pathland.view.core.Length;

/**
 * A value carrying a **design-token path** instead of a literal (spec/TOKENS.md).
 * The emitter sends such a value as the {@code DESIGN_TOKEN} value type, and the
 * renderer resolves it against the active theme/scheme.
 *
 * <p>Implementations are data values, not modifiers: {@link Color},
 * {@link Length}, and {@link FontFamily.Token}.
 */
public interface TokenPath {

    /** The token path (e.g. {@code color.primary}, {@code space.2}). */
    String tokenPath();
}
