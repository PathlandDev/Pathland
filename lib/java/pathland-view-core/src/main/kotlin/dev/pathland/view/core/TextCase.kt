package dev.pathland.view.core

import dev.pathland.core.protocol.Properties
import dev.pathland.core.protocol.WireValue

/**
 * Applies a text-case transform to the wrapped subtree (`TEXT_CASE`):
 * none=0, uppercase=1, lowercase=2.
 *
 * The enum is also a `ViewModifier`, so it can be chained directly
 * (`.textCase(TextCase.UPPERCASE)`).
 */
enum class TextCase(private val wire: Int) : ViewModifier, WireValue {
    NONE(0),
    UPPERCASE(1),
    LOWERCASE(2);

    override fun wire(): Int = wire

    override fun body(content: View): View =
        Modified.props(content, Modified.prop(Properties.TEXT_CASE, wire.toFloat()))
}
