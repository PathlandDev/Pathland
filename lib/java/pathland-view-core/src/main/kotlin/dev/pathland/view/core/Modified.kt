package dev.pathland.view.core

import dev.pathland.view.core.signal.Signal
import dev.pathland.view.engine.PathlandNode
import dev.pathland.view.engine.ReactiveValues

/** A view wrapped by property modifiers or a custom ViewModifier. */
class Modified private constructor(
    private val inner: View,
    private val props: List<Prop>,
    private val modifier: ViewModifier?,
) : View {

    /** One property application: a static value or a reactive signal. */
    data class Prop(
        val property: Int,
        val value: Any?,
        val signal: Signal<*>?,
    ) {
        fun property(): Int = property
        fun value(): Any? = value
        fun signal(): Signal<*>? = signal

        companion object {
            fun value(property: Int, value: Any?): Prop = Prop(property, value, null)

            fun reactive(property: Int, signal: Signal<*>): Prop = Prop(property, null, signal)
        }
    }

    /** The wrapped view (the next link inward in this modifier chain). */
    fun inner(): View = inner

    override fun renderNode(env: Environment): PathlandNode {
        if (modifier != null) {
            return modifier.body(inner).renderNode(env)
        }
        val node = inner.renderNode(env)
        for (p in props) {
            if (p.signal != null) {
                val value = ReactiveValues.initial(p.property, p.signal)
                node.properties[p.property] = value
                node.propertyBindings[p.property] = p.signal
            } else {
                node.properties[p.property] = p.value
            }
        }
        return node
    }

    companion object {
        /** A static property mod. */
        fun prop(property: Int, value: Any?): Prop = Prop.value(property, value)

        /** A reactive property mod. */
        fun prop(property: Int, signal: Signal<*>): Prop = Prop.reactive(property, signal)

        /** Build a property-modifier view from inner + props. */
        fun props(inner: View, vararg props: Prop): Modified = Modified(inner, props.toList(), null)

        /** A custom-view-modifier wrapper. */
        fun apply(inner: View, modifier: ViewModifier): Modified = Modified(inner, emptyList(), modifier)
    }
}
