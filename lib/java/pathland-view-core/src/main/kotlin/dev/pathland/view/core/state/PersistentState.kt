package dev.pathland.view.core.state

import dev.pathland.view.core.signal.EffectRef
import dev.pathland.view.core.signal.Signals
import dev.pathland.view.core.signal.WritableSignal

/**
 * Per-session persisted state. [signal] creates a signal that auto-loads its
 * persisted value and auto-saves on change, keyed `"name:scope"`; signals are
 * memoized by name.
 */
class PersistentState(
    private val store: StateStore,
    private val scope: String?,
) : AutoCloseable {

    private val signals = HashMap<String, WritableSignal<*>>()
    private val persistEffects = ArrayList<EffectRef>()

    /** Create (or return) the signal named [name], auto-loading and auto-saving it. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> signal(name: String, initial: T): WritableSignal<T> {
        val existing = signals[name] as WritableSignal<T>?
        if (existing != null) return existing

        val key = scoped(name)
        val signal = Signals.signal(name, store.load(key, initial.javaClass).orElse(initial))
        signals[name] = signal
        persistEffects.add(Signals.effect {
            try {
                store.save(key, signal.get())
            } catch (_: RuntimeException) {
                // Persistence is best-effort for a UI component.
            }
        })
        return signal
    }

    private fun scoped(name: String): String =
        if (scope.isNullOrEmpty()) name else "$name:$scope"

    override fun close() {
        for (effect in persistEffects) {
            effect.destroy()
        }
        persistEffects.clear()
        signals.clear()
    }

    companion object {
        private val CURRENT = ThreadLocal.withInitial { ArrayList<PersistentState?>() }

        /** The session state active during the current component render, if any. */
        fun current(): PersistentState? = CURRENT.get().lastOrNull()

        /**
         * Run [block] with [state] as the ambient session state for [Signals.state].
         * Nested component renders restore the previous binding afterwards.
         */
        fun <T> withCurrent(state: PersistentState?, block: () -> T): T {
            val stack = CURRENT.get()
            stack.add(state)
            return try {
                block()
            } finally {
                stack.removeAt(stack.size - 1)
            }
        }
    }
}
