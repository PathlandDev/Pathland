package dev.pathland.view.core

import dev.pathland.core.protocol.Properties
import dev.pathland.core.protocol.WireValue

/**
 * A control density hint (`CONTROL_SIZE`): small=0, regular=1, large=2. The
 * renderer maps it to native padding/font metrics; also a modifier.
 */
enum class ControlSize(private val wire: Int) : ViewModifier, WireValue {
    SMALL(0),
    REGULAR(1),
    LARGE(2);

    override fun wire(): Int = wire

    override fun body(content: View): View =
        Modified.props(content, Modified.prop(Properties.CONTROL_SIZE, wire.toFloat()))
}
