package dev.pathland.view.core

import dev.pathland.view.core.protocol.Properties
import dev.pathland.view.core.protocol.WireValue

/** The FontStyle wire enum; also a view modifier. */
enum class FontStyle(private val wire: Int) : ViewModifier, WireValue {
    NORMAL(0),
    ITALIC(1);

    override fun wire(): Int = wire

    override fun body(content: View): View =
        Modified.props(content, Modified.prop(Properties.FONT_STYLE, wire.toFloat()))
}
