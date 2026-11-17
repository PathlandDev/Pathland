package dev.pathland.view.core.signal

import java.util.function.BiPredicate
import java.util.function.UnaryOperator

/**
 * A writable signal backed by a reactive graph node.
 *
 * Writes are equality-suppressed and propagate dirty/version information to
 * consumers; reads record a dependency when inside a tracked render/effect.
 */
internal class WritableSignalImpl<T>(
    private var value: T,
    private val equal: BiPredicate<T, T>,
    name: String?,
) : ReactiveNode(name), WritableSignal<T> {

    override fun get(): T {
        recordDependency()
        return value
    }

    override fun set(value: T) {
        if (equal.test(this.value, value)) return
        checkWriteAllowed()
        this.value = value
        version++
        propagate()
    }

    override fun update(fn: UnaryOperator<T>) {
        set(fn.apply(value))
    }

    override fun asReadonly(): Signal<T> = Signal { get() }
}
