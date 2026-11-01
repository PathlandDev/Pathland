package dev.pathland.view.dsl

import dev.pathland.view.core.View
import java.util.ArrayDeque

/**
 * Marks the Kotlin DSL entry points. The builder is synchronous and single-threaded
 * (the same contract as the emitter and the Java environment), so a thread-local
 * child stack is the equivalent of SwiftUI's result builder.
 */
@DslMarker
annotation class PathlandDsl

internal object ViewScopes {
    private val stack = ThreadLocal.withInitial { ArrayDeque<MutableList<View>>() }

    fun push(): MutableList<View> {
        val scope = mutableListOf<View>()
        stack.get().addLast(scope)
        return scope
    }

    fun pop() {
        stack.get().removeLast()
    }

    fun current(): MutableList<View>? = stack.get().peekLast()

    fun register(view: View) {
        val scope = current() ?: return
        if (scope.none { it === view }) {
            scope.add(view)
        }
    }
}

/**
 * Add a view built outside the DSL builders (e.g. a Java `View` or
 * `mediaController.attach()`) to the current block.
 */
fun View.add(): View = registerChild()

/** Prefix form: `+mediaController.attach()`. */
operator fun View.unaryPlus(): View = registerChild()

/**
 * Collect the views declared by [content]. A newly declared child auto-registers
 * into the innermost active scope, so `VStack { Text("a"); Text("b") }` needs no
 * `.children(...)` boilerplate.
 */
fun collectChildren(content: () -> Unit): List<View> {
    val scope = ViewScopes.push()
    return try {
        content()
        scope.toList()
    } finally {
        ViewScopes.pop()
    }
}

internal fun <T : View> T.registerChild(): T {
    ViewScopes.register(this)
    return this
}

/** Collect a ForEach row into one child view (a single child stays unwrapped). */
internal fun rowView(content: () -> Unit): View {
    val children = collectChildren(content)
    return when (children.size) {
        0 -> dev.pathland.view.Group.children()
        1 -> children[0]
        else -> dev.pathland.view.Group.children(*children.toTypedArray())
    }
}
