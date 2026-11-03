package dev.pathland.view.core

import dev.pathland.view.core.protocol.Components
import dev.pathland.view.core.protocol.Properties
import dev.pathland.view.core.protocol.TokenPath
import dev.pathland.view.engine.PathlandNode

/** An sRGB color (0xAARRGGBB) or a design-token reference. Also a solid-color View. */
data class Color(val argb: Int, val token: String?) : View, TokenPath {
    fun argb(): Int = argb
    fun token(): String? = token
    fun isToken(): Boolean = token != null

    override fun tokenPath(): String? = token

    override fun renderNode(env: Environment): PathlandNode {
        val node = PathlandNode(Components.COLOR)
        node.properties[Properties.COLOR] = this
        return node
    }

    companion object {
        @JvmField val CLEAR = Color(0x00000000, null)
        @JvmField val BLACK = Color(0xFF000000.toInt(), null)
        @JvmField val WHITE = Color(0xFFFFFFFF.toInt(), null)
        @JvmField val RED = Color(0xFFFF0000.toInt(), null)
        @JvmField val BLUE = Color(0xFF2196F3.toInt(), null)

        /** A fully opaque sRGB color. */
        @JvmStatic
        fun rgb(r: Int, g: Int, b: Int): Color =
            Color(0xFF000000.toInt() or (r shl 16) or (g shl 8) or b, null)

        /** A color from its packed 0xAARRGGBB value. */
        @JvmStatic
        fun of(argb: Int): Color = Color(argb, null)

        /** An sRGB color with explicit alpha (0..255). */
        @JvmStatic
        fun argb(a: Int, r: Int, g: Int, b: Int): Color =
            Color((a shl 24) or (r shl 16) or (g shl 8) or b, null)

        /** A reference to a design token path. */
        @JvmStatic
        fun token(path: String): Color = Color(0, path)
    }
}
