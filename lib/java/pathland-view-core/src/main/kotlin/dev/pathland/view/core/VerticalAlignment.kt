package dev.pathland.view.core

import dev.pathland.core.protocol.WireValue

/**
 * The cross-axis alignment of an `HSTACK`: top=0, center=1, bottom=2. It is
 * position-only; stretching a child is its own `FILL` size kind.
 */
enum class VerticalAlignment(private val wire: Int) : WireValue {
    TOP(0),
    CENTER(1),
    BOTTOM(2);

    override fun wire(): Int = wire
}
