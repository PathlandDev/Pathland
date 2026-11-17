package dev.pathland.view.dsl

import dev.pathland.core.protocol.Categories
import dev.pathland.core.protocol.Commands
import dev.pathland.core.protocol.Properties
import dev.pathland.core.transport.FrameOpcodeSink
import dev.pathland.core.transport.ProtocolFrame
import dev.pathland.view.core.Environment
import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.Signals
import dev.pathland.view.core.subscription.SessionContext
import dev.pathland.view.core.subscription.Topic
import dev.pathland.view.data.AsyncPageLoader
import dev.pathland.view.data.Page
import dev.pathland.view.data.PagedDataSource
import dev.pathland.view.engine.Emitter
import dev.pathland.view.engine.VisibleRangeInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.ArrayDeque
import java.util.concurrent.CompletableFuture

/**
 * Fast back-and-forth scrolling over a large paged list. A page that was
 * requested for a window must remain usable when the user returns to that
 * window; otherwise the rows fall back to placeholders ("black"/blank rows)
 * and the page is fetched again.
 */
class PagedFastScrollTest {

    private class QueueContext : SessionContext {
        private val queue = ArrayDeque<Runnable>()
        private val closeTasks = ArrayList<Runnable>()

        override fun <T, S> subscribe(topic: Topic<T, S>, subject: S, initial: T): Signal<T> =
            Signals.signal(initial)

        override fun <T, S> unsubscribe(topic: Topic<T, S>, subject: S) = Unit

        override fun submit(task: Runnable) {
            queue.addLast(task)
        }

        override fun isLive(): Boolean = true

        override fun onClose(task: Runnable) {
            closeTasks.add(task)
        }

        fun drainOnce() {
            repeat(queue.size) {
                queue.removeFirst().run()
            }
        }

        fun close() {
            closeTasks.forEach { it.run() }
            closeTasks.clear()
        }
    }

    /** A page loader whose completions the test controls per request. */
    private class ControlledPages(
        private val total: Int = 1_000,
        private val pageSize: Int = 50,
    ) {
        private val lock = Any()
        private val calls = HashMap<Int, Int>()
        private val pending = HashMap<Int, ArrayDeque<CompletableFuture<Page<String>>>>()

        val loader = AsyncPageLoader<String> { offset, _ ->
            val future = CompletableFuture<Page<String>>()
            synchronized(lock) {
                calls[offset] = (calls[offset] ?: 0) + 1
                pending.getOrPut(offset) { ArrayDeque() }.addLast(future)
            }
            future
        }

        fun complete(offset: Int) {
            val future = synchronized(lock) {
                pending[offset]?.firstOrNull { !it.isDone }
                    ?: error("no pending load for offset $offset")
            }
            future.complete(page(offset))
        }

        fun completeAllPending() {
            val toComplete = ArrayList<Pair<Int, CompletableFuture<Page<String>>>>()
            synchronized(lock) {
                for ((offset, queue) in pending) {
                    for (future in queue) {
                        if (!future.isDone) {
                            toComplete.add(offset to future)
                        }
                    }
                }
            }
            for ((offset, future) in toComplete) {
                future.complete(page(offset))
            }
        }

        fun calls(offset: Int): Int = synchronized(lock) { calls[offset] ?: 0 }

        private fun page(offset: Int): Page<String> {
            val end = minOf(offset + pageSize, total)
            return Page((offset until end).map { "row-$it" }, total = total)
        }
    }

