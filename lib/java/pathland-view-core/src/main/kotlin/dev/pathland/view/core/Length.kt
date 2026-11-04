package dev.pathland.view.core

import dev.pathland.view.core.protocol.TokenPath

/** A length value: a literal dp value or a design-token reference. */
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
