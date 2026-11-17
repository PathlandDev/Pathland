package dev.pathland.view.core

import dev.pathland.core.protocol.WireValue

/**
 * A `SCROLLVIEW`'s scroll direction (`SCROLL_AXIS`): vertical=0 (the default)
 * or horizontal=1. The view is greedy on this axis and hugs its content on the
 * cross axis.
 */
enum class ScrollAxis(private val wire: Int) : WireValue {
    VERTICAL(0),
    HORIZONTAL(1);

    override fun wire(): Int = wire
}
