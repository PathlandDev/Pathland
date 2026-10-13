package dev.pathland.view.signal;

import java.util.ArrayDeque;

/**
 * The reactive evaluation context: a thread-local stack of consumers (a computed
 * being evaluated or an effect running). Signal reads register dependencies against
 * the top of this stack.
 *
 * <p>Single-threaded by contract (the app-actor model).
 */
final class ReactiveContext {

    private static final ThreadLocal<ArrayDeque<ReactiveNode>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private ReactiveContext() {}

    /** The consumer currently evaluating, or {@code null} outside any reactive context. */
    static ReactiveNode current() {
        return STACK.get().peek();
    }

    /** The underlying stack (used by {@code Signals#untracked}). */
    static ArrayDeque<ReactiveNode> stack() {
        return STACK.get();
    }

    /** Whether {@code node} is currently being evaluated (reentrancy guard). */
    static boolean contains(ReactiveNode node) {
        return STACK.get().contains(node);
    }

    static void push(ReactiveNode node) {
        ArrayDeque<ReactiveNode> stack = STACK.get();
        if (stack.contains(node)) {
            throw new IllegalStateException(
                    "Circular signal dependency detected while evaluating '" + node.describe() + "'");
        }
        stack.push(node);
    }

    static void pop() {
        STACK.get().pop();
    }
}