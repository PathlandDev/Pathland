package dev.pathland.view.core.signal

import java.util.ArrayDeque

/** Synchronous effect scheduler (flushes to quiescence at the outermost write). */
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
