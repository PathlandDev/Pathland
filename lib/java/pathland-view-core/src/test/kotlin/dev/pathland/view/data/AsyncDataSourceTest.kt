package dev.pathland.view.data

import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.Signals
import dev.pathland.view.core.subscription.SessionContext
import dev.pathland.view.core.subscription.Topic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

class AsyncDataSourceTest {

    private class TestContext(private val live: Boolean = true) : SessionContext {
        private val closeTasks = ArrayList<Runnable>()
        private var closed = false

        override fun <T, S> subscribe(topic: Topic<T, S>, subject: S, initial: T): Signal<T> =
            Signals.signal(initial)

        override fun <T, S> unsubscribe(topic: Topic<T, S>, subject: S) = Unit

        override fun submit(task: Runnable) {
            if (!closed) task.run()
        }

        override fun isLive(): Boolean = live

        override fun onClose(task: Runnable) {
            closeTasks.add(task)
        }

        fun close() {
            closed = true
            closeTasks.forEach { it.run() }
            closeTasks.clear()
        }
    }

    private val direct = Executor { it.run() }

    private fun <T> withContext(context: SessionContext, block: () -> T): T {
        val previous = SessionContext.push(context)
        return try {
            block()
        } finally {
            SessionContext.restore(previous)
        }
    }

    @Test
    fun liveLoadAppliesItemsOnTheActor() {
        withContext(TestContext()) {
            val source = AsyncDataSource.ofBlocking({ listOf("a", "b") }, direct)
            assertEquals(listOf("a", "b"), source.items.get())
            assertFalse(source.loading.get())
            assertNull(source.error.get())
            source.close()
        }
    }

    @Test
    fun loadFailureSetsTheErrorAndClearsLoading() {
        withContext(TestContext()) {
            val source = AsyncDataSource.ofBlocking<String>({ error("boom") }, direct)
            assertNotNull(source.error.get())
            assertFalse(source.loading.get())
            assertEquals(emptyList<String>(), source.items.get())
            source.close()
        }
    }

    @Test
    fun refreshReplacesTheItems() {
        withContext(TestContext()) {
            var version = 0
            val source = AsyncDataSource.ofBlocking({ listOf("v${++version}") }, direct)
            assertEquals(listOf("v1"), source.items.get())
            source.refresh()
            assertEquals(listOf("v2"), source.items.get())
            source.close()
        }
    }

    @Test
    fun aStaleResultIsIgnoredAfterRefresh() {
        withContext(TestContext()) {
            val first = CompletableFuture<List<String>>()
            var call = 0
            val source = AsyncDataSource.ofAsync {
                if (call++ == 0) {
                    first
                } else {
                    CompletableFuture.completedFuture(listOf("new"))
                }
            }
            source.refresh()
            assertEquals(listOf("new"), source.items.get())
            first.complete(listOf("old"))
            assertEquals(listOf("new"), source.items.get())
            source.close()
        }
    }

    @Test
    fun ssrStartsLoadingAndDoesNotRunTheLoader() {
        withContext(TestContext(live = false)) {
            var calls = 0
            val source = AsyncDataSource.ofBlocking({ calls++; listOf("a") }, direct)
            assertEquals(0, calls)
            assertTrue(source.loading.get())
            assertEquals(emptyList<String>(), source.items.get())
            source.close()
        }
    }

    @Test
    fun sessionCloseClosesTheSource() {
        val context = TestContext()
        withContext(context) {
            var calls = 0
            val source = AsyncDataSource.ofBlocking({ calls++; listOf("a") }, direct)
            assertEquals(1, calls)
            context.close()
            source.refresh()
            assertEquals(1, calls)
        }
    }

    @Test
    fun noSessionContextAppliesInline() {
        val source = AsyncDataSource.ofBlocking({ listOf("a") }, direct)
        assertEquals(listOf("a"), source.items.get())
        assertFalse(source.loading.get())
        source.close()
    }
}
