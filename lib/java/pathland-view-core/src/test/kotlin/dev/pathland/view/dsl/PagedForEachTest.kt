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
import dev.pathland.view.data.Page
import dev.pathland.view.data.PagedDataSource
import dev.pathland.view.engine.Emitter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.ArrayDeque
import java.util.concurrent.Executor

class PagedForEachTest {

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

        /** Run the applies queued at call time (not ones enqueued by their re-renders). */
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

    private fun texts(frame: ProtocolFrame): List<String> =
        frame.opcodes()
            .filter { it.category() == Categories.PARAMETER && it.command() == Commands.Parameter.SET_TEXT }
            .map { frame.stringAt(it.b()) }

    private fun property(frame: ProtocolFrame, id: Int): Int? =
        frame.opcodes()
            .firstOrNull {
                it.category() == Categories.PARAMETER &&
                    it.command() == Commands.Parameter.SET_PROPERTY &&
                    (it.b() and 0xffff) == id
            }
            ?.c()

    @Test
    fun pagedForEachLoadsTheVisiblePagesAndRendersPlaceholders() {
        val context = QueueContext()
        val previous = SessionContext.push(context)
        try {
            var calls = 0
            val source = PagedDataSource.ofBlocking(2, { offset, limit ->
                calls++
                val end = minOf(offset + limit, 5)
                Page((offset until end).map { "i$it" }, total = 5)
            }, Executor { it.run() })

            val sink = FrameOpcodeSink()
            val emitter = Emitter(sink)
            emitter.mount(
                LazyVStack(spacing = 0f) {
                    ForEach(source, itemExtent = 44f) { item, _ ->
                        Text(item).frame(height = 44f)
                    }
                },
                Environment.DEFAULT,
            )
            sink.frame() // the mount (count 0)

            // The first page (i0, i1) arrives; rows 2..4 are placeholders.
            context.drainOnce()
            val first = sink.frame()
            assertEquals(5, property(first, Properties.ITEM_COUNT))
            assertEquals(listOf("i0", "i1"), texts(first))
            assertEquals(null, source.row(2))

            // The next pages fill the placeholders.
            context.drainOnce()
            assertEquals("i2", source.row(2))
            assertEquals("i4", source.row(4))
            assertEquals(listOf("i4"), texts(sink.frame()))
            assertEquals(3, calls)
            context.close()
        } finally {
            SessionContext.restore(previous)
        }
    }
}
