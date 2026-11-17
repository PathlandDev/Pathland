package dev.pathland.view.core

import dev.pathland.core.protocol.Components
import dev.pathland.view.engine.PathlandNode

/**
 * A sentinel style result meaning "render no content".
 *
 * It differs from a style returning `null`, which asks the caller to keep its
 * default content. [INSTANCE] renders an empty `VSTACK` node so the slot still
 * has a stable component type.
 */
enum class EmptyContent : View {
    INSTANCE;

    override fun renderNode(env: Environment): PathlandNode =
        PathlandNode(Components.VSTACK)
}
