package dev.pathland.view.core

import dev.pathland.core.protocol.Properties
import dev.pathland.core.protocol.WireValue

/**
 * Where an ellipsis is placed under a line limit (`TRUNCATION_MODE`):
 * head=0, middle=1, tail=2. CSS/native text may approximate head/middle with a
 * tail ellipsis; the renderer owns that fidelity.
 */
enum class Truncation(private val wire: Int) : ViewModifier, WireValue {
    HEAD(0),
    MIDDLE(1),
    TAIL(2);

    override fun wire(): Int = wire

    override fun body(content: View): View =
        Modified.props(content, Modified.prop(Properties.TRUNCATION_MODE, wire.toFloat()))
}
