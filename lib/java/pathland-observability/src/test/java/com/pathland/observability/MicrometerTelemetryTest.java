package com.pathland.observability;

import com.pathland.server.PathlandTelemetry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The Micrometer adapter: each runtime event lands on the expected {@code pathland.*}
 * meter, tagged by mount, and the active-sessions gauge is pull-based.
 */
class MicrometerTelemetryTest {

    @Test
    void recordsTheFullMetricCatalog() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerTelemetry telemetry = new MicrometerTelemetry(registry);

        telemetry.bindActiveSessions(() -> 3);
        telemetry.sessionOpened("/app");
        telemetry.sessionOpened("/app");
        telemetry.sessionClosed("/app");
        telemetry.eventBatchReceived("/app", 5);
        telemetry.batchFlushed("/app", 2, 7, 128, 1_000_000L);
        telemetry.ssrRendered("/app", 2_000_000L);
        telemetry.resyncRequested("/app");
        telemetry.connectionFailed("/app", "boom");

        assertEquals(2.0, counter(registry, "pathland.sessions.opened"));
        assertEquals(1.0, counter(registry, "pathland.sessions.closed"));
        assertEquals(5.0, counter(registry, "pathland.events.received"));
        assertEquals(1.0, counter(registry, "pathland.batches.sent"));
        assertEquals(2.0, counter(registry, "pathland.frames.sent"));
        assertEquals(128.0, counter(registry, "pathland.bytes.sent"));
        assertEquals(1.0, counter(registry, "pathland.resync.requested"));
        assertEquals(1.0, counter(registry, "pathland.ws.failures"));
        assertEquals(1, registry.get("pathland.ssr.render").tag("mount", "/app").timer().count());
        assertEquals(1, registry.get("pathland.batch.flush").tag("mount", "/app").timer().count());
        assertEquals(7.0, registry.get("pathland.batch.size").tag("mount", "/app").summary().totalAmount());
        assertEquals(3.0, registry.get("pathland.sessions.active").gauge().value());
    }

    @Test
    void tagsSeriesByMount() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerTelemetry telemetry = new MicrometerTelemetry(registry);

        telemetry.sessionOpened("/a");
        telemetry.sessionOpened("/b");
        telemetry.sessionOpened("/b");

        assertEquals(1.0, counter(registry, "pathland.sessions.opened", "/a"));
        assertEquals(2.0, counter(registry, "pathland.sessions.opened", "/b"));
    }

    private static double counter(SimpleMeterRegistry registry, String name) {
        return counter(registry, name, "/app");
    }

    private static double counter(SimpleMeterRegistry registry, String name, String mount) {
        return registry.get(name).tag("mount", mount).counter().count();
    }
}
