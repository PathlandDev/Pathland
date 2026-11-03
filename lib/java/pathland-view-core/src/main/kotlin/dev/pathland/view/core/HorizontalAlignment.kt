package dev.pathland.view.core

import dev.pathland.view.core.protocol.WireValue

/** The HorizontalAlignment wire enum. */
enum class HorizontalAlignment(private val wire: Int) : WireValue {
    LEADING(0),
    CENTER(1),
    TRAILING(2);

    override fun wire(): Int = wire
}
