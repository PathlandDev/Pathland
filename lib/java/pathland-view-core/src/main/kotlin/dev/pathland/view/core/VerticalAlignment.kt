package dev.pathland.view.core

import dev.pathland.core.protocol.WireValue

/** The VerticalAlignment wire enum. */
enum class VerticalAlignment(private val wire: Int) : WireValue {
    TOP(0),
    CENTER(1),
    BOTTOM(2);

    override fun wire(): Int = wire
}
