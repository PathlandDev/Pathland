package dev.pathland.view.core.signal

import java.util.function.BiPredicate
import java.util.function.Supplier

/** A lazy, memoized derived signal (Angular-style `computed`). */
internal class ComputedSignal<T>(
    private val fn: Supplier<T>,
    private val equal: BiPredicate<T, T>,
) : ReactiveNode(null), Signal<T> {

    private var cachedValue: T? = null

    override fun get(): T {
        if (ReactiveContext.contains(this)) {
            throw IllegalStateException(
                "Circular signal dependency: computed '${describe()}' depends on itself",
            )
        }
        evaluateIfDirty()
        if (error != null) {
            rethrowError()
        }
        recordDependency()
        @Suppress("UNCHECKED_CAST")
        return cachedValue as T
    }

    fun evaluateIfDirty() {
        if (!dirty) return
        refreshProducers()
        dirty = false
        if (!firstRun && !depsChanged()) return
        firstRun = false
        ReactiveContext.push(this)
        resetProducers()
        try {
            val next = fn.get()
            if (equal.test(cachedValue as T, next)) {
                error = null
            } else {
                cachedValue = next
                error = null
                version++
                propagate()
            }
        } catch (t: Throwable) {
            if (error != t) {
                error = t
                version++
                propagate()
            }
        } finally {
            ReactiveContext.pop()
        }
    }
}
