package dev.pathland.view.core.signal

import java.util.ArrayDeque
import java.util.Objects
import java.util.function.BiPredicate
import java.util.function.Consumer
import java.util.function.Supplier

/** Factory for Angular-style reactive primitives. */
object Signals {

    /** A writable signal with `Objects.equals` equality. */
    @JvmStatic
    fun <T> signal(initial: T): WritableSignal<T> =
        WritableSignalImpl(initial, Objects::equals, null)

    /** A writable signal with a custom equality comparator. */
    @JvmStatic
    fun <T> signal(initial: T, equal: BiPredicate<T, T>): WritableSignal<T> =
        WritableSignalImpl(initial, Objects.requireNonNull(equal, "equal"), null)

    /** A named writable signal. */
    @JvmStatic
    fun <T> signal(name: String, initial: T): WritableSignal<T> =
        WritableSignalImpl(initial, Objects::equals, name)

    /** An immutable signal carrying a static value. */
    @JvmStatic
    fun <T> constant(value: T): Signal<T> = ConstantSignal(value)

    /** A lazy, memoized derived signal. */
    @JvmStatic
    fun <T> computed(fn: Supplier<T>): Signal<T> =
        ComputedSignal(Objects.requireNonNull(fn, "computed body"), Objects::equals)

    /** A derived signal with a custom equality comparator. */
    @JvmStatic
    fun <T> computed(fn: Supplier<T>, equal: BiPredicate<T, T>): Signal<T> =
        ComputedSignal(
            Objects.requireNonNull(fn, "computed body"),
            Objects.requireNonNull(equal, "equal"),
        )

    /** Register an effect; runs immediately, then on tracked dependency changes. */
    @JvmStatic
    fun effect(fn: Runnable): EffectRef = effect(fn, EffectOptions.DEFAULT)

    /** Register an effect with options. */
    @JvmStatic
    fun effect(fn: Runnable, options: EffectOptions): EffectRef =
        Effect(Objects.requireNonNull(fn, "effect body"), Objects.requireNonNull(options, "options"), null)

    /** A named effect. */
    @JvmStatic
    fun effect(name: String, fn: Runnable): EffectRef =
        Effect(Objects.requireNonNull(fn, "effect body"), EffectOptions.DEFAULT, name)

    /** A synchronous tracked computation (emitter-internal). */
    @JvmStatic
    fun <T> tracked(fn: Supplier<T>, onRecompute: Consumer<T>): Tracked<T> {
        Objects.requireNonNull(fn, "tracked body")
        Objects.requireNonNull(onRecompute, "tracked recompute")
        val computed = ComputedSignal(fn, Objects::equals)
        val initial = untracked(Supplier { computed.get() })
        var first = true
        val effect = effect(Runnable {
            val value = computed.get()
            if (first) {
                first = false
                if (!Objects.equals(value, initial)) {
                    onRecompute.accept(value)
                }
                return@Runnable
            }
            onRecompute.accept(value)
        })
        return Tracked(initial, effect)
    }

    /**
     * Read signals without recording dependencies on the enclosing context.
     */
    @JvmStatic
    fun <T> untracked(fn: Supplier<T>): T {
        val stack = ReactiveContext.stack()
        val saved = ArrayDeque(stack)
        stack.clear()
        try {
            return Objects.requireNonNull(fn, "untracked body").get()
        } finally {
            stack.addAll(saved)
        }
    }
}
