package dev.pathland.view.dsl

import dev.pathland.view.core.Environment
import dev.pathland.view.core.View
import dev.pathland.view.engine.PathlandNode

/**
 * A Kotlin-owned view over the shared engine's [PathlandNode]. This is the base
 * for the Kotlin DSL's primitives and containers; it is independent of any
 * authoring DSL.
 */
internal class CoreView(
    private val component: Int,
    private val children: List<View> = emptyList(),
    private val configure: (PathlandNode, Environment) -> Unit = { _, _ -> },
) : View {

    override fun renderNode(env: Environment): PathlandNode {
        val node = PathlandNode(component)
        configure(node, env)
        for (child in children) {
            node.children.add(child.render(env))
        }
        return node
    }
}

internal fun coreView(
    component: Int,
    children: List<View> = emptyList(),
    configure: (PathlandNode, Environment) -> Unit = { _, _ -> },
): View = CoreView(component, children, configure)

/** A Group is a bare VStack container with no spacing/alignment. */
internal fun groupView(children: List<View>): View =
    coreView(dev.pathland.view.core.protocol.Components.VSTACK, children)
