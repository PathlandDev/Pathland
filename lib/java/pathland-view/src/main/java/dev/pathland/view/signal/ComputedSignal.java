package dev.pathland.view.signal;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * A lazy, memoized derived signal (Angular-style {@code computed}).
 *
 * <p>The body runs only on first read and thereafter only when a producer's version
 * actually changed. If the recomputed value equals the cached value (per the equality
 * comparator), the version is not bumped and nothing downstream re-runs. A throw in
 * the body is cached and rethrown on subsequent reads until a producer changes.
 */
final class ComputedSignal<T> extends ReactiveNode implements Signal<T> {

    private final Supplier<T> fn;
    private final BiPredicate<T, T> equal;
    private T cachedValue;

    ComputedSignal(Supplier<T> fn, BiPredicate<T, T> equal) {
        super(null);
        this.fn = Objects.requireNonNull(fn, "computed body");
        this.equal = equal;
    }

    @Override
    public T get() {
        if (ReactiveContext.contains(this)) {
            throw new IllegalStateException(
                    "Circular signal dependency: computed '" + describe() + "' depends on itself");
        }
        evaluateIfDirty();
        if (error != null) {
            rethrowError(); // cached error rethrown until a producer changes
        }
        recordDependency();
        return cachedValue;
    }

    void evaluateIfDirty() {
        if (!dirty) {
            return;
        }
        refreshProducers();
        dirty = false;
        if (!firstRun && !depsChanged()) {
            return; // producers unchanged since last evaluation: nothing to recompute
        }
        firstRun = false;
        ReactiveContext.push(this);
        resetProducers();
        try {
            T next = fn.get();
            if (equal.test(cachedValue, next)) {
                // Same value: keep version, do not propagate to consumers.
                error = null;
            } else {
                cachedValue = next;
                error = null;
                version++;
                propagate();
            }
        } catch (Throwable t) {
            if (!Objects.equals(error, t)) {
                error = t;
                version++; // notify consumers so they re-run and observe the error
                propagate();
            }
        } finally {
            ReactiveContext.pop();
        }
    }
}