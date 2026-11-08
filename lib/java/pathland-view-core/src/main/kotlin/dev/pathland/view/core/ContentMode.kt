package dev.pathland.view.core

import dev.pathland.core.protocol.WireValue

/** The ContentMode wire enum. */
enum class ContentMode(private val wire: Int) : WireValue {
    FIT(0),
    FILL(1);

    override fun wire(): Int = wire
}
