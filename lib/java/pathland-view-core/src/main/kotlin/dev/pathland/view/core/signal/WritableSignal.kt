package dev.pathland.view.core.signal

import java.util.function.UnaryOperator

/**
 * A writable signal (Angular-style). Setting a new value that differs propagates
 * to dependent computed signals and effects, which flush synchronously at the end
 * of the write.
 */
interface WritableSignal<T> : Signal<T> {
    /** Replace the current value (no-op when equal). */
    fun set(value: T)

    /** Derive a new value from the current one and [set] it. */
    fun update(fn: UnaryOperator<T>)

    /** A read-only view of this signal. */
    fun asReadonly(): Signal<T>
}
