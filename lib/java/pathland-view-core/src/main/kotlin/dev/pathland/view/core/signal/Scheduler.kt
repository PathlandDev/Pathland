package dev.pathland.view.core.signal

import java.util.ArrayDeque

/**
 * Synchronous effect scheduler: the outermost signal write flushes effects to
 * quiescence before returning.
 *
 * Nested writes are folded into the same flush; this keeps signal reads/writes
 * and effect execution on the session thread in a deterministic order.
 */
internal object Scheduler {
    private val QUEUE = ArrayDeque<Effect>()
    private var flushing = false

    @Synchronized
    fun schedule(effect: Effect) {
        if (!effect.scheduled) {
            effect.scheduled = true
            QUEUE.add(effect)
        }
        flush()
    }

    @Synchronized
    fun flush() {
        if (flushing) return
        flushing = true
        try {
            while (QUEUE.isNotEmpty()) {
                val effect = QUEUE.poll()
                effect.scheduled = false
                effect.runIfScheduled()
            }
        } finally {
            flushing = false
        }
    }

    /** Drop a destroyed effect from the queue. */
    @Synchronized
    fun cancel(effect: Effect) {
        QUEUE.remove(effect)
        effect.scheduled = false
    }
}
