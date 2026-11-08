package dev.pathland.view.core

import dev.pathland.core.protocol.Components
import dev.pathland.view.engine.PathlandNode

/** A sentinel style content meaning "no content". */
enum class EmptyContent : View {
    INSTANCE;

    override fun renderNode(env: Environment): PathlandNode =
        PathlandNode(Components.VSTACK)
}
