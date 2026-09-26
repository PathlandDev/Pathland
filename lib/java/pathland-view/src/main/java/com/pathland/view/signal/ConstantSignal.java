package com.pathland.view.signal;

import java.util.Objects;

/**
 * An immutable signal whose value never changes (a "constant"). Reads return the
 * value without recording a reactive dependency — there is nothing to track, and
 * the emitter treats a constant as a plain (non-reactive) property, so static
 * values carry no binding overhead. Created via {@link Signals#constant}.
 *
 * @param <T> the value type
 */
public final class ConstantSignal<T> implements Signal<T> {

    private final T value;

    ConstantSignal(T value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    @Override
    public T get() {
        return value;
    }
}