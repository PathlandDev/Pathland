package dev.pathland.view.core.signal

/**
 * Base node of the reactive graph: versioned value, producer edges, consumer
 * edges, dirty/first-run flags, cached error, and write guards.
 */
internal abstract class ReactiveNode(
    @JvmField val name: String?,
) {
    /** A producer edge: the producer node plus its version when this node last read it. */
    internal class ProducerEdge(@JvmField val node: ReactiveNode) {
        @JvmField var lastSeenVersion: Long = node.version
    }

    @JvmField val producers: MutableList<ProducerEdge> = ArrayList()
    @JvmField val consumers: MutableList<ReactiveNode> = ArrayList()

    /** Bumped when this node's own value changes. */
    @JvmField var version: Long = 0

    /** Whether this node must (re)evaluate. */
    @JvmField var dirty: Boolean = true

    /** Whether the evaluation function has run at least once. */
    @JvmField var firstRun: Boolean = true

    /** Whether this node may write signals during its evaluation. */
    @JvmField var allowSignalWrites: Boolean = false

    /** Cached evaluation error (computed) / last run error (effect). */
    @JvmField var error: Throwable? = null

    fun describe(): String = name ?: javaClass.simpleName

    /** Register a dependency from the current reactive context to `this`. */
    fun recordDependency() {
        val consumer = ReactiveContext.current() ?: return
        val idx = consumer.indexOfProducer(this)
        if (idx < 0) {
            consumer.producers.add(ProducerEdge(this))
            consumers.add(consumer)
        } else {
            consumer.producers[idx].lastSeenVersion = version
        }
    }

    private fun indexOfProducer(producer: ReactiveNode): Int =
        producers.indexOfFirst { it.node === producer }

    /** True when at least one producer's version differs from what this node observed. */
    fun depsChanged(): Boolean = producers.any { it.node.version != it.lastSeenVersion }

    /** Drop all producer edges. */
    fun resetProducers() {
        for (edge in producers) {
            edge.node.consumers.remove(this)
        }
        producers.clear()
    }

    /** Re-evaluate dirty computed producers so their versions reflect value changes. */
    fun refreshProducers() {
        for (edge in producers) {
            val node = edge.node
            if (node is ComputedSignal<*>) {
                node.evaluateIfDirty()
            }
        }
    }

    /** Mark every consumer dirty, schedule effects, and propagate transitively. */
    fun propagate() {
        for (consumer in consumers.toList()) {
            consumer.markDirtyAndPropagate()
        }
    }

    private fun markDirtyAndPropagate() {
        if (dirty) return
        dirty = true
        if (this is Effect) {
            schedule()
        }
        for (consumer in consumers.toList()) {
            consumer.markDirtyAndPropagate()
        }
    }

    /** Throw this node's cached evaluation error. */
    fun rethrowError(): Nothing {
        when (val t = error) {
            is RuntimeException -> throw t
            is Error -> throw t
            else -> throw EffectException("Reactive node '${describe()}' failed", t!!)
        }
    }

    /** Guard against signal writes inside computed/effect bodies. */
    fun checkWriteAllowed() {
        val current = ReactiveContext.current() ?: return
        if (current.allowSignalWrites) return
        throw IllegalStateException(
            "Signal writes are not allowed inside ${current.describe()}; " +
                "pass EffectOptions.allowSignalWrites() to opt out",
        )
    }
}
