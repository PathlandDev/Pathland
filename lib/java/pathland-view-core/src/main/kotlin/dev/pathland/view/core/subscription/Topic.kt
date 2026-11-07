package dev.pathland.view.core.subscription

/**
 * A strongly typed, always-scoped subscription key.
 *
 * [T] is the published payload type and [S] is the subject (the thing the
 * subscription is about — a customer id, order id, tenant, conversation, …).
 * Both are checked at compile time by [subscribe] and
 * `PathlandSignalHub.publish`.
 *
 * Identity is by [name] (not by instance), so a topic declared once and shared
 * across modules — or across a process boundary — resolves to the same
 * subscription stream. Two topics with the same name must therefore carry the
 * same payload and subject types; declare each topic once in a shared constant
 * object.
 */
class Topic<T, S>(@JvmField val name: String) {

    override fun equals(other: Any?): Boolean =
        this === other || (other is Topic<*, *> && other.name == name)

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String = "Topic($name)"

    companion object {
        /** A topic named [name] with payload type [T] and subject type [S]. */
        @JvmStatic
        fun <T, S> of(name: String): Topic<T, S> = Topic(name)

        /**
         * An unscoped (global) topic named [name]. Every session that subscribes
         * with `topic(key, initial)` receives publishes; sessions that never
         * subscribe are untouched.
         */
        @JvmStatic
        fun <T> global(name: String): Topic<T, GlobalSubject> = Topic(name)
    }
}

/** Kotlin-friendly alias for an unscoped (global) topic. */
typealias GlobalTopic<T> = Topic<T, GlobalSubject>
