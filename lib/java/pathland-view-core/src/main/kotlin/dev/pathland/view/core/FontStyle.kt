package dev.pathland.view.core

import dev.pathland.core.protocol.Properties
import dev.pathland.core.protocol.WireValue

/**
 * Selects normal=0 or italic=1 text (`FONT_STYLE`) for the wrapped subtree.
 * Also a `ViewModifier` for fluent use.
 */
enum class FontStyle(private val wire: Int) : ViewModifier, WireValue {
    NORMAL(0),
    ITALIC(1);

    override fun wire(): Int = wire

    override fun body(content: View): View =
        Modified.props(content, Modified.prop(Properties.FONT_STYLE, wire.toFloat()))
}
