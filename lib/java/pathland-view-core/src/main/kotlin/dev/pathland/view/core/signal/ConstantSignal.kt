package dev.pathland.view.core.signal

/** An immutable signal whose value never changes; reads record no dependency. */
class ConstantSignal<T>(private val value: T) : Signal<T> {
    override fun get(): T = value
}
