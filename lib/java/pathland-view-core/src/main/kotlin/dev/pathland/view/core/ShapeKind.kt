package dev.pathland.view.core

import dev.pathland.core.protocol.WireValue

/**
 * The shape used to clip a view, carried by the `SHAPE_KIND` property.
 *
 * Values are circle=0, rectangle=1, roundedRectangle=2, capsule=3, ellipse=4,
 * and path=5. `path` has no wire path data and renders as the renderer's
 * placeholder; pair a shape with `CLIPS_TO_BOUNDS` via `ClipShape`.
 */
enum class ShapeKind(private val wire: Int) : WireValue {
    CIRCLE(0),
    RECTANGLE(1),
    ROUNDED_RECTANGLE(2),
    CAPSULE(3),
    ELLIPSE(4),
    PATH(5);

    override fun wire(): Int = wire
}
