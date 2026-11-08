package dev.pathland.view.core

import dev.pathland.core.protocol.Properties
import dev.pathland.core.protocol.WireValue

/** The ControlSize wire enum; also a view modifier. */
enum class ControlSize(private val wire: Int) : ViewModifier, WireValue {
    SMALL(0),
    REGULAR(1),
    LARGE(2);

    override fun wire(): Int = wire

    override fun body(content: View): View =
        Modified.props(content, Modified.prop(Properties.CONTROL_SIZE, wire.toFloat()))
}