    private class Harness(
        val context: QueueContext,
        val pages: ControlledPages,
        private val pageSize: Int = 50,
    ) {
        lateinit var source: PagedDataSource<String>
        private lateinit var emitter: Emitter
        private lateinit var sink: FrameOpcodeSink
        private lateinit var rangeInput: VisibleRangeInput

        fun mount() {
            source = PagedDataSource.ofAsync(pageSize, pages.loader)
            sink = FrameOpcodeSink()
            emitter = Emitter(sink)
            val result = emitter.mount(
                LazyVStack(spacing = 0f) {
                    ForEach(
                        source,
                        itemExtent = 44f,
                        placeholder = { Text("placeholder-$it") },
                    ) { item, _ ->
                        Text(item).frame(height = 44f)
                    }
                },
                Environment.DEFAULT,
            )
            rangeInput = result.visibleRanges().values.single()
        }

        fun range(first: Int, count: Int) {
            rangeInput.onVisibleRange(first, count)
        }

        fun drain() {
            context.drainOnce()
        }

        fun renderFull(): ProtocolFrame {
            emitter.renderFull()
            return sink.frame()
        }

        fun texts(frame: ProtocolFrame): List<String> =
            frame.opcodes()
                .filter { it.category() == Categories.PARAMETER && it.command() == Commands.Parameter.SET_TEXT }
                .map { frame.stringAt(it.b()) }

        fun property(frame: ProtocolFrame, property: Int): Int? =
            frame.opcodes()
                .firstOrNull {
                    it.category() == Categories.PARAMETER &&
                        it.command() == Commands.Parameter.SET_PROPERTY &&
                        (it.b() and 0xffff) == property
                }
                ?.c()
    }

    @Test
    fun returningToAPreviouslyRequestedWindowUsesTheCachedPage() {
        val pages = ControlledPages()
        val context = QueueContext()
        val previous = SessionContext.push(context)
        try {
            val h = Harness(context, pages)
            h.mount()
            pages.complete(0)
            h.drain()

            // W1: rows 100..119. Pages 50 and 100 are requested; we hold them.
            h.range(100, 20)
            h.drain()

            // W2: move away before page 100 arrives, then let it complete there.
            h.range(500, 20)
            h.drain()
            pages.complete(100)
            h.drain()

            // Return to W1. Page 100 is now needed again; it must come from cache.
            h.range(100, 20)
            h.drain()

            val texts = h.texts(h.renderFull())
            assertTrue(texts.contains("row-100"), "cached row must render, texts=$texts")
            assertTrue(texts.contains("row-119"), "cached row must render, texts=$texts")
            assertFalse(texts.contains("placeholder-100"), "the row must not fall back to a placeholder")
            assertEquals(1, pages.calls(100), "the page must not be fetched again after the window returns")
        } finally {
            context.close()
            SessionContext.restore(previous)
        }
    }

    @Test
    fun aRangeBeforeTheCountDoesNotSnapTheListBackToTheTop() {
        val pages = ControlledPages()
        val context = QueueContext()
        val previous = SessionContext.push(context)
        try {
            val h = Harness(context, pages)
            h.mount()

            // The client scrolls before the first page has established ITEM_COUNT.
            h.range(700, 20)
            h.drain()

            // The count now arrives. The pending range must survive.
            pages.complete(0)
            h.drain()

            val first = h.property(h.renderFull(), Properties.FIRST_ITEM_INDEX)
            assertTrue(first != null && first >= 600, "expected the pending range, got first=$first")

            pages.completeAllPending()
            h.drain()
            val texts = h.texts(h.renderFull())
            assertTrue(texts.contains("row-700"), "the settled row must render, texts=$texts")
            assertFalse(texts.contains("placeholder-700"), "the settled row must not stay a placeholder")
        } finally {
            context.close()
            SessionContext.restore(previous)
        }
    }

    @Test
    fun fastBackAndForthLeavesTheSettledWindowRendered() {
        val pages = ControlledPages()
        val context = QueueContext()
        val previous = SessionContext.push(context)
        try {
            val h = Harness(context, pages)
            h.mount()
            pages.complete(0)
            h.drain()

            for (first in listOf(800, 100, 650, 250, 500)) {
                h.range(first, 20)
                h.drain()
                pages.completeAllPending()
                h.drain()
            }
            pages.completeAllPending()
            h.drain()

            val texts = h.texts(h.renderFull())
            assertTrue(texts.contains("row-500"), "the settled window must render, texts=$texts")
            assertFalse(texts.contains("row-100"), "a stale window must not render, texts=$texts")
        } finally {
            context.close()
            SessionContext.restore(previous)
        }
    }
}
