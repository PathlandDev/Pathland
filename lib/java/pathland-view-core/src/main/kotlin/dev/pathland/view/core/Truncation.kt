package dev.pathland.view.core

import dev.pathland.core.protocol.Properties
import dev.pathland.core.protocol.WireValue

/** The Truncation wire enum; also a view modifier. */
enum class Truncation(private val wire: Int) : ViewModifier, WireValue {
    HEAD(0),
    MIDDLE(1),
    TAIL(2);

    override fun wire(): Int = wire

    override fun body(content: View): View =
        Modified.props(content, Modified.prop(Properties.TRUNCATION_MODE, wire.toFloat()))
}
