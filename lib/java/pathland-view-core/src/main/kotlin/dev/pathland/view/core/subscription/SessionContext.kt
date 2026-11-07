package dev.pathland.view.core.subscription

import dev.pathland.view.core.signal.Signal

/**
 * The session-scoped surface available to views while they render: topic
 * subscriptions and a way to schedule work on the session's actor.
 *
 * A live [SessionContext] is installed by the server runtime for the duration of
 * every actor task (mount, dispatch, subscription drain, …), so [Topic] reads can
 * resolve without any explicit plumbing. Outside a session (an emitter-only unit
 * test, for example) [current] is `null`.
 */
interface SessionContext {

    /** Subscribe to [topic] for [subject], seeded with [initial]. Idempotent per `(topic, subject)`. */
    fun <T, S> subscribe(topic: Topic<T, S>, subject: S, initial: T): Signal<T>

    /** Drop the session's subscription for `(topic, subject)`. */
    fun <T, S> unsubscribe(topic: Topic<T, S>, subject: S)

    /** Run [task] on this session's actor (never on the caller's thread). */
    fun submit(task: Runnable)

    companion object {
        private val CURRENT = ThreadLocal<SessionContext?>()

        /** The session context of the current actor task, or `null`. */
        @JvmStatic
        fun current(): SessionContext? = CURRENT.get()

        /** Install [context] for the current thread; returns the previous one. */
        @JvmStatic
        fun push(context: SessionContext): SessionContext? {
            val previous = CURRENT.get()
            CURRENT.set(context)
            return previous
        }

        /** Restore the context returned by [push]. */
        @JvmStatic
        fun restore(previous: SessionContext?) {
            if (previous == null) CURRENT.remove() else CURRENT.set(previous)
        }
    }
}
