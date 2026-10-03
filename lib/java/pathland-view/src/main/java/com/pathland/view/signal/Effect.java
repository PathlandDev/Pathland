package com.pathland.view.signal;

import java.util.List;
import java.util.Objects;

/**
 * A reactive effect (Angular-style {@code effect}). Runs its body immediately on
 * creation, tracking the signals it reads; re-runs (synchronously, via the shared
 * {@link Scheduler}) whenever a tracked dependency's value actually changes.
 */
final class Effect extends ReactiveNode implements EffectRef {

    private final Runnable fn;
    boolean scheduled;

    Effect(Runnable fn, EffectOptions options, String name) {
        super(name);
        this.fn = Objects.requireNonNull(fn, "effect body");
        this.allowSignalWrites = options.allowSignalWrites();
        if (!options.manualCleanup()) {
            schedule();
        }
    }

    void schedule() {
        Scheduler.schedule(this);
    }

    void runIfScheduled() {
        if (!dirty) {
            return;
        }
        refreshProducers();
        dirty = false;
        if (!firstRun && !depsChanged()) {
            return; // dirtied transitively, but no tracked producer actually changed
        }
        firstRun = false;
        ReactiveContext.push(this);
        // Capture the pre-run dependency edges: a failed run must not permanently
        // sever this effect from its signals (a transient throw — e.g. a foreign-thread
        // flush race — would otherwise leave the binding silently dead forever).
        List<ReactiveNode.ProducerEdge> previous = List.copyOf(producers);
        resetProducers();
        try {
            fn.run();
            error = null;
        } catch (Throwable t) {
            error = t;
            restoreProducers(previous);
            throw new EffectException("Effect '" + describe() + "' threw during flush", t);
        } finally {
            ReactiveContext.pop();
        }
    }

    /** Re-subscribe this effect to the producers it had before a failed run, so a
     *  transient error is retried on the next dependency change instead of silently
     *  killing the binding. */
    private void restoreProducers(List<ReactiveNode.ProducerEdge> previous) {
        resetProducers(); // drop whatever the failed run partially recorded
        for (ReactiveNode.ProducerEdge edge : previous) {
            producers.add(edge);
            edge.node.consumers.add(this);
        }
        dirty = false; // a future producer change re-dirties + re-schedules us
    }

    @Override
    public void destroy() {
        Scheduler.cancel(this);
        resetProducers();
        dirty = false;
    }
}