package com.pathland.server;

import com.pathland.view.Button;
import com.pathland.view.Text;
import com.pathland.view.View;
import com.pathland.view.emit.ProtocolFrame;
import com.pathland.view.emit.Opcode;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;
import com.pathland.view.state.InMemoryStateStore;
import com.pathland.view.state.StateStore;
import com.pathland.view.transport.EnvironmentData;
import com.pathland.view.transport.Event;
import com.pathland.view.transport.FrameCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The observability seam: the runtime emits session/event/frame/SSR/resync/failure
 * events through {@link PathlandTelemetry}, and the host binds a pull-based
 * active-sessions gauge. A recording telemetry stands in for a Micrometer registry.
 */
class PathlandTelemetryTest {

    private static final StateStore STORE = new InMemoryStateStore();

    @Test
    void registryEmitsLifecycleAndThroughputEvents() {
        RecordingTelemetry telemetry = new RecordingTelemetry();
        PathlandRegistry registry = new PathlandRegistry(
                "/", () -> Button.of("Tap", () -> {}), STORE, false, telemetry);

        // SSR render (synchronous) emits ssrRendered.
        registry.renderHtml("/");
        assertEquals(1, telemetry.ssrRendered.get(), "SSR render is measured");

        // Open + environment creates the session (sessionOpened); resync flushes a
        // snapshot (resyncRequested + batchFlushed); an event batch is counted.
        registry.open("ui", "w1", new NoopConnection());
        registry.environment("ui", EnvironmentData.of("/"));
        registry.dispatch("ui", FrameCodec.encodeEvents(List.of(Event.pointerUp(1, 0, 0))));
        registry.resync("ui");

        await(() -> telemetry.sessionOpened.get() >= 1
                && telemetry.eventBatchReceived.get() >= 1
                && telemetry.resyncRequested.get() >= 1
                && telemetry.batchFlushed.get() >= 1);

        assertEquals(1, telemetry.sessionOpened.get(), "one session opened");
        assertTrue(telemetry.eventBatchReceived.get() >= 1, "inbound events counted");
        assertTrue(telemetry.batchFlushed.get() >= 1, "outbound batch flushed");
        assertTrue(telemetry.lastOpcodes > 0, "batch flush reports opcodes");

        registry.close("ui");
        await(() -> telemetry.sessionClosed.get() >= 1);
        assertEquals(1, telemetry.sessionClosed.get(), "one session closed");
        registry.shutdown();
    }

    @Test
    void hostBindsTheActiveSessionsGauge() {
        RecordingTelemetry telemetry = new RecordingTelemetry();
        PathlandHost host = new PathlandHost(
                List.of(MountedApp.of("/", () -> Button.of("Tap", () -> {}))), STORE, false, telemetry);

        assertNotNull(telemetry.activeSessions, "the host binds the gauge supplier");
        assertEquals(0, telemetry.activeSessions.getAsInt(), "no sessions yet");

        PathlandRegistry registry = host.registry("/");
        registry.open("ui", "w1", new NoopConnection());
        registry.environment("ui", EnvironmentData.of("/"));
        await(() -> host.activeSessions() == 1);
        assertEquals(1, telemetry.activeSessions.getAsInt(), "the gauge reflects the live session");

        host.shutdown();
        await(() -> telemetry.activeSessions.getAsInt() == 0);
    }

    @Test
    void sessionReportsConnectionFailures() {
        RecordingTelemetry telemetry = new RecordingTelemetry();
        PathlandApp app = () -> Button.of("Tap", () -> {});
        PathlandSession session = new PathlandSession(
                "s1", STORE, app, EnvironmentData.of("/"), false, "/_pathland", "s1", telemetry, "/");

        session.connect(new FailingConnection());
        session.resync();

        assertEquals(1, telemetry.connectionFailed.get(), "a failed send is reported");
        assertEquals("send failed", telemetry.lastFailureReason);
        session.close();
    }

    @Test
    void deltaBatcherReportsTheFlushedBatch() {
        RecordingTelemetry telemetry = new RecordingTelemetry();
        List<byte[]> sent = new java.util.ArrayList<>();
        DeltaBatcher batcher = new DeltaBatcher(60_000, 1_000_000, sent::add, telemetry, "/app");

        batcher.append(new ProtocolFrame(List.of(new Opcode(2, 3, 0, 1, 0, 0)), new byte[0]));
        batcher.flush();
        batcher.close();

        assertEquals(1, sent.size());
        assertEquals(1, telemetry.batchFlushed.get());
        assertEquals(1, telemetry.lastFrames);
        assertEquals(1, telemetry.lastOpcodes);
        assertEquals(sent.get(0).length, telemetry.lastBytes);
        assertEquals("/app", telemetry.lastMount);
    }

    private static void await(java.util.function.BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        assertTrue(condition.getAsBoolean(), "condition not met before the deadline");
    }

    /** A recording telemetry double (stands in for a Micrometer registry). */
    private static final class RecordingTelemetry implements PathlandTelemetry {
        final AtomicInteger sessionOpened = new AtomicInteger();
        final AtomicInteger sessionClosed = new AtomicInteger();
        final AtomicInteger eventBatchReceived = new AtomicInteger();
        final AtomicInteger batchFlushed = new AtomicInteger();
        final AtomicInteger ssrRendered = new AtomicInteger();
        final AtomicInteger resyncRequested = new AtomicInteger();
        final AtomicInteger connectionFailed = new AtomicInteger();
        volatile IntSupplier activeSessions;
        volatile int lastFrames;
        volatile int lastOpcodes;
        volatile int lastBytes;
        volatile String lastMount;
        volatile String lastFailureReason;

        @Override
        public void bindActiveSessions(IntSupplier supplier) {
            this.activeSessions = supplier;
        }

        @Override
        public void sessionOpened(String mount) {
            sessionOpened.incrementAndGet();
        }

        @Override
        public void sessionClosed(String mount) {
            sessionClosed.incrementAndGet();
        }

        @Override
        public void eventBatchReceived(String mount, int events) {
            eventBatchReceived.incrementAndGet();
        }

        @Override
        public void batchFlushed(String mount, int frames, int opcodes, int bytes, long nanos) {
            batchFlushed.incrementAndGet();
            lastFrames = frames;
            lastOpcodes = opcodes;
            lastBytes = bytes;
            lastMount = mount;
        }

        @Override
        public void ssrRendered(String mount, long nanos) {
            ssrRendered.incrementAndGet();
        }

        @Override
        public void resyncRequested(String mount) {
            resyncRequested.incrementAndGet();
        }

        @Override
        public void connectionFailed(String mount, String reason) {
            connectionFailed.incrementAndGet();
            lastFailureReason = reason;
        }
    }

    private static final class NoopConnection implements PathlandConnection {
        @Override
        public void send(byte[] bytes) {
            // no-op
        }

        @Override
        public boolean isOpen() {
            return true;
        }
    }

    private static final class FailingConnection implements PathlandConnection {
        @Override
        public void send(byte[] bytes) {
            throw new RuntimeException("send failed");
        }

        @Override
        public boolean isOpen() {
            return true;
        }
    }
}
