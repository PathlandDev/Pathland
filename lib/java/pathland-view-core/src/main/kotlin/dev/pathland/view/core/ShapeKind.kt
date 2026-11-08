package dev.pathland.view.core

import dev.pathland.core.protocol.WireValue

/** The ShapeKind wire enum. */
enum class ShapeKind(private val wire: Int) : WireValue {
    CIRCLE(0),
    RECTANGLE(1),
    ROUNDED_RECTANGLE(2),
    CAPSULE(3),
    ELLIPSE(4),
    PATH(5);

    override fun wire(): Int = wire
}
