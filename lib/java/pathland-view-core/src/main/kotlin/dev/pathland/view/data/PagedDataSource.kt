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
 * One page of a paged list. [total] is the full item count when known (an
 * offset/limit source); [hasMore] and [nextCursor] support cursor sources.
 */
data class Page<T>(
    val items: List<T>,
    val total: Int? = null,
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
)

/**
 * A blocking page loader.
 *
 * `load(offset, limit)` returns one page; the source runs it on an executor so
 * the session actor is never blocked. [Page.total] carries the full count for
 * offset sources; null means the count is unknown and grows as pages arrive.
 */
fun interface PageLoader<T> {
    fun load(offset: Int, limit: Int): Page<T>
}

/**
 * A `CompletionStage`-based page loader.
 *
 * The stage may complete on any thread; the source applies the page on the
 * session actor. Use this for REST/WebClient/Mutiny sources; use [PageLoader]
 * for blocking JDBC/JPA sources.
 */
fun interface AsyncPageLoader<T> {
    fun load(offset: Int, limit: Int): CompletionStage<Page<T>>
}

/**
 * A windowed, paged data source for lazy lists. The engine calls
 * [ensureLoaded] for the visible/prefetch window; pages are loaded on an
 * executor and applied on the session actor. Unloaded rows return `null` from
 * [row] and render as placeholders.
 *
 * A live session loads pages on demand; an SSR session starts in the loading
 * state and does not run async work.
 */
