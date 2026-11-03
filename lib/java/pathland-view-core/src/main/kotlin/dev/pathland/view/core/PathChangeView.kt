package dev.pathland.view.core

import dev.pathland.view.engine.PathlandNode
import java.util.function.Consumer

/** A view wrapped by `onPathChange`: records the listener on the built node. */
internal class PathChangeView(
    private val content: View,
    private val listener: Consumer<String>?,
) : View {
    override fun renderNode(env: Environment): PathlandNode {
        val node = content.renderNode(env)
        if (listener != null) node.pathChangeListeners.add(listener)
        node.activePath = Environment.value(Platform.ACTIVE_PATH)
        return node
    }
}
