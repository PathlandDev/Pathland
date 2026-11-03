package dev.pathland.view.core.signal

/**
 * A reactive value (Angular-style). Reading a signal via [get] records a
 * dependency on the enclosing reactive context.
 *
 * Signals are single-threaded by contract: reads and writes happen on the
 * application's actor thread.
 */
fun interface Signal<T> {
    /** Read the current value, registering a dependency on the current context. */
    fun get(): T
}
