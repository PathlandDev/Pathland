package dev.pathland.view.core

import dev.pathland.core.protocol.Properties
import dev.pathland.core.protocol.WireValue

/**
 * Selects the renderer's font category (`FONT_DESIGN`): default=0, serif=1,
 * rounded=2, monospaced=3. Also a `ViewModifier`.
 */
enum class FontDesign(private val wire: Int) : ViewModifier, WireValue {
    DEFAULT(0),
    SERIF(1),
    ROUNDED(2),
    MONOSPACED(3);

    override fun wire(): Int = wire

    override fun body(content: View): View =
        Modified.props(content, Modified.prop(Properties.FONT_DESIGN, wire.toFloat()))
}
