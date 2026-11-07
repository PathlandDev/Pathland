package dev.pathland.server

import dev.pathland.view.core.Environment
import dev.pathland.view.core.Platform
import dev.pathland.view.core.View
import dev.pathland.view.core.protocol.Opcode
import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.Signals
import dev.pathland.view.core.state.InMemoryStateStore
import dev.pathland.view.core.subscription.GlobalSubject
import dev.pathland.view.core.subscription.Topic
import dev.pathland.view.core.transport.EnvironmentData
import dev.pathland.view.core.transport.FrameCodec
import dev.pathland.view.core.transport.ProtocolFrame
import dev.pathland.view.dsl.Component
import dev.pathland.view.dsl.Text
import dev.pathland.view.dsl.computed
import dev.pathland.view.dsl.topic
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Topic subscriptions: strongly typed `(topic, subject)` routing, actor-confined
 * application, coalescing, and close/unsubscribe cleanup.
 */
class TopicsTest {

    private val name: Topic<String, Long> = Topic.of("test.customer.name")
    private val globalEvents: Topic<Long, GlobalSubject> = Topic.global("test.events")

    @Test
    fun publishTargetsOnlyTheSubscribedSubject() {
        val registry = registry(::probe)
        val customer42 = RecordingConnection()
        val customer43 = RecordingConnection()
        open(registry, "u42", "w42", "/42", customer42)
        open(registry, "u43", "w43", "/43", customer43)
        await { customer42.has("initial-42") && customer43.has("initial-43") }

        registry.signalHub().publish(name, 42L, "Alice")

        await { customer42.has("Alice") }
        Thread.sleep(100)
        assertFalse(customer43.has("Alice"), "customer 43 must not receive customer 42's update")
        assertTrue(customer43.has("initial-43"))
        assertEquals(1, registry.signalHub().subscriberCount(name, 42L))
        assertEquals(1, registry.signalHub().subscriberCount(name, 43L))
        registry.shutdown()
    }

    @Test
    fun everySessionForTheSameSubjectUpdates() {
        val registry = registry(::probe)
        val first = RecordingConnection()
        val second = RecordingConnection()
        open(registry, "a", "wa", "/42", first)
        open(registry, "b", "wb", "/42", second)
        await { first.has("initial-42") && second.has("initial-42") }

        registry.signalHub().publish(name, 42L, "Alice")

        await { first.has("Alice") && second.has("Alice") }
        assertEquals(2, registry.signalHub().subscriberCount(name, 42L))
        registry.shutdown()
    }

    @Test
    fun rapidPublishesCoalesceToTheLatestValue() {
        val registry = registry(::probe)
        val conn = RecordingConnection()
        open(registry, "u42", "w42", "/42", conn)
        await { conn.has("initial-42") }

        for (i in 1..200) {
            registry.signalHub().publish(name, 42L, "v$i")
        }

        await { conn.has("v200") }
        val applied = conn.texts().filter { it.startsWith("v") }.map { it.removePrefix("v").toInt() }
        assertTrue(applied.zipWithNext().all { (a, b) -> a <= b }, "values never go backwards: $applied")
        assertFalse(applied.any { it > 200 })
        registry.shutdown()
    }

    @Test
    fun updatesAreAppliedOnTheSessionActor() {
        val threads = CopyOnWriteArrayList<String>()
        val registry = registry { subjectProbe { threads.add(Thread.currentThread().name) } }
        val conn = RecordingConnection()
        open(registry, "u42", "w42", "/42", conn)
        await { conn.has("initial-42") }

        registry.signalHub().publish(name, 42L, "Alice")
        await { conn.has("Alice") }

        assertTrue(threads.isNotEmpty())
        assertTrue(
            threads.all { it.startsWith("pathland-actor") },
            "all renders must run on the session actor, saw $threads",
        )
        registry.shutdown()
    }

    @Test
    fun closingASessionRemovesItsSubscriptions() {
        val registry = registry(::probe)
        val conn = RecordingConnection()
        open(registry, "u42", "w42", "/42", conn)
        await { conn.has("initial-42") }
        assertEquals(1, registry.signalHub().subscriberCount(name, 42L))

        registry.close("u42")
        await { registry.signalHub().subscriberCount(name, 42L) == 0 }

        registry.signalHub().publish(name, 42L, "after-close")
        Thread.sleep(50)
        assertFalse(conn.has("after-close"))
        registry.shutdown()
    }

    @Test
    fun unsubscribeStopsRoutingAndKeepsTheLastValue() {
        val hub = PathlandSignalHub("/", PathlandTelemetry.NOOP)
        val subscriptions = SessionSubscriptions(
            hub,
            Consumer { it.run() },
            PathlandTelemetry.NOOP,
            "/",
        )
        val signal: Signal<String> = subscriptions.subscribe(name, 42L, "initial")
        assertEquals(1, hub.subscriberCount(name, 42L))

        hub.publish(name, 42L, "Alice")
        assertEquals("Alice", signal.get())

        subscriptions.unsubscribe(name, 42L)
        assertEquals(0, hub.subscriberCount(name, 42L))
        hub.publish(name, 42L, "ignored")
        assertEquals("Alice", signal.get())
        subscriptions.close()
    }

    @Test
    fun fieldDeclaredSubscriptionResolvesDuringMount() {
        val registry = registry {
            object : Component() {
                private val nameSignal = topic(name, 42L, "field-42")

                override fun body(): View = Text(nameSignal)
            }
        }
        val conn = RecordingConnection()
        open(registry, "u42", "w42", "/42", conn)
        await { conn.has("field-42") }

        registry.signalHub().publish(name, 42L, "Alice")
        await { conn.has("Alice") }
        registry.shutdown()
    }

