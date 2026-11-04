package dev.pathland.view.core

import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.Signals
import dev.pathland.view.core.state.PersistentState

/** The rendering environment — the single inheritance/scoping mechanism. */
class Environment(private val state: PersistentState?) {

    private var windowMetadata: WindowMetadata? = null

    /** The window/surface metadata declared by the root, or null. */
    fun windowMetadata(): WindowMetadata? = windowMetadata

    /** Record the root-declared window metadata during its render. */
    internal fun setWindowMetadata(metadata: WindowMetadata?) {
        windowMetadata = metadata
    }

    /** The session's persistent state (null for a stateless environment). */
    fun state(): PersistentState? = state

    companion object {
        @JvmField
        val BUTTON_STYLE: EnvironmentKey<ButtonStyle> = EnvironmentKey.of("buttonStyle")

        @JvmField
        val LABEL_STYLE: EnvironmentKey<LabelStyle> = EnvironmentKey.of("labelStyle")

        @JvmField
        val VIDEO_STYLE: EnvironmentKey<VideoStyle> = EnvironmentKey.of("videoStyle")

        private val VALUES: ThreadLocal<EnvironmentValues> =
            ThreadLocal.withInitial { EnvironmentValues.empty() }

        /** The default environment (no persistent state). */
        @JvmField
        val DEFAULT = Environment(null)

        /** The current scope (empty at the top of a render). */
        fun current(): EnvironmentValues = VALUES.get()

        /** The scoped value for [key] as a signal (reactive). */
        @JvmStatic
        fun <T> value(key: EnvironmentKey<T>): Signal<T> {
            val scope = VALUES.get()
            if (scope.contains(key)) {
                return toSignal(scope.get(key))
            }
            return EnvironmentSignal(key)
        }

        @Suppress("UNCHECKED_CAST")
        private fun <T> toSignal(value: Any?): Signal<T> {
            if (value is Signal<*>) return value as Signal<T>
            return Signal { value as T }
        }

        private class EnvironmentSignal<T>(private val key: EnvironmentKey<T>) : Signal<T> {
            private var captured: Signal<T>? = null

            @Suppress("UNCHECKED_CAST")
            override fun get(): T {
                val scope = VALUES.get()
                if (scope.contains(key)) {
                    captured = toSignal(scope.get(key))
                }
                return captured?.get() as T
            }
        }

        /** Temporarily set the active scope for a render. */
        fun within(scope: EnvironmentValues) {
            VALUES.set(scope)
        }

        /** Restore the previous active scope. */
        fun restore(previous: EnvironmentValues) {
            VALUES.set(previous)
        }
    }
}
