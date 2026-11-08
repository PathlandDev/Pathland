package dev.pathland.view.core

import dev.pathland.core.protocol.Components
import dev.pathland.view.engine.PathlandNode
import java.util.function.Supplier

/** The component backing `View.of(Supplier)`: a Group-backed slot. */
internal class ReactiveSlotView(private val content: Supplier<View>) : View {
    override fun renderNode(env: Environment): PathlandNode {
        val node = PathlandNode(Components.VSTACK)
        val selected = content.get()
        if (selected != null) {
            node.children.add(selected.render(env))
        }
        return node
    }
}
