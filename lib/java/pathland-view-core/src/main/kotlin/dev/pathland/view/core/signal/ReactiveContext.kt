package dev.pathland.view.core.signal

import java.util.ArrayDeque

/** The reactive evaluation context: a thread-local stack of consumers. */
internal object ReactiveContext {
    private val STACK: ThreadLocal<ArrayDeque<ReactiveNode>> =
        ThreadLocal.withInitial { ArrayDeque() }

    /** The consumer currently evaluating, or null outside any reactive context. */
    fun current(): ReactiveNode? = STACK.get().peek()

    /** The underlying stack (used by `Signals.untracked`). */
    fun stack(): ArrayDeque<ReactiveNode> = STACK.get()

    /** Whether [node] is currently being evaluated (reentrancy guard). */
    fun contains(node: ReactiveNode): Boolean = STACK.get().contains(node)

    fun push(node: ReactiveNode) {
        val stack = STACK.get()
        if (stack.contains(node)) {
            throw IllegalStateException(
                "Circular signal dependency detected while evaluating '${node.describe()}'",
            )
        }
        stack.push(node)
    }

    fun pop() {
        STACK.get().pop()
    }
}
