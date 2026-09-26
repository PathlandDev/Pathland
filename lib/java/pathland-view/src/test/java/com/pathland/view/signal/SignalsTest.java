package com.pathland.view.signal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Angular-parity semantics for signals/computed/effects. */
class SignalsTest {

    // --- writable + computed: lazy + memoized ---

    @Test
    void computedIsLazyAndMemoized() {
        AtomicInteger runs = new AtomicInteger();
        WritableSignal<Integer> a = Signals.signal(1);
        Signal<Integer> b = Signals.computed(() -> {
            runs.incrementAndGet();
            return a.get() * 2;
        });
        assertEquals(0, runs.get(), "computed must not run before first read");
        assertEquals(2, b.get());
        assertEquals(2, b.get());
        assertEquals(1, runs.get(), "second read must reuse the memoized value");
    }

    @Test
    void computedRecomputesOnlyWhenProducerChanges() {
        AtomicInteger runs = new AtomicInteger();
        WritableSignal<Integer> a = Signals.signal(1);
        Signal<Integer> b = Signals.computed(() -> {
            runs.incrementAndGet();
            return a.get() + 1;
        });
        assertEquals(2, b.get());
        a.set(5);
        assertEquals(6, b.get());
        assertEquals(2, runs.get());
    }

    @Test
    void equalitySuppressedWriteDoesNotPropagate() {
        AtomicInteger runs = new AtomicInteger();
        WritableSignal<Integer> a = Signals.signal(1);
        Signal<Integer> b = Signals.computed(() -> {
            runs.incrementAndGet();
            return a.get() * 10;
        });
        assertEquals(10, b.get());
        a.set(1); // equal -> no propagation
        assertEquals(10, b.get());
        assertEquals(1, runs.get(), "equal write must not recompute");
    }

    // --- glitch-free diamond ---

    @Test
    void glitchFreeDiamondRecomputesOnce() {
        WritableSignal<Integer> a = Signals.signal(1);
        AtomicInteger bRuns = new AtomicInteger();
        AtomicInteger cRuns = new AtomicInteger();
        AtomicInteger dRuns = new AtomicInteger();
        Signal<Integer> b = Signals.computed(() -> {
            bRuns.incrementAndGet();
            return a.get() + 1;
        });
        Signal<Integer> c = Signals.computed(() -> {
            cRuns.incrementAndGet();
            return a.get() * 2;
        });
        Signal<Integer> d = Signals.computed(() -> {
            dRuns.incrementAndGet();
            return b.get() + c.get();
        });
        assertEquals(4, d.get()); // b=2, c=2, d=4

        a.set(10);
        assertEquals(31, d.get()); // b=11, c=20, d=31
        assertEquals(2, dRuns.get(), "d must recompute exactly once per a change");
    }

    // --- effects ---

    @Test
    void effectRunsOnCreationAndReRunsOnChange() {
        WritableSignal<Integer> a = Signals.signal(0);
        List<Integer> seen = new ArrayList<>();
        EffectRef effect = Signals.effect(() -> seen.add(a.get()));
        assertEquals(List.of(0), seen, "effect runs once immediately");
        a.set(1);
        a.set(2);
        assertEquals(List.of(0, 1, 2), seen, "effect re-runs synchronously on each change");
        effect.destroy();
    }

    @Test
    void destroyedEffectStopsRunning() {
        WritableSignal<Integer> a = Signals.signal(0);
        List<Integer> seen = new ArrayList<>();
        EffectRef effect = Signals.effect(() -> seen.add(a.get()));
        effect.destroy();
        a.set(1);
        a.set(2);
        assertEquals(List.of(0), seen, "destroyed effect must not re-run");
    }

