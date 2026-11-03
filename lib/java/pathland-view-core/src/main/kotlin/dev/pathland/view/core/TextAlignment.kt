package dev.pathland.view.core

import dev.pathland.view.core.protocol.Properties
import dev.pathland.view.core.protocol.WireValue

/** The TextAlignment wire enum; also a view modifier. */
enum class TextAlignment(private val wire: Int) : ViewModifier, WireValue {
    LEADING(0),
    CENTER(1),
    TRAILING(2);

    override fun wire(): Int = wire

    override fun body(content: View): View =
        Modified.props(content, Modified.prop(Properties.TEXT_ALIGNMENT, wire.toFloat()))
}
