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
import java.util.concurrent.Executor

class PagedDataSourceTest {

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
    fun loadsTheVisiblePagesAndTotal() {
        withContext(TestContext()) {
            val source = PagedDataSource.ofBlocking(2, { offset, limit ->
                val end = minOf(offset + limit, 5)
                Page((offset until end).map { "i$it" }, total = 5)
            }, direct)
            source.ensureLoaded(0, 1)
            assertEquals(5, source.total.get())
            assertEquals("i0", source.row(0))
            assertEquals("i1", source.row(1))
            assertNull(source.row(2))
            assertFalse(source.loading.get())
            source.ensureLoaded(2, 4)
            assertEquals("i4", source.row(4))
            source.close()
        }
    }

    @Test
    fun anUnknownTotalGrowsAsPagesArrive() {
        withContext(TestContext()) {
            val source = PagedDataSource.ofBlocking(2, { offset, limit ->
                val end = minOf(offset + limit, 5)
                Page((offset until end).map { "i$it" }, hasMore = end < 5)
            }, direct)
            source.ensureLoaded(0, 1)
            assertEquals(2, source.total.get())
            source.ensureLoaded(2, 3)
            assertEquals(4, source.total.get())
            source.ensureLoaded(4, 5)
            assertEquals(5, source.total.get())
            assertFalse(source.hasMore.get())
            source.close()
        }
    }

    @Test
    fun ssrDoesNotLoadPages() {
        withContext(TestContext(live = false)) {
            var calls = 0
            val source = PagedDataSource.ofBlocking(2, { _, _ ->
                calls++
                Page(listOf("a"), total = 1)
            }, direct)
            source.ensureLoaded(0, 1)
            assertEquals(0, calls)
            assertTrue(source.loading.get())
            assertEquals(0, source.total.get())
            source.close()
        }
    }

    @Test
    fun aFailedPageCanBeRetried() {
        withContext(TestContext()) {
            var fail = true
            val source = PagedDataSource.ofBlocking(2, { offset, limit ->
                if (fail) error("boom")
                Page((offset until offset + limit).map { "i$it" }, total = 10)
            }, direct)
            source.ensureLoaded(0, 1)
            assertNotNull(source.error.get())
            fail = false
            source.ensureLoaded(0, 1)
            assertNull(source.error.get())
            assertEquals("i0", source.row(0))
            source.close()
        }
    }

    @Test
    fun refreshReloadsTheLastWindow() {
        withContext(TestContext()) {
            var version = 0
            val source = PagedDataSource.ofBlocking(2, { offset, limit ->
                Page((offset until offset + limit).map { "v${++version}" }, total = 10)
            }, direct)
            source.ensureLoaded(0, 1)
            assertEquals("v1", source.row(0))
            source.refresh()
            assertEquals("v3", source.row(0))
            source.close()
        }
    }
}
