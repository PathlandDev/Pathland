package dev.pathland.view.core.state

import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/** An in-memory, thread-safe [StateStore]. */
class InMemoryStateStore : StateStore, DebugStateStore {
    private val states: ConcurrentMap<String, Any> = ConcurrentHashMap()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> load(key: String, clazz: Class<T>): Optional<T> {
        val value = states[key]
        return if (value != null && clazz.isInstance(value)) {
            Optional.of(clazz.cast(value))
        } else {
            Optional.empty()
        }
    }

    override fun save(key: String, state: Any) {
        states[key] = state
    }

    override fun delete(key: String) {
        states.remove(key)
    }

    override fun keys(): List<String> = states.keys.sorted()

    override fun get(key: String): Optional<Any> = Optional.ofNullable(states[key])

    override fun toString(): String = "InMemoryStateStore(${states.size} keys)"
}
