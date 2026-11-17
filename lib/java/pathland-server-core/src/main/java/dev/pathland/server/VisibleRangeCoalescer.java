package dev.pathland.server;

import dev.pathland.core.transport.Event;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Latest-wins coalescing for inbound {@code VISIBLE_RANGE} events.
 *
 * <p>A fast scrollbar drag can report a new visible range every animation frame
 * (spec/EVENTS.md §Visible range). Applying every report would make a lazy
 * window and its paged source walk every intermediate page between the start
 * and end of the gesture. Only the latest range for a given
 * {@code (session, target)} matters, so this coalescer keeps the newest event
 * and dispatches it once after a short interval
 * (ADR-0017).
 *
 * <p>Other event types never pass through this class; the registry dispatches
 * them immediately. {@link #offer} may be called from any thread (the transport
 * callback); the sink runs on the scheduler thread and typically enqueues actor
 * work.
 */
final class VisibleRangeCoalescer implements AutoCloseable {

    /** Default latest-wins window before a coalesced range is dispatched. */
    static final long DEFAULT_DELAY_MILLIS = 50;

    /** Receives the latest range for a session after the coalescing interval. */
    interface Sink {
        void accept(String sessionId, Event event);
    }

    private final long delayMillis;
    private final Sink sink;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<Key, Pending> pending = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    VisibleRangeCoalescer(long delayMillis, Sink sink) {
        this(delayMillis, sink, Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pathland-visible-range");
            t.setDaemon(true);
            return t;
        }));
    }

    VisibleRangeCoalescer(long delayMillis, Sink sink, ScheduledExecutorService scheduler) {
        this.delayMillis = Math.max(0, delayMillis);
        this.sink = sink;
        this.scheduler = scheduler;
    }

    /** Keep {@code event} as the latest range for its session/target. */
    void offer(String sessionId, Event event) {
        if (closed.get()) {
            return;
        }
        Key key = new Key(sessionId, event.target());
        AtomicBoolean created = new AtomicBoolean();
        pending.compute(key, (ignored, existing) -> {
            if (existing != null) {
                existing.event = event;
                return existing;
            }
            created.set(true);
            return new Pending(event);
        });
        if (created.get()) {
            try {
                scheduler.schedule(() -> flush(key), delayMillis, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException e) {
                pending.remove(key);
            }
        }
    }

    private void flush(Key key) {
        if (closed.get()) {
            return;
        }
        Pending removed = pending.remove(key);
        if (removed != null && removed.event != null) {
            sink.accept(key.sessionId(), removed.event);
        }
    }

    /** Drop pending ranges for a session that is closing. */
    void removeSession(String sessionId) {
        pending.keySet().removeIf(key -> key.sessionId().equals(sessionId));
    }

    /** Drop every pending range (registry shutdown). */
    void clear() {
        pending.clear();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        pending.clear();
        scheduler.shutdownNow();
    }

    private record Key(String sessionId, int target) {
    }

    private static final class Pending {
        private volatile Event event;

        private Pending(Event event) {
            this.event = event;
        }
    }
}
