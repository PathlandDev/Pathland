package dev.pathland.view.core

import dev.pathland.core.protocol.TokenPath

/**
 * A length value: a literal logical-point value or a design-token reference.
 *
 * Token lengths are emitted as `DESIGN_TOKEN` and resolved by the renderer;
 * they work anywhere a spacing/padding/font/frame length is accepted.
 */
data class Length(val value: Float, val token: String?) : TokenPath {
    fun value(): Float = value
    fun token(): String? = token
    fun isToken(): Boolean = token != null

    override fun tokenPath(): String? = token

    companion object {
        /** A literal length in device pixels/points. */
        fun of(value: Float): Length = Length(value, null)

        /** A design-token reference. */
        fun token(path: String): Length = Length(0f, path)
    }
}
