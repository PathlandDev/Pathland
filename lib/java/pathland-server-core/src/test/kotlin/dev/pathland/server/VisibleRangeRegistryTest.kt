package dev.pathland.server

import dev.pathland.core.protocol.Categories
import dev.pathland.core.protocol.Commands
import dev.pathland.core.protocol.Properties
import dev.pathland.core.transport.EnvironmentData
import dev.pathland.core.transport.Event
import dev.pathland.core.transport.FrameCodec
import dev.pathland.view.core.state.InMemoryStateStore
import dev.pathland.view.data.Page
import dev.pathland.view.data.PagedDataSource
import dev.pathland.view.dsl.ForEach
import dev.pathland.view.dsl.LazyVStack
import dev.pathland.view.dsl.Text
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The registry-level visible-range path: a rapid scrollbar jump must reach the
 * session only as the final window, so a paged source loads the settled pages
 * rather than every intermediate window (ADR-0017).
 */
class VisibleRangeRegistryTest {

    @Test
    fun aFastScrollbarJumpLoadsOnlyTheSettledWindow() {
        val loads = CopyOnWriteArrayList<Int>()
        val source = AtomicReference<PagedDataSource<String>>()
        val app = PathlandApp {
            val paged = PagedDataSource.ofBlocking(10, { offset, limit ->
                loads.add(offset)
                Thread.sleep(10)
                val end = minOf(offset + limit, 1000)
                Page((offset until end).map { "i$it" }, total = 1000)
            })
            source.set(paged)
            LazyVStack(spacing = 0f) {
                ForEach(paged, itemExtent = 20f) { item, _ -> Text(item) }
            }
        }
        val registry = PathlandRegistry(app, InMemoryStateStore())
        try {
            val connection = RecordingConnection()
            registry.open("ui", "w", connection)
            registry.environment("ui", EnvironmentData.of("/"))

            await { source.get()?.loading?.get() == false }
            // The lazy stack is the root view, so its node id is 1.
            val target = 1
            Thread.sleep(200)
            loads.clear()

            // One batch carrying the whole drag, so the coalescer sees every
            // range before its interval expires (the client sends them in
            // separate batches; the latest still wins).
            val ranges = (0..800 step 40).map { Event.visibleRange(target, it, 20) }
            registry.dispatch("ui", FrameCodec.encodeEvents(ranges))

            await { loads.isNotEmpty() }
            Thread.sleep(300)
            assertTrue(loads.isNotEmpty(), "the settled window loads pages")
            assertTrue(loads.contains(800), "the settled window's page is loaded: $loads")
            assertTrue(loads.all { it >= 700 }, "no intermediate window loads pages: $loads")

            registry.resync("ui")
            await { connection.lastFrameTexts().contains("i800") }
            val texts = connection.lastFrameTexts()
            val first = connection.lastFrameFirstItemIndex()
            assertTrue(first != null && first >= 700, "settled FIRST_ITEM_INDEX: got $first")
            assertTrue(texts.contains("i800"), "the settled row renders: $texts")
            assertFalse(texts.contains("i0"), "a stale window row must not render")
        } finally {
            registry.shutdown()
        }
    }

    private fun await(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (!condition() && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }
        assertTrue(condition(), "timed out after $timeoutMillis ms")
    }

    private class RecordingConnection : PathlandConnection {
        private val frames = CopyOnWriteArrayList<ByteArray>()

        override fun send(bytes: ByteArray) {
            frames.add(bytes)
        }

        override fun isOpen(): Boolean = true

        fun lastFrameTexts(): List<String> {
            val frame = frames.lastOrNull()?.let { FrameCodec.decodeFrame(it) } ?: return emptyList()
            return frame.opcodes()
                .filter {
                    it.category() == Categories.PARAMETER &&
                        it.command() == Commands.Parameter.SET_TEXT
                }
                .map { frame.stringAt(it.b()) }
        }

        fun lastFrameFirstItemIndex(): Int? {
            val frame = frames.lastOrNull()?.let { FrameCodec.decodeFrame(it) } ?: return null
            return frame.opcodes()
                .firstOrNull {
                    it.category() == Categories.PARAMETER &&
                        it.command() == Commands.Parameter.SET_PROPERTY &&
                        (it.b() and 0xffff) == Properties.FIRST_ITEM_INDEX
                }
                ?.c()
        }
    }
}
