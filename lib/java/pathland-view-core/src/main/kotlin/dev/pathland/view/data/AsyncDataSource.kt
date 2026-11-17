package dev.pathland.view.data

import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.Signals
import dev.pathland.view.core.signal.WritableSignal
import dev.pathland.view.core.subscription.SessionContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * A blocking load-all loader (JDBC/JPA or a blocking REST client).
 *
 * `AsyncDataSource.ofBlocking` runs it on an executor and applies the result on
 * the session actor; use it only for small lists.
 */
fun interface ListLoader<T> {
    fun load(): List<T>
}

/**
 * A `CompletionStage` load-all loader.
 *
 * The stage may complete on any thread; the source applies the result on the
 * session actor. Use it for REST/WebClient/Mutiny sources.
 */
fun interface AsyncListLoader<T> {
    fun load(): CompletionStage<List<T>>
}

/**
 * Loads a small list once from a database, REST API, or other source.
 *
 * A live session starts the load immediately and applies the result on the
 * session actor; an SSR session starts in the loading state and does not run
 * async work (the SSR render is discarded after the first paint). The
 * application renders [loading]/[error] and feeds [items] to `ForEach`.
 */
class AsyncDataSource<T> private constructor(
    private val blockingLoader: ListLoader<T>?,
    private val asyncLoader: AsyncListLoader<T>?,
    private val executor: Executor,
) : AutoCloseable {

    private val context: SessionContext? = SessionContext.current()
    private val closed = AtomicBoolean(false)
    private val generation = AtomicLong(0)

    private val itemsSignal: WritableSignal<List<T>> = Signals.signal(emptyList())
    private val loadingSignal: WritableSignal<Boolean> = Signals.signal(true)
    private val errorSignal: WritableSignal<Throwable?> = Signals.signal(null)

    /** The loaded items (empty until the first successful load). */
    val items: Signal<List<T>> get() = itemsSignal

    /** Whether a load is in flight (starts true). */
    val loading: Signal<Boolean> get() = loadingSignal

    /** The last load failure, or null. */
    val error: Signal<Throwable?> get() = errorSignal

    init {
        context?.onClose { close() }
        if (context?.isLive() ?: true) {
            refresh()
        }
    }

    /** Reload, discarding any stale in-flight result. */
    fun refresh() {
        if (closed.get()) {
            return
        }
        val token = generation.incrementAndGet()
        loadingSignal.set(true)
        errorSignal.set(null)
        val stage: CompletionStage<List<T>> = try {
            val async = asyncLoader
            if (async != null) {
                async.load()
            } else {
                val blocking = checkNotNull(blockingLoader)
                CompletableFuture.supplyAsync({ blocking.load() }, executor)
            }
        } catch (failure: Throwable) {
            applyError(token, failure)
            return
        }
        stage.whenComplete { value, failure ->
            if (failure != null) {
                applyError(token, failure)
            } else {
                applyItems(token, value ?: emptyList())
            }
        }
    }

    private fun applyItems(token: Long, value: List<T>) {
        apply(token) {
            itemsSignal.set(value)
            loadingSignal.set(false)
            errorSignal.set(null)
        }
    }

    private fun applyError(token: Long, failure: Throwable) {
        apply(token) {
            loadingSignal.set(false)
            errorSignal.set(failure)
        }
    }

    private fun apply(token: Long, block: () -> Unit) {
        if (closed.get() || token != generation.get()) {
            return
        }
        val current = context
        if (current != null) {
            current.submit(
                Runnable {
                    if (!closed.get() && token == generation.get()) {
                        block()
                    }
                },
            )
        } else {
            block()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        generation.incrementAndGet()
    }

    companion object {
        /** A blocking loader; runs on [executor]. */
        @JvmStatic
        fun <T> ofBlocking(
            loader: ListLoader<T>,
            executor: Executor = DataExecutors.SHARED_IO,
        ): AsyncDataSource<T> = AsyncDataSource(loader, null, executor)

        /** A `CompletionStage` loader. */
        @JvmStatic
        fun <T> ofAsync(loader: AsyncListLoader<T>): AsyncDataSource<T> =
            AsyncDataSource(null, loader, DataExecutors.SHARED_IO)
    }
}
