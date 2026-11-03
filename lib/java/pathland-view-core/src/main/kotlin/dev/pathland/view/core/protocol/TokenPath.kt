package dev.pathland.view.core.protocol

/**
 * A value carrying a design-token path instead of a literal (spec/TOKENS.md).
 * The emitter sends such a value as the `DESIGN_TOKEN` value type.
 */
interface TokenPath {
    /** The token path (e.g. `color.primary`, `space.2`). */
    fun tokenPath(): String?
}
