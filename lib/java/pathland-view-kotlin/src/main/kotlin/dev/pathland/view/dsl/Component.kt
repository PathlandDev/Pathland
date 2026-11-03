package dev.pathland.view.dsl

import dev.pathland.view.core.Environment
import dev.pathland.view.core.View
import dev.pathland.view.engine.PathlandNode
import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.Signals
import dev.pathland.view.core.signal.WritableSignal
import dev.pathland.view.core.state.PersistentState
import java.util.function.UnaryOperator
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Base class for application-authored Kotlin views.
 *
 * A subclass declares its subtree in [body]; a signal read there re-renders only this
 * component (the Java emitter tracks the slot). Persisted state is declared with
 * [state] / [mutableState] and is bound to the session's [PersistentState] before the
 * first body render — no annotation processor or kapt.
 *
 * Constructing a [Component] inside a builder block auto-registers it, so
 * `VStack { CounterView() }` needs no `.children(...)`.
 */
abstract class Component : View {

    private val stateSignals = mutableListOf<StateSignal<*>>()

    init {
        // Inline construction (`VStack { CounterView() }`) auto-registers. A view
        // constructed as a field of another view must not leak into the enclosing
        // builder scope; it is added explicitly with `+field` inside body().
        if (!insideAnotherViewConstructor()) {
            registerChild()
        }
    }

    /**
     * A persisted, two-way state signal keyed by the property name:
     * `private var count by state(0)` or with an explicit store key
     * `private var count by state(0, "counter")`.
     */
    protected fun <T : Any> state(initial: T, key: String? = null): StateDelegate<T> =
        StateDelegate(mutableState(initial, key))

    /**
     * A persisted state signal for code that wants the signal directly:
     * `val name = mutableState("")` can be passed to `TextField(text = name)`.
     */
    protected fun <T : Any> mutableState(initial: T, key: String? = null): StateSignal<T> {
        val state = StateSignal(initial, key)
        stateSignals.add(state)
        return state
    }

    final override fun renderNode(env: Environment): PathlandNode {
        env.state()?.let { persistent ->
            stateSignals.forEach { it.bind(persistent) }
        }
        val resolved = body()
        check(resolved !== this) {
            "${javaClass.name} must return its subtree from body()"
        }
        return resolved.renderNode(env)
    }

    abstract override fun body(): View

    private fun insideAnotherViewConstructor(): Boolean {
        val self = javaClass
        return java.lang.StackWalker
            .getInstance(java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE)
            .walk { frames ->
                frames.anyMatch { frame ->
                    frame.methodName == "<init>" &&
                        frame.declaringClass != self &&
                        frame.declaringClass != Component::class.java &&
                        Component::class.java.isAssignableFrom(frame.declaringClass)
                }
            }
    }
}

/**
 * A persisted state value. It behaves as a [WritableSignal] so it can be passed
 * straight to two-way controls; binding to the session store happens before the
 * owning view's [Component.body] runs.
 */
class StateSignal<T : Any> internal constructor(
    initial: T,
    var key: String? = null,
) : WritableSignal<T> {

    private var current: WritableSignal<T> = Signals.signal(initial)
    private var bound = false

    internal fun bind(persistent: PersistentState) {
        if (bound) return
        val storeKey = checkNotNull(key) { "State has no key (declare it with `by state(...)`) " }
        current = persistent.signal(storeKey, current.get())
        bound = true
    }

    override fun get(): T = current.get()

    override fun set(value: T) = current.set(value)

    override fun update(fn: UnaryOperator<T>) = current.update(fn)

    override fun asReadonly(): Signal<T> = current.asReadonly()

    /** The auto-saved persisted value (the signal itself is a [WritableSignal]). */
    var value: T
        get() = get()
        set(value) = set(value)
}

/** Property-delegate form of [StateSignal]. */
class StateDelegate<T : Any> internal constructor(
    val signal: StateSignal<T>,
) : ReadWriteProperty<Any?, T> {

    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): StateDelegate<T> {
        if (signal.key == null) {
            signal.key = property.name
        }
        return this
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = signal.get()

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) = signal.set(value)
}
