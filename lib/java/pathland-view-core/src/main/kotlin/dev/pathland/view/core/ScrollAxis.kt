package dev.pathland.view.core

import dev.pathland.core.protocol.WireValue

/** The ScrollAxis wire enum. */
enum class ScrollAxis(private val wire: Int) : WireValue {
    VERTICAL(0),
    HORIZONTAL(1);

    override fun wire(): Int = wire
}
