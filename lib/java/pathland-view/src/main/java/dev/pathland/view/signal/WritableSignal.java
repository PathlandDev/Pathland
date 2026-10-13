package dev.pathland.view.signal;

import java.util.function.UnaryOperator;

/**
 * A writable signal (Angular-style {@code WritableSignal}). Setting a new value
 * that differs (per the equality comparator) propagates to dependent computed
 * signals and effects, which flush synchronously at the end of the write.
 *
 * @param <T> the value type
 */
public interface WritableSignal<T> extends Signal<T> {

    /** Replace the current value. No-op (no propagation) when equal to the current value. */
    void set(T value);

    /** Derive a new value from the current one and {@link #set} it. */
    void update(UnaryOperator<T> fn);

    /** A read-only view of this signal (write methods are not available). */
    Signal<T> asReadonly();
}