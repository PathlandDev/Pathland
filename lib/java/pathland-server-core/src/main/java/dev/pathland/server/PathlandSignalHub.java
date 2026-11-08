package dev.pathland.server;

import dev.pathland.view.core.subscription.GlobalSubject;
import dev.pathland.view.core.subscription.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The app-scoped topic fan-out: external code publishes a value for a
 * `(topic, subject)` pair, and only the sessions that subscribed to that exact
 * pair are updated. The hub never touches a session's reactive graph directly —
 * each subscriber enqueues the update, and the session applies it on its actor.
 *
 * <p>One hub belongs to one mounted app (one {@link PathlandRegistry}); two apps
 * on the same server never see each other's topics. Publish is non-blocking and
 * may be called from any thread.
 *
 * <p>Topics are declared once as shared {@link Topic} constants. The topic name is
 * the wirable identity; the subject is the scoped instance (a customer id, an
 * order id, …).
 */
public final class PathlandSignalHub {

    private static final Logger LOG = LoggerFactory.getLogger(PathlandSignalHub.class);

    private final String mount;
    private final PathlandTelemetry telemetry;
    private final ConcurrentHashMap<SubscriptionKey, Set<SubscriptionSubscriber>> subscribers =
            new ConcurrentHashMap<>();

    public PathlandSignalHub(String mount, PathlandTelemetry telemetry) {
        this.mount = mount == null ? "/" : mount;
        this.telemetry = telemetry == null ? PathlandTelemetry.NOOP : telemetry;
    }

    /**
     * Publish [value] to every session subscribed to an unscoped (global) [topic].
     * Sessions that never subscribed are untouched. Safe from any thread.
     */
    public <T> void publish(Topic<T, GlobalSubject> topic, T value) {
        publish(topic, GlobalSubject.INSTANCE, value);
    }

    /**
     * Publish [value] to every session subscribed to [topic] for [subject].
     * Safe from any thread; non-blocking.
     */
    public <T, S> void publish(Topic<T, S> topic, S subject, T value) {
        Objects.requireNonNull(topic, "topic");
        publishTo(new SubscriptionKey(topic.name, subject), topic.name, value);
    }

    /**
     * Publish an untyped value by topic name (Dev UI tooling): routes exactly like
     * the typed overload. A value that does not match the subscriber's signal type
     * is dropped by that session (the same failure path as any malformed publish).
     */
    public void publishRaw(String topic, Object subject, Object value) {
        Objects.requireNonNull(topic, "topic");
        publishTo(new SubscriptionKey(topic, subject), topic, value);
    }

    private void publishTo(SubscriptionKey key, String topicName, Object value) {
        Set<SubscriptionSubscriber> targets = subscribers.get(key);
        int count = targets == null ? 0 : targets.size();
        telemetry.subscriptionPublished(mount, topicName, count);
        if (count == 0) {
            telemetry.subscriptionDropped(mount, topicName, "no-subscribers");
            if (LOG.isDebugEnabled()) {
                LOG.debug("topic '{}' for subject {} published with no subscribers",
                        topicName, key.subject());
            }
            return;
        }
        for (SubscriptionSubscriber target : List.copyOf(targets)) {
            target.enqueue(key, value);
        }
    }

    /** Register a session's inbox for [key] (called on the session actor). */
    void subscribe(SubscriptionKey key, SubscriptionSubscriber subscriber) {
        subscribers.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet()).add(subscriber);
    }

    /** Remove a session's inbox from [key] (called on the session actor). */
    void unsubscribe(SubscriptionKey key, SubscriptionSubscriber subscriber) {
        subscribers.computeIfPresent(key, (ignored, targets) -> {
            targets.remove(subscriber);
            return targets.isEmpty() ? null : targets;
        });
    }

    /** The number of live subscriptions for `(topic, subject)` (a test/metrics hook). */
    public int subscriberCount(Topic<?, ?> topic, Object subject) {
        Set<SubscriptionSubscriber> targets =
                subscribers.get(new SubscriptionKey(topic.name, subject));
        return targets == null ? 0 : targets.size();
    }

    /** One live `(topic, subject)` registration set. */
    public record SubscriptionCount(String topic, String subject, int subscribers) {
    }

    /** A read-only snapshot of every live subscription (diagnostic tooling). */
    public List<SubscriptionCount> subscriptions() {
        java.util.List<SubscriptionCount> snapshot = new java.util.ArrayList<>(subscribers.size());
        for (var entry : subscribers.entrySet()) {
            snapshot.add(new SubscriptionCount(
                    entry.getKey().topic(),
                    String.valueOf(entry.getKey().subject()),
                    entry.getValue().size()));
        }
        snapshot.sort(java.util.Comparator
                .comparing(SubscriptionCount::topic)
                .thenComparing(SubscriptionCount::subject));
        return List.copyOf(snapshot);
    }

    /** The mount this hub serves. */
    public String mount() {
        return mount;
    }

    /** Drop every registration (host shutdown). */
    void clear() {
        subscribers.clear();
    }
}
