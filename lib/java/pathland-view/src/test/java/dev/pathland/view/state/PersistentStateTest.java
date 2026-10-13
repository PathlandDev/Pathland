package dev.pathland.view.state;

import dev.pathland.view.signal.WritableSignal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Auto load/save + memoization + scoping of {@link PersistentState}. */
class PersistentStateTest {

    @Test
    void signalLoadsPersistedValueAndAutoSaves() {
        StateStore store = new InMemoryStateStore();

        PersistentState first = new PersistentState(store, "session-1");
        assertEquals(0, first.signal("count", 0).get());
        first.signal("count", 0).set(7);
        first.close();

        // A fresh state for the same scope restores the persisted value.
        PersistentState second = new PersistentState(store, "session-1");
        assertEquals(7, second.signal("count", 0).get());
        second.close();
    }

    @Test
    void signalIsMemoizedByName() {
        PersistentState state = new PersistentState(new InMemoryStateStore(), "s");
        WritableSignal<Integer> a = state.signal("count", 0);
        WritableSignal<Integer> b = state.signal("count", 0);
        assertEquals(a, b, "the same name must return the same signal instance");
        state.close();
    }

    @Test
    void keysAreScopedPerSession() {
        StateStore store = new InMemoryStateStore();

        PersistentState a = new PersistentState(store, "A");
        PersistentState b = new PersistentState(store, "B");
        a.signal("count", 0).set(1);
        b.signal("count", 0).set(2);
        a.close();
        b.close();

        PersistentState ra = new PersistentState(store, "A");
        PersistentState rb = new PersistentState(store, "B");
        assertEquals(1, ra.signal("count", 0).get());
        assertEquals(2, rb.signal("count", 0).get());
        ra.close();
        rb.close();
    }

    @Test
    void closeIsIdempotent() {
        PersistentState state = new PersistentState(new InMemoryStateStore(), "s");
        state.signal("count", 0);
        state.close();
        state.close();
    }
}