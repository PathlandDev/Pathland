package com.pathland.quarkus;

import io.quarkus.websockets.next.CloseReason;
import io.quarkus.websockets.next.HandshakeRequest;
import io.quarkus.websockets.next.UserData;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Uni;
import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Quarkus connection seam: serialized sends, a send-timeout that surfaces a
 * half-dead connection (the silent-stall fix), and WS-protocol keep-alive pings.
 */
class QuarkusConnectionTest {

    @Test
    void sendTimeoutClosesTheConnectionWhenASendNeverCompletes() throws Exception {
        FakeWebSocketConnection fake = new FakeWebSocketConnection();
        fake.binaryResult = Uni.createFrom().nothing(); // never completes, never fails

        QuarkusConnection conn = new QuarkusConnection(fake, /*sendTimeout*/ 200, /*pingInterval*/ 60_000);
        conn.send(new byte[] {1, 2, 3});

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!fake.closed && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        conn.close();

        assertTrue(fake.closed, "a hung send times out and closes the connection");
        assertFalse(conn.isOpen(), "a failed connection is not open");
        assertEquals(1, fake.sendBinaryCount.get(), "the send was attempted");
        // A late completion after the timeout must not resume draining (no further sends).
        int after = fake.sendBinaryCount.get();
        Thread.sleep(100);
        assertEquals(after, fake.sendBinaryCount.get(), "a timed-out send never resumes the queue");
    }

    @Test
    void completedSendDrainsTheNextQueuedBatch() throws Exception {
        FakeWebSocketConnection fake = new FakeWebSocketConnection();
        CountDownLatch twoSent = new CountDownLatch(2);

        QuarkusConnection conn = new QuarkusConnection(fake, 60_000, 60_000);
        fake.sendsUntil = twoSent;
        conn.send(new byte[] {1});
        conn.send(new byte[] {2});
        assertTrue(twoSent.await(2, TimeUnit.SECONDS), "both queued sends drain in order");
        conn.close();
        assertEquals(2, fake.sendBinaryCount.get());
    }

    @Test
    void keepAlivePingIsSentPeriodicallyAndStopsOnClose() throws Exception {
        FakeWebSocketConnection fake = new FakeWebSocketConnection();
        QuarkusConnection conn = new QuarkusConnection(fake, 60_000, /*pingInterval*/ 40);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (fake.pingCount.get() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(fake.pingCount.get() > 0, "the connection sends keep-alive pings");

        conn.close(); // stops the scheduler
        int pings = fake.pingCount.get();
        Thread.sleep(150);
        assertEquals(pings, fake.pingCount.get(), "no pings after close");
    }

    /** A minimal {@link WebSocketConnection} that records sends/pings/closes. */
    private static final class FakeWebSocketConnection implements WebSocketConnection {
        volatile boolean closed;
        volatile Uni<Void> binaryResult = Uni.createFrom().item((Void) null);
        final AtomicInteger sendBinaryCount = new AtomicInteger();
        final AtomicInteger pingCount = new AtomicInteger();
        volatile CountDownLatch sendsUntil;

        @Override
        public Uni<Void> sendBinary(byte[] message) {
            sendBinaryCount.incrementAndGet();
            CountDownLatch latch = sendsUntil;
            if (latch != null) {
                latch.countDown();
            }
            return binaryResult;
        }

        @Override
        public Uni<Void> sendPing(Buffer data) {
            pingCount.incrementAndGet();
            return Uni.createFrom().item((Void) null);
        }

        @Override
        public Uni<Void> close(CloseReason reason) {
            closed = true;
            return Uni.createFrom().item((Void) null);
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public String id() {
            return "fake";
        }

        @Override
        public String pathParam(String name) {
            return null;
        }

        @Override
        public boolean isSecure() {
            return true;
        }

        @Override
        public javax.net.ssl.SSLSession sslSession() {
            return null;
        }

        @Override
        public CloseReason closeReason() {
            return null;
        }

        @Override
        public HandshakeRequest handshakeRequest() {
            return null;
        }

        @Override
        public Instant creationTime() {
            return Instant.now();
        }

        @Override
        public UserData userData() {
            return null;
        }

        @Override
        public String endpointId() {
            return "fake";
        }

        @Override
        public BroadcastSender broadcast() {
            return null;
        }

        @Override
        public Set<WebSocketConnection> getOpenConnections() {
            return Set.of();
        }

        @Override
        public String subprotocol() {
            return null;
        }

        @Override
        public Uni<Void> sendText(String message) {
            return Uni.createFrom().item((Void) null);
        }

        @Override
        public <M> Uni<Void> sendText(M message) {
            return Uni.createFrom().item((Void) null);
        }

        @Override
        public Uni<Void> sendBinary(Buffer message) {
            return Uni.createFrom().item((Void) null);
        }

        @Override
        public Uni<Void> sendPong(Buffer data) {
            return Uni.createFrom().item((Void) null);
        }
    }
}