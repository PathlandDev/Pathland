package dev.pathland.view.core.signal

import java.util.ArrayDeque
import java.util.Objects
import java.util.function.BiPredicate
import java.util.function.Consumer
import java.util.function.Supplier
import dev.pathland.view.core.state.PersistentState
import dev.pathland.view.core.subscription.GlobalSubject
import dev.pathland.view.core.subscription.SessionContext
import dev.pathland.view.core.subscription.Topic

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

    /**
     * A persisted writable signal for [name], backed by the session state active
     * during this component render. Outside a state context it falls back to an
     * in-memory signal.
     */
    fun <T : Any> state(name: String, initial: T): WritableSignal<T> =
        PersistentState.current()?.signal(name, initial) ?: signal(initial)

    /** An immutable signal carrying a static value. */
    @JvmStatic
    fun <T> constant(value: T): Signal<T> = ConstantSignal(value)

    /**
     * Subscribe to [topic] for [subject], returning the session's local signal.
     *
     * Idempotent per `(topic, subject)` within a session. The value is seeded with
     * [initial] and updated by [dev.pathland.server.PathlandSignalHub.publish] on the
     * session's actor. Resolution is deferred until the first read when called before
     * the session context is installed (e.g. a root view's field initializer).
     */
    @JvmStatic
    fun <T, S> topic(topic: Topic<T, S>, subject: S, initial: T): Signal<T> {
        val context = SessionContext.current()
        return context?.subscribe(topic, subject, initial)
            ?: DeferredTopicSignal(topic, subject, initial)
    }

    /**
     * Subscribe to an unscoped (global) topic. Every session that calls this
     * receives [dev.pathland.server.PathlandSignalHub.publish] values; sessions
     * that never subscribe are untouched.
     */
    @JvmStatic
    fun <T> topic(topic: Topic<T, GlobalSubject>, initial: T): Signal<T> =
        topic(topic, GlobalSubject, initial)

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

/**
 * A topic signal created before the session context was installed. It resolves
 * (and registers the subscription) on first read, then delegates normally.
 */
private class DeferredTopicSignal<T, S>(
    private val topic: Topic<T, S>,
    private val subject: S,
    private val initial: T,
) : Signal<T> {

    private var resolvedContext: SessionContext? = null
    private var resolved: Signal<T>? = null

    override fun get(): T {
        val context = SessionContext.current()
            ?: throw IllegalStateException(
                "topic '${topic.name}' must be read inside a Pathland session render",
            )
        if (resolved == null || resolvedContext !== context) {
            resolved = context.subscribe(topic, subject, initial)
            resolvedContext = context
        }
        return resolved!!.get()
    }
}
