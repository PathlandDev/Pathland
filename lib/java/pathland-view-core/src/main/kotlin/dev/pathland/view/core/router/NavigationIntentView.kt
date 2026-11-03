package dev.pathland.view.core.router

import dev.pathland.view.core.Environment
import dev.pathland.view.core.View
import dev.pathland.view.engine.PathlandNode

/** A view wrapped by a navigation intent; records it on the built node. */
internal class NavigationIntentView(
    private val content: View,
    private val to: String,
    private val op: NavOp,
) : View {
    override fun renderNode(env: Environment): PathlandNode {
        val node = content.renderNode(env)
        node.navigateTo = to
        node.navOp = op
        return node
    }
}
