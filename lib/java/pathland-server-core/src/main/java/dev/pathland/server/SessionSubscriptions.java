package dev.pathland.server;

import dev.pathland.view.core.signal.Signal;
import dev.pathland.view.core.signal.Signals;
import dev.pathland.view.core.signal.WritableSignal;
import dev.pathland.view.core.subscription.SessionContext;
import dev.pathland.view.core.subscription.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A session's topic subscriptions. Views call `topic(...)` during render; this
 * class creates the session-local mirror signal, registers the session with the
 * app's {@link PathlandSignalHub}, and applies published values on the session's
 * actor.
 *
 * <p>Publishes arrive on arbitrary threads; [enqueue] only stores the latest value
 * per subscription and schedules at most one drain task per session, so a hot
 * producer cannot flood the actor or grow the queue without bound.
 */
final class SessionSubscriptions implements SessionContext, SubscriptionSubscriber {

    private static final Logger LOG = LoggerFactory.getLogger(SessionSubscriptions.class);

    /** A published value; wraps null (ConcurrentHashMap rejects null values). */
    private record Pending(Object value) {
    }

    private final PathlandSignalHub hub;
    private final Consumer<Runnable> actor;
    private final PathlandTelemetry telemetry;
    private final String mount;

    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean scheduled = new AtomicBoolean();

    /** Actor-confined: topic key → local mirror signal. */
    private final Map<SubscriptionKey, WritableSignal<Object>> locals = new HashMap<>();

    /** Latest unpublished value per subscription (latest-wins). */
    private final ConcurrentHashMap<SubscriptionKey, Pending> pending = new ConcurrentHashMap<>();

    SessionSubscriptions(
            PathlandSignalHub hub,
            Consumer<Runnable> actor,
            PathlandTelemetry telemetry,
            String mount) {
        this.hub = hub;
        this.actor = actor;
        this.telemetry = telemetry == null ? PathlandTelemetry.NOOP : telemetry;
        this.mount = mount == null ? "/" : mount;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, S> Signal<T> subscribe(Topic<T, S> topic, S subject, T initial) {
        if (closed.get()) {
            throw new IllegalStateException("session is closed");
        }
        SubscriptionKey key = new SubscriptionKey(topic.name, subject);
        WritableSignal<Object> existing = locals.get(key);
        if (existing != null) {
            return (Signal<T>) (Signal<?>) existing;
        }
        WritableSignal<T> signal = Signals.signal(topic.name, initial);
        locals.put(key, (WritableSignal<Object>) (WritableSignal<?>) signal);
        if (hub != null) {
            hub.subscribe(key, this);
        }
        return signal;
    }

    @Override
    public <T, S> void unsubscribe(Topic<T, S> topic, S subject) {
        SubscriptionKey key = new SubscriptionKey(topic.name, subject);
        if (locals.remove(key) != null) {
            pending.remove(key);
            if (hub != null) {
                hub.unsubscribe(key, this);
            }
        }
    }

    @Override
    public void submit(Runnable task) {
        if (task == null || closed.get()) {
            return;
        }
        actor.accept(task);
    }

    @Override
    public void enqueue(SubscriptionKey key, Object value) {
        if (closed.get()) {
            return;
        }
        if (pending.put(key, new Pending(value)) != null) {
            telemetry.subscriptionCoalesced(mount, key.topic());
        }
        schedule();
    }

    /** Schedule one drain task; at most one is pending per session. */
    private void schedule() {
        if (!scheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            actor.accept(this::drain);
        } catch (RuntimeException e) {
            // The actor is shutting down or rejected the task; updates are moot.
            scheduled.set(false);
        }
    }

    /** Actor task: apply every latest value, looping while new updates arrive. */
    private void drain() {
        while (!closed.get()) {
            for (SubscriptionKey key : new ArrayList<>(pending.keySet())) {
                Pending value = pending.remove(key);
                if (value != null) {
                    apply(key, value.value());
                }
            }
            scheduled.set(false);
            if (!pending.isEmpty() && scheduled.compareAndSet(false, true)) {
                continue;
            }
            return;
        }
        pending.clear();
    }

    @SuppressWarnings("unchecked")
    private void apply(SubscriptionKey key, Object value) {
        WritableSignal<Object> signal = locals.get(key);
        if (signal == null) {
            telemetry.subscriptionDropped(mount, key.topic(), "unsubscribed");
            return;
        }
        try {
            signal.set(value);
        } catch (RuntimeException e) {
            LOG.warn("topic '{}' update failed for subject {}: {}",
                    key.topic(), key.subject(), e.getMessage());
            telemetry.subscriptionDropped(mount, key.topic(), "effect-error");
        }
    }

    /** Stop routing updates to this session and unregister every subscription. */
    void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (hub != null) {
            for (SubscriptionKey key : new ArrayList<>(locals.keySet())) {
                hub.unsubscribe(key, this);
            }
        }
        locals.clear();
        pending.clear();
        scheduled.set(false);
    }

    /** Whether this session's subscriptions are closed (test hook). */
    boolean isClosed() {
        return closed.get();
    }
}
