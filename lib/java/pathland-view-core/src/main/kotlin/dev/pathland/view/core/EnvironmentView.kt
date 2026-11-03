package dev.pathland.view.core

import dev.pathland.view.engine.PathlandNode

/** A view scoped to an environment value. */
internal class EnvironmentView private constructor(
    private val content: View,
    private val key: EnvironmentKey<Any?>,
    private val value: Any?,
) : View {
    override fun renderNode(env: Environment): PathlandNode {
        val scope = Environment.current().with(key, value)
        val previous = Environment.current()
        Environment.within(scope)
        try {
            return content.renderNode(env)
        } finally {
            Environment.restore(previous)
        }
    }

    companion object {
        /** A scope wrapper for a typed key → value binding. */
        @Suppress("UNCHECKED_CAST")
        fun <T> of(content: View, key: EnvironmentKey<T>, value: T): EnvironmentView =
            EnvironmentView(content, key as EnvironmentKey<Any?>, value)
    }
}
