package dev.pathland.view.core

import dev.pathland.core.protocol.Properties
import dev.pathland.core.protocol.WireValue

/**
 * Multiline text alignment (`TEXT_ALIGNMENT`): leading=0, center=1, trailing=2.
 * Also a `ViewModifier` for fluent use on a text-bearing subtree.
 */
enum class TextAlignment(private val wire: Int) : ViewModifier, WireValue {
    LEADING(0),
    CENTER(1),
    TRAILING(2);

    override fun wire(): Int = wire

    override fun body(content: View): View =
        Modified.props(content, Modified.prop(Properties.TEXT_ALIGNMENT, wire.toFloat()))
}
