package dev.pathland.server;

import java.util.concurrent.ConcurrentHashMap;

/**
 * A small per-connection fixed-window limiter for inbound protocol messages (event
 * batches, environments, resync requests). It runs before actor work is enqueued, so a
 * chatty or hostile client cannot flood the single-threaded session actor or grow its
 * task queue without bound.
 *
 * <p>Limits are per one-second window and configurable:
 * {@code pathland.max-inbound-batches-per-second} (default 500) and
 * {@code pathland.max-inbound-bytes-per-second} (default 2 MiB).
 */
final class InboundRateLimiter {

    private static final int DEFAULT_MAX_BATCHES_PER_SECOND = 500;
    private static final long DEFAULT_MAX_BYTES_PER_SECOND = 2L * 1024 * 1024;
    private static final long WINDOW_NANOS = 1_000_000_000L;

    private final int maxBatches;
    private final long maxBytes;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    InboundRateLimiter() {
        this(
                Integer.getInteger("pathland.max-inbound-batches-per-second",
                        DEFAULT_MAX_BATCHES_PER_SECOND),
                Long.getLong("pathland.max-inbound-bytes-per-second",
                        DEFAULT_MAX_BYTES_PER_SECOND));
    }

    InboundRateLimiter(int maxBatches, long maxBytes) {
        this.maxBatches = Math.max(1, maxBatches);
        this.maxBytes = Math.max(1L, maxBytes);
    }

    /** Whether one more message of {@code bytes} is allowed for {@code sessionId}. */
    boolean allow(String sessionId, int bytes) {
        Window window = windows.computeIfAbsent(sessionId, id -> new Window(System.nanoTime()));
        synchronized (window) {
            long now = System.nanoTime();
            if (now - window.startNanos > WINDOW_NANOS) {
                window.startNanos = now;
                window.batches = 0;
                window.bytes = 0;
            }
            window.batches++;
            window.bytes += Math.max(0, bytes);
            return window.batches <= maxBatches && window.bytes <= maxBytes;
        }
    }

    /** Forget a closed connection's window. */
    void remove(String sessionId) {
        windows.remove(sessionId);
    }

    /** Forget every connection (registry shutdown). */
    void clear() {
        windows.clear();
    }

    /** Number of tracked connections (tests / leak checks). */
    int tracked() {
        return windows.size();
    }

    private static final class Window {
        long startNanos;
        int batches;
        long bytes;

        Window(long startNanos) {
            this.startNanos = startNanos;
        }
    }
}
