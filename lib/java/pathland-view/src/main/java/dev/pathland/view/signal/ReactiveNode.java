package dev.pathland.view.signal;

import java.util.ArrayList;
import java.util.List;

/**
 * Package-private base node of the reactive graph. Each node owns:
 *
 * <ul>
 *   <li>a monotonically increasing {@code version}, bumped only when the node's own
 *       value actually changes (equality-suppressed writes do not bump it);</li>
 *   <li>a {@code producers} list (nodes read during the last evaluation) with the
 *       producer version observed at that time, so a node can cheaply skip re-running
 *       when nothing it depends on actually changed (glitch-free + equality
 *       suppression);</li>
 *   <li>a {@code consumers} list (nodes that depend on this node), used to mark them
 *       dirty and schedule effects when this node's value changes.</li>
 * </ul>
 */
abstract class ReactiveNode {

    /** A producer edge: the producer node plus its version when this node last read it. */
    static final class ProducerEdge {
        final ReactiveNode node;
        long lastSeenVersion;

        ProducerEdge(ReactiveNode node) {
            this.node = node;
            this.lastSeenVersion = node.version;
        }
    }

    final List<ProducerEdge> producers = new ArrayList<>();
    final List<ReactiveNode> consumers = new ArrayList<>();

    /** Bumped when this node's own value changes. */
    long version;

    /** Whether this node must (re)evaluate. */
    boolean dirty = true;

    /** Whether the evaluation function has run at least once. */
    boolean firstRun = true;

    /** Whether this node may write signals during its evaluation. */
    boolean allowSignalWrites = false;

    /** Cached evaluation error (computed) / last run error (effect). */
    Throwable error;

    final String name;

    ReactiveNode(String name) {
        this.name = name;
    }

    String describe() {
        return name != null ? name : getClass().getSimpleName();
    }

    /**
     * Register a dependency from the current reactive context to {@code this}.
     * Called by {@code Signal#get()}.
     */
    final void recordDependency() {
        ReactiveNode consumer = ReactiveContext.current();
        if (consumer == null) {
            return; // plain read outside any reactive context: no dependency
        }
        int idx = consumer.indexOfProducer(this);
        if (idx < 0) {
            consumer.producers.add(new ProducerEdge(this));
            this.consumers.add(consumer);
        } else {
            // Re-read within the same evaluation: refresh the observed version.
            consumer.producers.get(idx).lastSeenVersion = version;
        }
    }

    private int indexOfProducer(ReactiveNode producer) {
        for (int i = 0; i < producers.size(); i++) {
            if (producers.get(i).node == producer) {
                return i;
            }
        }
        return -1;
    }

    /**
     * True when at least one producer's version differs from what this node last
     * observed. Used to skip re-evaluation when a dirty mark turned out not to
     * correspond to an actual value change.
     */
    final boolean depsChanged() {
        for (ProducerEdge edge : producers) {
            if (edge.node.version != edge.lastSeenVersion) {
                return true;
            }
        }
        return false;
    }

    /** Drop all producer edges (and remove this node from each producer's consumers). */
    final void resetProducers() {
        for (ProducerEdge edge : producers) {
            edge.node.consumers.remove(this);
        }
        producers.clear();
    }

    /**
     * Re-evaluate dirty {@code computed} producers so their versions reflect any
     * value change before this node decides whether it must re-run. Equality
     * suppression happens inside the computed (no version bump when unchanged), so
     * this must run before {@link #depsChanged()}.
     */
    final void refreshProducers() {
        for (ProducerEdge edge : producers) {
            if (edge.node instanceof ComputedSignal<?> computed) {
                computed.evaluateIfDirty();
            }
        }
    }

    /**
     * Called after this node's own value changed: mark every (transitive) consumer
     * dirty and schedule the effects among them. Computed consumers are only flagged;
     * they re-evaluate lazily on read.
     */
    final void propagate() {
        // Snapshot: effects/computeds re-record producer edges while flushing, which
        // mutates the consumers list mid-iteration.
        for (ReactiveNode consumer : List.copyOf(consumers)) {
            consumer.markDirtyAndPropagate();
        }
    }

    private void markDirtyAndPropagate() {
        if (dirty) {
            return;
        }
        dirty = true;
        if (this instanceof Effect effect) {
            effect.schedule();
        }
        for (ReactiveNode consumer : List.copyOf(consumers)) {
            consumer.markDirtyAndPropagate();
        }
    }

    /** Throw this node's cached evaluation error (rethrow semantics). */
    final void rethrowError() {
        Throwable t = error;
        if (t instanceof RuntimeException re) {
            throw re;
        }
        if (t instanceof Error er) {
            throw er;
        }
        throw new EffectException("Reactive node '" + describe() + "' failed", t);
    }

    /** Guard against signal writes inside computed/effect bodies. */
    final void checkWriteAllowed() {
        ReactiveNode current = ReactiveContext.current();
        if (current == null || current.allowSignalWrites) {
            return;
        }
        throw new IllegalStateException(
                "Signal writes are not allowed inside " + current.describe()
                        + "; pass EffectOptions.allowSignalWrites() to opt out");
    }
}