    @Test
    fun globalTopicReachesOnlySubscribedSessions() {
        val registry = registry(::globalProbe)
        val first = RecordingConnection()
        val second = RecordingConnection()
        val skipped = RecordingConnection()
        open(registry, "a", "wa", "/subscribe", first)
        open(registry, "b", "wb", "/subscribe", second)
        open(registry, "c", "wc", "/skip", skipped)
        await {
            first.has("events-0") && second.has("events-0") && skipped.has("no-events")
        }

        registry.signalHub().publish(globalEvents, 5L)

        await { first.has("events-5") && second.has("events-5") }
        Thread.sleep(100)
        assertFalse(skipped.has("events-5"), "an unsubscribed session must not receive a global publish")
        assertTrue(skipped.has("no-events"))
        assertEquals(2, registry.signalHub().subscriberCount(globalEvents, GlobalSubject))
        registry.shutdown()
        assertEquals(0, registry.signalHub().subscriberCount(globalEvents, GlobalSubject))
    }

    @Test
    fun globalAndScopedTopicsWithTheSameNameAreIsolated() {
        val global = Topic.global<Long>("test.shared.name")
        val scoped = Topic.of<Long, Long>("test.shared.name")
        val registry = registry { sameNameProbe(global, scoped) }
        val globalConn = RecordingConnection()
        val scopedConn = RecordingConnection()
        open(registry, "g", "wg", "/global", globalConn)
        open(registry, "s", "ws", "/scoped", scopedConn)
        await { globalConn.has("g-0") && scopedConn.has("s-0") }

        registry.signalHub().publish(global, 1L)
        await { globalConn.has("g-1") }
        Thread.sleep(100)
        assertFalse(scopedConn.has("s-1"), "the scoped subscriber must not see the global publish")
        assertTrue(scopedConn.has("s-0"))

        registry.signalHub().publish(scoped, 42L, 2L)
        await { scopedConn.has("s-2") }
        Thread.sleep(100)
        assertFalse(globalConn.has("g-2"), "the global subscriber must not see the scoped publish")
        assertTrue(globalConn.has("g-1"))
        registry.shutdown()
    }

    @Test
    fun topicOutsideASessionResolvesLazilyAndFailsOnRead() {
        val signal = Signals.topic(name, 42L, "initial")
        assertThrows(IllegalStateException::class.java) { signal.get() }
    }

    private fun registry(app: () -> View): PathlandRegistry =
        PathlandRegistry({ app() }, InMemoryStateStore())

    private fun open(
        registry: PathlandRegistry,
        uiId: String,
        windowId: String,
        route: String,
        connection: RecordingConnection,
    ) {
        registry.open(uiId, windowId, connection)
        registry.environment(uiId, EnvironmentData.of(route))
        registry.resync(uiId)
    }

    /** A text view bound to `test.customer.name` for the customer id in the route. */
    private fun probe(): View = subjectProbe()

    private fun subjectProbe(onRender: () -> Unit = {}): View = object : Component() {
        override fun body(): View {
            val id = subject()
            val nameSignal = topic(name, id, "initial-$id")
            onRender()
            return Text(nameSignal)
        }
    }

    private fun Component.subject(): Long =
        Environment.value(Platform.ACTIVE_PATH).get().trim('/').toLongOrNull() ?: 0L

    private fun await(timeoutMillis: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        while (!condition() && System.nanoTime() < deadline) {
            Thread.sleep(5)
        }
        assertTrue(condition(), "condition not met within ${timeoutMillis}ms")
    }

    private fun globalProbe(): View = object : Component() {
        override fun body(): View {
            return if (Environment.value(Platform.ACTIVE_PATH).get() == "/subscribe") {
                val events = topic(globalEvents, 0L)
                Text(computed { "events-${events.get()}" })
            } else {
                Text("no-events")
            }
        }
    }

    private fun sameNameProbe(
        global: Topic<Long, GlobalSubject>,
        scoped: Topic<Long, Long>,
    ): View = object : Component() {
        override fun body(): View = when (Environment.value(Platform.ACTIVE_PATH).get()) {
            "/global" -> {
                val value = topic(global, 0L)
                Text(computed { "g-${value.get()}" })
            }
            "/scoped" -> {
                val value = topic(scoped, 42L, 0L)
                Text(computed { "s-${value.get()}" })
            }
            else -> Text("none")
        }
    }

    /** A connection that records decoded SET_TEXT values. */
    private class RecordingConnection : PathlandConnection {
        private val recorded = CopyOnWriteArrayList<ByteArray>()

        override fun send(bytes: ByteArray) {
            recorded.add(bytes)
        }

        override fun isOpen(): Boolean = true

        fun texts(): List<String> {
            val values = ArrayList<String>()
            for (bytes in recorded) {
                val frame: ProtocolFrame = try {
                    FrameCodec.decodeFrame(bytes)
                } catch (_: RuntimeException) {
                    continue
                }
                for (op: Opcode in frame.opcodes()) {
                    if (op.category() == 0x02 && op.command() == 0x03) { // PARAMETER::SET_TEXT
                        values.add(frame.stringAt(op.b()))
                    }
                }
            }
            return values
        }

        fun has(text: String): Boolean = texts().contains(text)
    }
}
