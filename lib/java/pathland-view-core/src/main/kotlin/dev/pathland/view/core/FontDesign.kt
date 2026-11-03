package dev.pathland.view.core

import dev.pathland.view.core.protocol.Properties
import dev.pathland.view.core.protocol.WireValue

/** The FontDesign wire enum; also a view modifier. */
enum class FontDesign(private val wire: Int) : ViewModifier, WireValue {
    DEFAULT(0),
    SERIF(1),
    ROUNDED(2),
    MONOSPACED(3);

    override fun wire(): Int = wire

    override fun body(content: View): View =
        Modified.props(content, Modified.prop(Properties.FONT_DESIGN, wire.toFloat()))
}
