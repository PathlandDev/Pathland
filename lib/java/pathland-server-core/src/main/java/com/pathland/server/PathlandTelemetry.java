package com.pathland.server;

import java.util.function.IntSupplier;

/**
 * The observability seam: the framework-agnostic runtime emits lifecycle and
 * throughput events here, and a host (a Micrometer adapter, an OTel decorator, a
 * test recorder) turns them into counters, timers, gauges, or spans. The runtime
 * carries a {@link #NOOP} instance by default, so the core has no metrics
 * dependency and a host with no observability configured pays nothing.
 *
 * <p>All methods are non-blocking and MUST NOT throw; the runtime calls them on
 * the actor thread and the batcher scheduler, so an implementation should be
 * cheap (Micrometer's registry is thread-safe and allocation-light).
 *
 * <p>See {@code pathland-observability} for the Micrometer + OpenTelemetry
 * adapters and the metric catalog.
 */
public interface PathlandTelemetry {

    /** A no-op implementation: the default when a host configures no observability. */
    PathlandTelemetry NOOP = new PathlandTelemetry() {};

    /**
     * Bind a pull-based active-session count (a Micrometer gauge). Called once by
     * the host after construction; the supplier is thread-safe.
     */
    default void bindActiveSessions(IntSupplier supplier) {}

    /** A new live session was created (lazily, on its first inbound message). */
    default void sessionOpened(String mount) {}

    /** A live session was closed (client disconnect, replacement, or shutdown). */
    default void sessionClosed(String mount) {}

    /** An inbound event batch was routed to a session ({@code events} decoded events). */
    default void eventBatchReceived(String mount, int events) {}

    /**
     * An outbound delta batch was flushed to the connection.
     *
     * @param frames   emit-pass frames coalesced into the batch
     * @param opcodes  opcodes in the batch
     * @param bytes    encoded batch bytes
     * @param nanos    time spent merging + encoding the batch
     */
    default void batchFlushed(String mount, int frames, int opcodes, int bytes, long nanos) {}

    /** SSR HTML was rendered for a request (the Rust renderer round-trip). */
    default void ssrRendered(String mount, long nanos) {}

    /** A {@code META::RESYNC} full-snapshot request was handled. */
    default void resyncRequested(String mount) {}

    /** A connection send failed and the session dropped it (the client will reconnect). */
    default void connectionFailed(String mount, String reason) {}
}
