package com.pathland.view.signal;

import java.util.ArrayDeque;

/**
 * Synchronous effect scheduler. Dirty effects are queued and flushed to quiescence
 * at the end of the outermost signal write — the deterministic flush model chosen
 * for SSR + WebSocket delta streaming. No async surprises: by the time {@code set}
 * returns, all effects that depend (transitively) on the write have run.
 *
 * <p>Thread-safety: the queue is global (shared by every session in the JVM) but
 * {@code schedule}/flush are invoked from multiple threads (the per-app actor
 * thread and any SSR/request thread that constructs a session). All three entry
 * points are synchronized on this class so a request-thread flush can never race
 * the actor's mid-flush {@code QUEUE} mutation (the non-thread-safe
 * {@link ArrayDeque}) and corrupt or lose another session's pending effects.
 */
final class Scheduler {

    private static final ArrayDeque<Effect> QUEUE = new ArrayDeque<>();
    private static boolean flushing = false;

    private Scheduler() {}

    static synchronized void schedule(Effect effect) {
        if (!effect.scheduled) {
            effect.scheduled = true;
            QUEUE.add(effect);
        }
        flush();
    }

    static synchronized void flush() {
        if (flushing) {
            return;
        }
        flushing = true;
        try {
            while (!QUEUE.isEmpty()) {
                Effect effect = QUEUE.poll();
                effect.scheduled = false;
                effect.runIfScheduled();
            }
        } finally {
            flushing = false;
        }
    }

    /** Drop a destroyed effect from the queue. */
    static synchronized void cancel(Effect effect) {
        QUEUE.remove(effect);
        effect.scheduled = false;
    }
}