package dev.pathland.view.core

import dev.pathland.view.engine.PathlandNode
import java.util.ArrayList

/** The chainable result of a view's static `make(...)` entry point. */
class ViewBuilder<V, C>(
    private val view: V,
) : View where V : View, V : Configurable<C>, C : View.Config {

    private val modifiers = ArrayList<ViewModifier>()
    private val children = ArrayList<View>()

    /** Apply modifiers (innermost-first). */
    override fun modifiers(vararg modifiers: ViewModifier): ViewBuilder<V, C> {
        this.modifiers.addAll(modifiers)
        return this
    }

    /** Supply the content (content-bearing views only). */
    fun children(vararg children: View): ViewBuilder<V, C> {
        this.children.addAll(children)
        return this
    }

    override fun renderNode(env: Environment): PathlandNode {
        if (children.isNotEmpty() && view is ChildrenView) {
            view.setChildren(children)
        }
        var result: View = view
        for (modifier in modifiers) {
            result = Modified.apply(result, modifier)
        }
        return result.renderNode(env)
    }
}
