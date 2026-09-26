package com.pathland.view.signal;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * Factory for Angular-style reactive primitives:
 *
 * <pre>{@code
 * WritableSignal<Integer> count = Signals.signal(0);
 * Signal<String> label = Signals.computed(() -> "Count: " + count.get());
 * EffectRef e = Signals.effect(() -> emit(label.get()));
 * count.set(1); // synchronously flushes the effect
 * e.destroy();
 * int n = Signals.untracked(() -> count.get()); // no dependency recorded
 * }</pre>
 *
 * <p>Equality defaults to {@link Objects#equals}. Effects flush synchronously at the
 * end of the outermost write. Signals are single-threaded by contract.
 */
public final class Signals {

    private Signals() {}

    /** A writable signal with {@link Objects#equals} equality. */
    public static <T> WritableSignal<T> signal(T initial) {
        return new WritableSignalImpl<>(initial, Objects::equals, null);
    }

    /** A writable signal with a custom equality comparator. */
    public static <T> WritableSignal<T> signal(T initial, BiPredicate<T, T> equal) {
        return new WritableSignalImpl<>(initial, Objects.requireNonNull(equal, "equal"), null);
    }

    /** A named writable signal (names surface in dependency errors). */
    public static <T> WritableSignal<T> signal(String name, T initial) {
        return new WritableSignalImpl<>(initial, Objects::equals, name);
    }

    /**
     * An immutable signal whose value never changes. Used to give a view a single
     * {@link Signal} field while still authoring static values ergonomically
     * ({@code Text.of("…")} ≡ {@code Text.of(Signals.constant("…"))}). The emitter
     * treats a constant as a plain (non-reactive) property — no binding/effect.
     */
    public static <T> Signal<T> constant(T value) {
        return new ConstantSignal<>(value);
    }

    /** A lazy, memoized derived signal. */
    public static <T> Signal<T> computed(Supplier<T> fn) {
        return new ComputedSignal<>(Objects.requireNonNull(fn, "computed body"), Objects::equals);
    }

    /** A derived signal with a custom equality comparator. */
    public static <T> Signal<T> computed(Supplier<T> fn, BiPredicate<T, T> equal) {
        return new ComputedSignal<>(
                Objects.requireNonNull(fn, "computed body"), Objects.requireNonNull(equal, "equal"));
    }

    /** Register an effect; runs immediately, then on tracked dependency changes. */
    public static EffectRef effect(Runnable fn) {
        return effect(fn, EffectOptions.DEFAULT);
    }

    /** Register an effect with options. */
    public static EffectRef effect(Runnable fn, EffectOptions options) {
        return new Effect(
                Objects.requireNonNull(fn, "effect body"),
                Objects.requireNonNull(options, "options"),
                null);
    }

    /** A named effect. */
    public static EffectRef effect(String name, Runnable fn) {
        return new Effect(Objects.requireNonNull(fn, "effect body"), EffectOptions.DEFAULT, name);
    }

    /**
     * Read signals without recording dependencies on the enclosing context. Any
     * signals read inside {@code fn} are not tracked, so the surrounding computed or
     * effect will not re-run when they change.
     */
    public static <T> T untracked(Supplier<T> fn) {
        ArrayDeque<ReactiveNode> stack = ReactiveContext.stack();
        ArrayDeque<ReactiveNode> saved = new ArrayDeque<>(stack);
        stack.clear();
        try {
            return Objects.requireNonNull(fn, "untracked body").get();
        } finally {
            stack.addAll(saved);
        }
    }
}