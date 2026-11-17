package dev.pathland.view.core

import dev.pathland.core.protocol.Properties
import dev.pathland.core.protocol.WireValue

/**
 * A numeric font weight (`FONT_WEIGHT`): light=300, regular=400, medium=500,
 * semibold=600, bold=700. The value is emitted as `F32`; also a modifier.
 */
enum class FontWeight(private val wire: Int) : ViewModifier, WireValue {
    LIGHT(300),
    REGULAR(400),
    MEDIUM(500),
    SEMIBOLD(600),
    BOLD(700);

    override fun wire(): Int = wire

    override fun body(content: View): View =
        Modified.props(content, Modified.prop(Properties.FONT_WEIGHT, wire.toFloat()))
}
