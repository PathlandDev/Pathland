package dev.pathland.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The per-connection inbound budget protects the single-threaded session actor. */
class InboundRateLimiterTest {

    @Test
    void capsBatchesPerWindowPerConnection() {
        InboundRateLimiter limiter = new InboundRateLimiter(2, 1000);
        assertTrue(limiter.allow("a", 1));
        assertTrue(limiter.allow("a", 1));
        assertFalse(limiter.allow("a", 1), "the third batch in the window is dropped");
        assertTrue(limiter.allow("b", 1), "another connection has its own budget");
        assertEquals(2, limiter.tracked());
    }

    @Test
    void capsBytesPerWindow() {
        InboundRateLimiter limiter = new InboundRateLimiter(100, 10);
        assertTrue(limiter.allow("a", 6));
        assertFalse(limiter.allow("a", 6), "the byte budget is exhausted");
    }

    @Test
    void removeForgetsAClosedConnection() {
        InboundRateLimiter limiter = new InboundRateLimiter(1, 1000);
        assertTrue(limiter.allow("a", 1));
        assertFalse(limiter.allow("a", 1));
        limiter.remove("a");
        assertEquals(0, limiter.tracked());
        assertTrue(limiter.allow("a", 1), "a reconnected id gets a fresh window");
    }
}
