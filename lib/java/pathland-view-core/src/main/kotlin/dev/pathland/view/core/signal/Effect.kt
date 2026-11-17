package dev.pathland.view.core.signal

/**
 * A reactive effect: runs an action when any tracked dependency changes.
 *
 * Effects are scheduled through the global [Scheduler] and flush synchronously
 * on the thread that writes a signal. Dependencies are re-collected on each
 * run; an effect may opt into allowing signal writes. Errors are cached and
 * rethrown on the next flush.
 */
internal class Effect(
    private val fn: Runnable,
    options: EffectOptions,
    name: String?,
) : ReactiveNode(name), EffectRef {

    @JvmField var scheduled: Boolean = false

    init {
        allowSignalWrites = options.allowSignalWrites()
        if (!options.manualCleanup()) {
            schedule()
        }
    }

    fun schedule() {
        Scheduler.schedule(this)
    }

    fun runIfScheduled() {
        if (!dirty) return
        refreshProducers()
        dirty = false
        if (!firstRun && !depsChanged()) return
        firstRun = false
        ReactiveContext.push(this)
        val previous = producers.toList()
        resetProducers()
        try {
            fn.run()
            error = null
        } catch (t: Throwable) {
            error = t
            restoreProducers(previous)
            throw EffectException("Effect '${describe()}' threw during flush", t)
        } finally {
            ReactiveContext.pop()
        }
    }

    private fun restoreProducers(previous: List<ProducerEdge>) {
        resetProducers()
        for (edge in previous) {
            producers.add(edge)
            edge.node.consumers.add(this)
        }
        dirty = false
    }

    override fun destroy() {
        Scheduler.cancel(this)
        resetProducers()
        dirty = false
    }
}
