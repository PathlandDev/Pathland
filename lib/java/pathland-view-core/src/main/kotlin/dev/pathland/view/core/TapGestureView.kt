package dev.pathland.view.core

import dev.pathland.core.protocol.Commands
import dev.pathland.core.protocol.Properties
import dev.pathland.view.engine.PathlandNode

/** A view wrapped by `onTapGesture`: declares pointer listeners and records the action. */
internal class TapGestureView(private val content: View, private val action: Runnable?) : View {
    override fun renderNode(env: Environment): PathlandNode {
        val node = content.renderNode(env)
        val existing = node.properties[Properties.EVENT_LISTENERS]
        val mask = if (existing is Int) existing else 0
        node.properties[Properties.EVENT_LISTENERS] =
            mask or Commands.Listeners.POINTER_DOWN or Commands.Listeners.POINTER_UP
        if (action != null) node.tapActions.add(action)
        return node
    }
}
