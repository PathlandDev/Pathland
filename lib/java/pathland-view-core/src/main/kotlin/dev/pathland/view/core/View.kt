package dev.pathland.view.core

import dev.pathland.view.engine.Emitter
import dev.pathland.view.engine.PathlandNode
import java.util.Objects
import java.util.function.Supplier

/**
 * A composable view. Composite views declare their subtree with [body]; primitives
 * override [renderNode] to materialize a [PathlandNode].
 */
interface View {
    /** The base marker for a view's value config. */
    interface Config

    /** Declarative composition: a composite view returns its subtree here. */
    fun body(): View = this

    /** The emitter entry point: render this view as a tracked component slot. */
    fun render(env: Environment): PathlandNode {
        val emitter = Emitter.current()
        return if (emitter == null) renderNode(env) else emitter.renderView(this, env)
    }

    /** The root-chain render. */
    fun renderNode(env: Environment): PathlandNode {
        val resolved = body()
        if (resolved !== this) {
            return resolved.renderNode(env)
        }
        throw UnsupportedOperationException(
            javaClass.name + " must override body() (composite) or renderNode() (primitive)",
        )
    }

    /** Wrap this view in modifiers, applied innermost-first. */
    fun modifiers(vararg modifiers: ViewModifier): View {
        var result: View = this
        for (modifier in modifiers) {
            result = Modified.apply(result, modifier)
        }
        return result
    }

    companion object {
        /** A reactive subtree from a supplier: the inline form of control flow. */
        @JvmStatic
        fun of(content: Supplier<View>): View =
            ReactiveSlotView(Objects.requireNonNull(content, "content"))
    }
}
