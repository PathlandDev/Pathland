package com.pathland.observability;

import com.pathland.server.PathlandTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;

import java.time.Instant;
import java.util.Objects;
import java.util.function.IntSupplier;

/**
 * An OpenTelemetry decorator over another {@link PathlandTelemetry}: every flush,
 * inbound event batch, and SSR render also emits a span, so a trace shows the
 * server-side work behind each frame. The wrapped telemetry still receives the
 * events, so metrics keep flowing when tracing is on.
 *
 * <p>Spans are server-side and self-contained (the runtime's actor/batcher threads
 * are not in a client request's trace context): each is a single-shot span whose
 * start timestamp is back-dated by the measured duration, so it renders with the
 * real latency. Disabled entirely when no {@link Tracer} is available — the
 * starters only build this decorator when the host enables tracing.
 */
public final class TracingTelemetry implements PathlandTelemetry {

    private static final AttributeKey<String> MOUNT = AttributeKey.stringKey("pathland.mount");
    private static final AttributeKey<Long> FRAMES = AttributeKey.longKey("pathland.frames");
    private static final AttributeKey<Long> OPCODES = AttributeKey.longKey("pathland.opcodes");
    private static final AttributeKey<Long> BYTES = AttributeKey.longKey("pathland.bytes");
    private static final AttributeKey<Long> EVENTS = AttributeKey.longKey("pathland.events");

    private final PathlandTelemetry delegate;
    private final Tracer tracer;

    public TracingTelemetry(PathlandTelemetry delegate, Tracer tracer) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.tracer = Objects.requireNonNull(tracer, "tracer");
    }

    @Override
    public void bindActiveSessions(IntSupplier supplier) {
        delegate.bindActiveSessions(supplier);
    }

    @Override
    public void sessionOpened(String mount) {
        delegate.sessionOpened(mount);
    }

    @Override
    public void sessionClosed(String mount) {
        delegate.sessionClosed(mount);
    }

    @Override
    public void eventBatchReceived(String mount, int events) {
        delegate.eventBatchReceived(mount, events);
        Span span = tracer.spanBuilder("pathland.event.batch")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(MOUNT, mount)
                .setAttribute(EVENTS, (long) events)
                .startSpan();
        span.end();
    }

    @Override
    public void batchFlushed(String mount, int frames, int opcodes, int bytes, long nanos) {
        delegate.batchFlushed(mount, frames, opcodes, bytes, nanos);
        Span span = tracer.spanBuilder("pathland.frame.send")
                .setSpanKind(SpanKind.INTERNAL)
                .setStartTimestamp(Instant.now().minusNanos(nanos))
                .setAttribute(MOUNT, mount)
                .setAttribute(FRAMES, (long) frames)
                .setAttribute(OPCODES, (long) opcodes)
                .setAttribute(BYTES, (long) bytes)
                .startSpan();
        span.end();
    }

    @Override
    public void ssrRendered(String mount, long nanos) {
        delegate.ssrRendered(mount, nanos);
        Span span = tracer.spanBuilder("pathland.ssr.render")
                .setSpanKind(SpanKind.INTERNAL)
                .setStartTimestamp(Instant.now().minusNanos(nanos))
                .setAttribute(MOUNT, mount)
                .startSpan();
        span.end();
    }

    @Override
    public void resyncRequested(String mount) {
        delegate.resyncRequested(mount);
    }

    @Override
    public void connectionFailed(String mount, String reason) {
        delegate.connectionFailed(mount, reason);
    }
}
