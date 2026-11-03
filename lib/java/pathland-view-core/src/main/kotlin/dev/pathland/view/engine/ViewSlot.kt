package dev.pathland.view.engine

import dev.pathland.view.core.Environment
import dev.pathland.view.core.EnvironmentValues
import dev.pathland.view.core.View
import dev.pathland.view.core.signal.EffectRef
import dev.pathland.view.core.signal.Tracked
import java.util.ArrayList

/** One component invocation in the emitter's render tree. */
internal class ViewSlot(
    @JvmField val view: View,
    @JvmField val env: Environment,
    @JvmField val envScope: EnvironmentValues,
    @JvmField val parent: ViewSlot?,
) {
    @JvmField val children: MutableList<ViewSlot> = ArrayList()
    @JvmField var renderingChildren: MutableList<ViewSlot>? = null
    @JvmField var node: PathlandNode? = null
    @JvmField var nodeParent: PathlandNode? = null
    @JvmField var nodeIndex: Int = -1
    @JvmField var tracked: Tracked<PathlandNode>? = null
    @JvmField val bindings: MutableList<EffectRef> = ArrayList()
    @JvmField var oldNodeHint: PathlandNode? = null

    fun beginRenderChildren() {
        renderingChildren = ArrayList()
    }

    fun addRenderingChild(child: ViewSlot) {
        val rendering = renderingChildren
        if (rendering != null) rendering.add(child) else children.add(child)
    }

    fun finishFirstRender() {
        children.clear()
        renderingChildren?.let {
            children.addAll(it)
            renderingChildren = null
        }
    }

    fun takeRenderedChildren(): List<ViewSlot> {
        val result = renderingChildren ?: emptyList()
        renderingChildren = null
        return result
    }
}
