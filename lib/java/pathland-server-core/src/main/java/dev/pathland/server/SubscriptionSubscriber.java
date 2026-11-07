package dev.pathland.server;

/** A session's inbox for topic updates; the hub delivers to it from any thread. */
interface SubscriptionSubscriber {

    /** Enqueue the latest [value] for [key]; the session applies it on its actor. */
    void enqueue(SubscriptionKey key, Object value);
}