    @Test
    void effectSkipsWhenComputedValueUnchanged() {
        WritableSignal<Integer> a = Signals.signal(1);
        // This computed maps two inputs to the same output (equality suppression).
        Signal<Integer> clamped = Signals.computed(() -> a.get() >= 0 ? 1 : -1);
        AtomicInteger effectRuns = new AtomicInteger();
        EffectRef effect = Signals.effect(() -> {
            clamped.get();
            effectRuns.incrementAndGet();
        });
        assertEquals(1, effectRuns.get());
        a.set(5); // clamped stays 1
        assertEquals(1, effectRuns.get(), "effect must not run when the derived value is unchanged");
        a.set(-3); // clamped becomes -1
        assertEquals(2, effectRuns.get());
        effect.destroy();
    }

    // --- write discipline ---

    @Test
    void writeInsideComputedIsForbidden() {
        WritableSignal<Integer> a = Signals.signal(0);
        Signal<Integer> bad = Signals.computed(() -> {
            a.set(1);
            return a.get();
        });
        assertThrows(IllegalStateException.class, bad::get);
    }

    @Test
    void writeInsideEffectIsForbiddenByDefaultButAllowedOptIn() {
        WritableSignal<Integer> a = Signals.signal(0);

        // The effect's initial flush throws (wrapping the IllegalStateException).
        assertThrows(EffectException.class, () -> Signals.effect(() -> a.set(1)));

        EffectRef allowed = Signals.effect(() -> a.set(7), EffectOptions.allowWrites());
        assertEquals(7, a.get());
        allowed.destroy();
    }

    // --- untracked ---

    @Test
    void untrackedReadsRegisterNoDependency() {
        WritableSignal<Integer> a = Signals.signal(0);
        AtomicInteger effectRuns = new AtomicInteger();
        EffectRef effect = Signals.effect(() -> {
            Signals.untracked(a::get);
            effectRuns.incrementAndGet();
        });
        a.set(1);
        a.set(2);
        assertEquals(1, effectRuns.get(), "untracked reads must not schedule re-runs");
        effect.destroy();
    }

    // --- error caching + recovery ---

    @Test
    void computedCachesErrorThenRecovers() {
        WritableSignal<Integer> a = Signals.signal(0);
        Signal<Integer> flaky = Signals.computed(() -> {
            if (a.get() == 0) {
                throw new ArithmeticException("boom");
            }
            return a.get() * 10;
        });
        assertThrows(ArithmeticException.class, flaky::get);
        assertThrows(ArithmeticException.class, flaky::get, "error is cached between reads");
        a.set(1);
        assertEquals(10, flaky.get(), "computed recovers once a producer changes");
    }

    // --- circular dependency ---

    @Test
    void circularDependencyIsDetected() {
        // a depends on b, b depends on a.
        Signal<Integer>[] a = new Signal[1];
        Signal<Integer>[] b = new Signal[1];
        a[0] = Signals.computed(() -> b[0].get() + 1);
        b[0] = Signals.computed(() -> a[0].get() + 1);
        assertThrows(IllegalStateException.class, () -> a[0].get());
    }

    // --- update + asReadonly ---

    @Test
    void updateAndReadonly() {
        WritableSignal<Integer> a = Signals.signal(0);
        a.update(v -> v + 5);
        assertEquals(5, a.get());
        Signal<Integer> ro = a.asReadonly();
        assertEquals(5, ro.get());
        assertTrue(ro instanceof Signal, "readonly view is read-only");
    }

    // --- constant ---

    @Test
    void constantIsImmutableAndDependencyFree() {
        Signal<String> c = Signals.constant("Hello");
        assertEquals("Hello", c.get());
        assertEquals("Hello", c.get(), "constant never changes");
        assertTrue(c instanceof ConstantSignal, "constant is a ConstantSignal (emitter treats it as non-reactive)");

        // A computed reading only constants runs exactly once (no dependency to track).
        AtomicInteger runs = new AtomicInteger();
        Signal<Integer> derived = Signals.computed(() -> {
            runs.incrementAndGet();
            return c.get().length();
        });
        assertEquals(5, derived.get());
        assertEquals(5, derived.get());
        assertEquals(1, runs.get(), "no re-computation for an immutable constant");
    }
}