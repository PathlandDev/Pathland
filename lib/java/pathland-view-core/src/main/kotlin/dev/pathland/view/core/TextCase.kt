package dev.pathland.view.core

import dev.pathland.view.core.protocol.Properties
import dev.pathland.view.core.protocol.WireValue

/** The TextCase wire enum; also a view modifier. */
enum class TextCase(private val wire: Int) : ViewModifier, WireValue {
    NONE(0),
    UPPERCASE(1),
    LOWERCASE(2);

    override fun wire(): Int = wire

    override fun body(content: View): View =
        Modified.props(content, Modified.prop(Properties.TEXT_CASE, wire.toFloat()))
}
