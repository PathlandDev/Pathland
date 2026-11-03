package dev.pathland.view.core

import dev.pathland.view.core.protocol.Properties
import dev.pathland.view.engine.PathlandNode

/** A view wrapped by `pointerEvents(mask)`: ORs the listener bits into EVENT_LISTENERS. */
internal class PointerEventsView(private val content: View, private val mask: Int) : View {
    override fun renderNode(env: Environment): PathlandNode {
        val node = content.renderNode(env)
        val existing = node.properties[Properties.EVENT_LISTENERS]
        val current = if (existing is Int) existing else 0
        node.properties[Properties.EVENT_LISTENERS] = current or mask
        return node
    }
}
