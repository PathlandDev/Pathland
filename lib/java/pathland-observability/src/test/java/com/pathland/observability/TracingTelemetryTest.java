package com.pathland.observability;

import com.pathland.server.PathlandTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The OTel decorator: frame sends, event batches, and SSR renders each emit a span
 * with the expected name, and the wrapped telemetry still receives every event.
 */
class TracingTelemetryTest {

    @Test
    void emitsSpansAndStillDelegates() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        Tracer tracer = provider.get("pathland-test");

        Recording delegate = new Recording();
        TracingTelemetry telemetry = new TracingTelemetry(delegate, tracer);

        telemetry.batchFlushed("/app", 2, 7, 128, 1_000_000L);
        telemetry.eventBatchReceived("/app", 4);
        telemetry.ssrRendered("/app", 2_000_000L);
        telemetry.sessionOpened("/app");

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertEquals(3, spans.size(), "frame + event + ssr spans");
        assertTrue(spans.stream().anyMatch(s -> s.getName().equals("pathland.frame.send")));
        assertTrue(spans.stream().anyMatch(s -> s.getName().equals("pathland.event.batch")));
        assertTrue(spans.stream().anyMatch(s -> s.getName().equals("pathland.ssr.render")));

        // Every event still reaches the wrapped telemetry (metrics keep flowing).
        assertEquals(1, delegate.batchFlushed);
        assertEquals(1, delegate.eventBatchReceived);
        assertEquals(1, delegate.ssrRendered);
        assertEquals(1, delegate.sessionOpened);

        provider.close();
    }

    private static final class Recording implements PathlandTelemetry {
        int sessionOpened;
        int batchFlushed;
        int eventBatchReceived;
        int ssrRendered;

        @Override
        public void sessionOpened(String mount) {
            sessionOpened++;
        }

        @Override
        public void batchFlushed(String mount, int frames, int opcodes, int bytes, long nanos) {
            batchFlushed++;
        }

        @Override
        public void eventBatchReceived(String mount, int events) {
            eventBatchReceived++;
        }

        @Override
        public void ssrRendered(String mount, long nanos) {
            ssrRendered++;
        }
    }
}
