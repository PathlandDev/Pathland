package com.pathland.observability;

import com.pathland.server.PathlandTelemetry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

/**
 * The Micrometer adapter for {@link PathlandTelemetry}: turns the runtime's
 * lifecycle/throughput events into {@code pathland.*} meters (counters, timers, a
 * distribution summary, and a pull-based active-sessions gauge). Works with any
 * Micrometer registry (Prometheus, OTLP, …) and therefore with both the Quarkus
 * and Spring starters.
 *
 * <p>Every meter is tagged by {@code mount} (a bounded set), so per-app series are
 * available. Registrations are idempotent — Micrometer returns the existing meter
 * for the same name + tags — so calling a builder per event is cheap and cannot
 * leak meters.
 *
 * <h2>Metric catalog</h2>
 * <ul>
 *   <li>{@code pathland.sessions.active} — gauge, live sessions (all mounts)</li>
 *   <li>{@code pathland.sessions.opened} / {@code .closed} — counters</li>
 *   <li>{@code pathland.events.received} — counter, decoded inbound events</li>
 *   <li>{@code pathland.batches.sent} / {@code .frames.sent} / {@code .bytes.sent} — counters</li>
 *   <li>{@code pathland.batch.size} — distribution summary (opcodes per batch)</li>
 *   <li>{@code pathland.batch.flush} — timer (merge + encode)</li>
 *   <li>{@code pathland.ssr.render} — timer (SSR render round-trip)</li>
 *   <li>{@code pathland.resync.requested} — counter</li>
 *   <li>{@code pathland.ws.failures} — counter (a send failed; the client reconnects)</li>
 * </ul>
 */
public final class MicrometerTelemetry implements PathlandTelemetry {

    private static final String PREFIX = "pathland";

    private final MeterRegistry registry;

    public MicrometerTelemetry(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void bindActiveSessions(IntSupplier supplier) {
        Gauge.builder(PREFIX + ".sessions.active", supplier, IntSupplier::getAsInt)
                .description("Live Pathland sessions across all mounts")
                .register(registry);
    }

    @Override
    public void sessionOpened(String mount) {
        counter("sessions.opened", mount).increment();
    }

    @Override
    public void sessionClosed(String mount) {
        counter("sessions.closed", mount).increment();
    }

    @Override
    public void eventBatchReceived(String mount, int events) {
        counter("events.received", mount).increment(events);
    }

    @Override
    public void batchFlushed(String mount, int frames, int opcodes, int bytes, long nanos) {
        counter("batches.sent", mount).increment();
        counter("frames.sent", mount).increment(frames);
        counter("bytes.sent", mount).increment(bytes);
        DistributionSummary.builder(PREFIX + ".batch.size")
                .baseUnit("opcodes")
                .tag("mount", mount)
                .register(registry)
                .record(opcodes);
        Timer.builder(PREFIX + ".batch.flush")
                .tag("mount", mount)
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void ssrRendered(String mount, long nanos) {
        Timer.builder(PREFIX + ".ssr.render")
                .tag("mount", mount)
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void resyncRequested(String mount) {
        counter("resync.requested", mount).increment();
    }

    @Override
    public void connectionFailed(String mount, String reason) {
        // The reason is deliberately NOT a tag (unbounded cardinality); it is carried
        // for logging adapters. Failures are rare, so a single per-mount counter suffices.
        counter("ws.failures", mount).increment();
    }

    private Counter counter(String name, String mount) {
        return Counter.builder(PREFIX + "." + name)
                .tag("mount", mount)
                .register(registry);
    }
}