class PagedDataSource<T> private constructor(
    val pageSize: Int,
    private val blockingLoader: PageLoader<T>?,
    private val asyncLoader: AsyncPageLoader<T>?,
    private val executor: Executor,
) : AutoCloseable {

    init {
        require(pageSize > 0) { "pageSize must be > 0" }
    }

    private val context: SessionContext? = SessionContext.current()
    private val live: Boolean = context?.isLive() ?: true
    private val closed = AtomicBoolean(false)
    private val generation = AtomicLong(0)

    private val pages = object : LinkedHashMap<Int, List<T>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, List<T>>): Boolean =
            size > MAX_PAGES
    }
    private val inFlight = HashSet<Int>()
    private var lastFirst = -1
    private var lastLast = -1
    /** Whether a page has reported an exact total (offset source) vs a growing count. */
    private var knownTotal = false

    private val totalSignal: WritableSignal<Int> = Signals.signal(0)
    private val loadingSignal: WritableSignal<Boolean> = Signals.signal(true)
    private val errorSignal: WritableSignal<Throwable?> = Signals.signal(null)
    private val hasMoreSignal: WritableSignal<Boolean> = Signals.signal(true)
    private val versionSignal: WritableSignal<Int> = Signals.signal(0)

    /** The full item count (0 until the first page reports it). */
    val total: Signal<Int> get() = totalSignal

    /** Whether the initial page is still loading. */
    val loading: Signal<Boolean> get() = loadingSignal

    /** The last page failure, or null. */
    val error: Signal<Throwable?> get() = errorSignal

    /** Whether more pages exist (cursor/unknown-total sources). */
    val hasMore: Signal<Boolean> get() = hasMoreSignal

    /** Changes when a page arrives or is evicted; read by the paged `ForEach`. */
    internal val version: Signal<Int> get() = versionSignal

    init {
        context?.onClose { close() }
        if (live) {
            // Start the count/first page before the lazy window is known; the
            // engine's prefetch then loads the window's remaining pages.
            ensureLoaded(0, pageSize - 1)
        }
    }

    /** Ensure the pages covering the absolute item range `[first, last]` load. */
    fun ensureLoaded(first: Int, last: Int) {
        if (closed.get() || !live) {
            return
        }
        val safeFirst = first.coerceAtLeast(0)
        val safeLast = last.coerceAtLeast(safeFirst)
        lastFirst = safeFirst
        lastLast = safeLast
        val firstPage = safeFirst / pageSize
        val total = totalSignal.get()
        val lastPage = when {
            // Discover the count from the first page before loading the rest.
            !knownTotal && total <= 0 -> firstPage
            // Unknown/cursor total: the count grows as pages arrive; do not cap.
            !knownTotal -> safeLast / pageSize
            else -> minOf(safeLast / pageSize, (total - 1) / pageSize)
        }
        if (lastPage < firstPage) {
            return
        }
        for (page in firstPage..lastPage) {
            loadPage(page)
        }
    }

    /** The loaded item at [index], or null when its page is not loaded. */
    fun row(index: Int): T? {
        if (index < 0) {
            return null
        }
        val page = pages[index / pageSize] ?: return null
        return page.getOrNull(index % pageSize)
    }

    /** Drop the cache and reload the last requested window. */
    fun refresh() {
        if (closed.get()) {
            return
        }
        generation.incrementAndGet()
        pages.clear()
        inFlight.clear()
        knownTotal = false
        loadingSignal.set(true)
        errorSignal.set(null)
        versionSignal.set(versionSignal.get() + 1)
        if (lastFirst >= 0) {
            ensureLoaded(lastFirst, lastLast)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        generation.incrementAndGet()
        pages.clear()
        inFlight.clear()
    }

    private fun loadPage(page: Int) {
        if (pages.containsKey(page) || !inFlight.add(page)) {
            return
        }
        val token = generation.get()
        val offset = page * pageSize
        val stage: CompletionStage<Page<T>> = try {
            val async = asyncLoader
            if (async != null) {
                async.load(offset, pageSize)
            } else {
                val blocking = checkNotNull(blockingLoader)
                CompletableFuture.supplyAsync({ blocking.load(offset, pageSize) }, executor)
            }
        } catch (failure: Throwable) {
            applyError(token, page, failure)
            return
        }
        stage.whenComplete { value, failure ->
            if (failure != null) {
                applyError(token, page, failure)
            } else {
                applyPage(token, page, value)
            }
        }
    }

    private fun applyPage(token: Long, page: Int, value: Page<T>?) {
        apply(token, page) {
            val result = value ?: Page(emptyList())
            pages[page] = result.items
            val loadedEnd = page * pageSize + result.items.size
            knownTotal = result.total != null
            val nextTotal = result.total ?: maxOf(totalSignal.get(), loadedEnd)
            totalSignal.set(nextTotal)
            hasMoreSignal.set(
                result.hasMore || (result.total != null && nextTotal > loadedEnd),
            )
            loadingSignal.set(false)
            errorSignal.set(null)
            versionSignal.set(versionSignal.get() + 1)
        }
    }

    private fun applyError(token: Long, page: Int, failure: Throwable) {
        apply(token, page) {
            loadingSignal.set(false)
            errorSignal.set(failure)
        }
    }

    private fun apply(token: Long, page: Int, block: () -> Unit) {
        if (closed.get() || token != generation.get()) {
            return
        }
        val current = context
        if (current != null) {
            current.submit(
                Runnable {
                    if (!closed.get() && token == generation.get()) {
                        inFlight.remove(page)
                        block()
                    }
                },
            )
        } else {
            inFlight.remove(page)
            block()
        }
    }

    companion object {
        private const val MAX_PAGES = 20

        /** A blocking page loader; runs on [executor]. */
        @JvmStatic
        fun <T> ofBlocking(
            pageSize: Int,
            loader: PageLoader<T>,
            executor: Executor = DataExecutors.SHARED_IO,
        ): PagedDataSource<T> = PagedDataSource(pageSize, loader, null, executor)

        /** A `CompletionStage` page loader. */
        @JvmStatic
        fun <T> ofAsync(pageSize: Int, loader: AsyncPageLoader<T>): PagedDataSource<T> =
            PagedDataSource(pageSize, null, loader, DataExecutors.SHARED_IO)
    }
}
