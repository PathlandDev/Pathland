package dev.pathland.view.core

import dev.pathland.core.protocol.WireValue

/**
 * How media fills its allocated box (`CONTENT_MODE`): fit=0 scales the content
 * inside the box while preserving aspect ratio; fill=1 covers the box and may
 * crop. Used by `IMAGE` and `VIDEO`.
 */
enum class ContentMode(private val wire: Int) : WireValue {
    FIT(0),
    FILL(1);

    override fun wire(): Int = wire
}
