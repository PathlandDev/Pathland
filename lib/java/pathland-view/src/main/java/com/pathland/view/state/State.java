package com.pathland.view.state;

import com.pathland.view.signal.WritableSignal;

import java.util.function.UnaryOperator;

/**
 * A field-holder for a persisted signal (the Java realization of a
 * {@code @State} field, wired by the {@code pathland-view-processor} annotation
 * processor). Declared as a field in a view and populated by the generated binder before
 * the first render. The processor detects {@code State} fields by type, so no annotation
 * is required:
 *
 * <pre>{@code
 * State<Integer> count = new State<>(0);   // persisted under "count"
 * State<Integer> total = new State<>(0, "total"); // explicit store key
 * count.get();              // current value
 * count.update(v -> v + 1); // mutate (persisted automatically)
 * }</pre>
 *
 * @param <T> the state type
 */
public final class State<T> {

    private final T initial;
    private final String key;
    private WritableSignal<T> signal;

    /** A state persisted under its field name. */
    public State(T initial) {
        this(initial, null);
    }

    /** A state persisted under an explicit store key (overrides the field name). */
    public State(T initial, String key) {
        this.initial = initial;
        this.key = key;
    }

    /**
     * Called by the generated binder to connect this state to the store. The explicit
     * {@link #State(Object, String) key}, when set, wins over {@code fieldName}.
     */
    public void bind(PersistentState state, String fieldName) {
        this.signal = state.signal(key != null ? key : fieldName, initial);
    }

    /** The underlying signal (for {@code TextField}, {@code Text.of(Signal)}, …). */
    public WritableSignal<T> signal() {
        return signal;
    }

    /** The current value. */
    public T get() {
        return signal.get();
    }

    /** Replace the value. */
    public void set(T value) {
        signal.set(value);
    }

    /** Derive a new value from the current one and set it. */
    public void update(UnaryOperator<T> fn) {
        signal.update(fn);
    }
}
