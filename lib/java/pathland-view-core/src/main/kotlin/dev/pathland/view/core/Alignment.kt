package dev.pathland.view.core

import dev.pathland.core.protocol.WireValue

/** The Alignment wire enum. */
enum class Alignment(private val wire: Int) : WireValue {
    TOP_LEADING(0),
    CENTER(1),
    BOTTOM_TRAILING(2),
    TOP_CENTER(3),
    BOTTOM_CENTER(4),
    CENTER_LEADING(5),
    CENTER_TRAILING(6),
    TOP_TRAILING(7),
    BOTTOM_LEADING(8);

    override fun wire(): Int = wire
}
