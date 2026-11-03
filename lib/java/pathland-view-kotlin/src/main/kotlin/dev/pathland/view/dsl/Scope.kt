package dev.pathland.view.dsl

import dev.pathland.view.core.Modified
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
        // A DSL leaf/container registers its base view as soon as it is constructed.
        // A following modifier chain then registers the wrapper; replace the base in
        // place so `.padding(16)` on a child is not silently dropped.
        val inner = (view as? Modified)?.inner()
        if (inner != null) {
            val index = scope.indexOfFirst { it === inner }
            if (index >= 0) {
                scope[index] = view
                return
            }
        }
        if (scope.none { it === view }) {
            scope.add(view)
        }
    }

    fun unregister(view: View) {
        current()?.removeAll { it === view }
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
        0 -> groupView(emptyList())
        1 -> children[0]
        else -> groupView(children)
    }
}
