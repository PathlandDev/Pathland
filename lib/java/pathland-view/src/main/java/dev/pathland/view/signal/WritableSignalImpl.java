package dev.pathland.view.signal;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.UnaryOperator;

/**
 * A writable signal backed by a reactive graph node.
 */
final class WritableSignalImpl<T> extends ReactiveNode implements WritableSignal<T> {

    private T value;
    private final BiPredicate<T, T> equal;

    WritableSignalImpl(T value, BiPredicate<T, T> equal, String name) {
        super(name);
        this.value = Objects.requireNonNull(value, "initial value");
        this.equal = equal;
    }

    @Override
    public T get() {
        recordDependency();
        return value;
    }

    @Override
    public void set(T value) {
        if (equal.test(this.value, value)) {
            return; // equality-suppressed: no propagation
        }
        checkWriteAllowed();
        this.value = Objects.requireNonNull(value, "value");
        version++;
        propagate(); // marks consumers dirty, schedules effects, flushes synchronously
    }

    @Override
    public void update(UnaryOperator<T> fn) {
        set(fn.apply(value));
    }

    @Override
    public Signal<T> asReadonly() {
        return this::get;
    }
}