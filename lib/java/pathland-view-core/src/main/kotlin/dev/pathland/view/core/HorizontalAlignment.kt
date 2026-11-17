package dev.pathland.view.core

import dev.pathland.core.protocol.WireValue

/**
 * The cross-axis alignment of a `VSTACK`: leading=0, center=1, trailing=2. It
 * is position-only; stretching a child is its own `FILL` size kind.
 */
enum class HorizontalAlignment(private val wire: Int) : WireValue {
    LEADING(0),
    CENTER(1),
    TRAILING(2);

    override fun wire(): Int = wire
}
