package dev.pathland.view.core.signal

import dev.pathland.view.core.state.InMemoryStateStore
import dev.pathland.view.core.state.PersistentState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SignalsStateTest {

    @Test
    fun stateBindsToTheAmbientPersistentState() {
        val store = InMemoryStateStore()
        val state = PersistentState(store, "session-1")

        PersistentState.withCurrent(state) {
            val count = Signals.state("count", 0)
            count.set(4)
            assertEquals(4, store.load("count:session-1", Integer::class.java).get())
        }
    }

    @Test
    fun stateFallsBackToAnInMemorySignalWithoutAContext() {
        val count = Signals.state("count", 0)
        count.set(3)
        assertEquals(3, count.get())
    }

    @Test
    fun sameNameReturnsTheSamePersistentSignal() {
        val store = InMemoryStateStore()
        val state = PersistentState(store, "session-1")

        PersistentState.withCurrent(state) {
            val first = Signals.state("count", 0)
            val second = Signals.state("count", 99)
            assertEquals(first, second)
        }
    }
}
