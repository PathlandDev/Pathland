package dev.pathland.server;

import java.util.Objects;

/**
 * The routing key of a topic subscription: a topic name plus a subject. Identity is
 * value-based (`topic`, `subject`), so a view and a publisher that use the same
 * topic constant and subject reach the same stream. The subject is mandatory —
 * every subscription is scoped.
 */
final class SubscriptionKey {

    private final String topic;
    private final Object subject;

    SubscriptionKey(String topic, Object subject) {
        this.topic = Objects.requireNonNull(topic, "topic");
        this.subject = Objects.requireNonNull(subject, "subject");
    }

    String topic() {
        return topic;
    }

    Object subject() {
        return subject;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SubscriptionKey key)) {
            return false;
        }
        return topic.equals(key.topic) && subject.equals(key.subject);
    }

    @Override
    public int hashCode() {
        return 31 * topic.hashCode() + subject.hashCode();
    }

    @Override
    public String toString() {
        return topic + "@" + subject;
    }
}
