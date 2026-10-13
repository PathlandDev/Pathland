package dev.pathland.view.signal;

/**
 * A reactive value (Angular-style). Reading a signal via {@link #get()} records a
 * dependency on the enclosing reactive context (a {@code computed} or {@code effect}),
 * so that context re-runs when the signal's value changes.
 *
 * <p>Signals are single-threaded by contract: reads and writes must happen on the
 * application's actor thread (the same model as Angular change detection and the
 * single-threaded app actors in the Quarkus/Spring demos).
 *
 * @param <T> the value type
 */
@FunctionalInterface
public interface Signal<T> {

    /** Read the current value, registering a dependency on the current context. */
    T get();
}