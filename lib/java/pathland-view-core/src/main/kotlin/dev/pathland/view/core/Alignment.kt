package dev.pathland.view.core

import dev.pathland.core.protocol.WireValue

/**
 * A 2D content alignment, encoded as the protocol's 0–8 alignment code.
 *
 * Used by `ZStack`, grids, and a `frame`'s content alignment; a one-axis stack
 * reads only the matching component (`HorizontalAlignment`/`VerticalAlignment`).
 * The codes are topLeading=0, center=1, bottomTrailing=2, topCenter=3,
 * bottomCenter=4, centerLeading=5, centerTrailing=6, topTrailing=7,
 * bottomLeading=8.
 */
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
