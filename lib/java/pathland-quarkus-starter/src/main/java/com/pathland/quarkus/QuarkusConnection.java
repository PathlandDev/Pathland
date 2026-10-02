package com.pathland.quarkus;

import com.pathland.server.PathlandConnection;
import io.quarkus.websockets.next.WebSocketConnection;
import io.vertx.core.buffer.Buffer;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adapts a Quarkus {@link WebSocketConnection} to the transport-agnostic {@link PathlandConnection}.
 *
 * <p>Sends are **serialized**: at most one {@code sendBinary} subscription is in
 * flight, later sends queue behind it, so rapid delta batches cannot race the
 * connection's send path. A **send failure closes the connection** — the session
 * then sees {@code isOpen() == false}, drops the connection, and the DOM client
 * reconnects + requests a {@code META::RESYNC}. Without this, a silently failing
 * send leaves the WebSocket "open" while the server delivers nothing (the remote
 * seekbar symptom: the browser suddenly stops receiving deltas).
 *
 * <p>Two keep-alive mechanisms guard against the silent-stall case:
 * <ul>
 *   <li><b>Send timeout</b> — a {@code sendBinary} that neither completes nor
 *       fails within {@value #SEND_TIMEOUT_MILLIS} ms is a half-dead connection;
 *       it is marked failed and closed so the client reconnects instead of
 *       waiting forever (a stalled send would otherwise wedge {@code sending}
 *       and silently queue every future delta).</li>
 *   <li><b>Protocol pings</b> — the connection sends a WebSocket ping frame
 *       every {@value #PING_INTERVAL_MILLIS} ms; browsers auto-answer, which
 *       keeps the connection warm through middleboxes and lets the server detect
 *       a dead client. These are WS-protocol pings, not the app-level
 *       {@code META::PING} the client sends (spec/OPCODE.md §Transport heartbeat).</li>
 * </ul>
 */
final class QuarkusConnection implements PathlandConnection, AutoCloseable {

    /** A send that has not completed (or failed) in this window is a dead connection. */
    static final long SEND_TIMEOUT_MILLIS = 10_000;
    /** Cadence of the WS-protocol ping frames (keep-alive + dead-client detection). */
    static final long PING_INTERVAL_MILLIS = 15_000;

    private final WebSocketConnection connection;
    private final ConcurrentLinkedQueue<byte[]> pending = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean sending = new AtomicBoolean(false);
    private final AtomicBoolean failed = new AtomicBoolean(false);
    private final long sendTimeoutMillis;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "pathland-ws-keepalive");
        t.setDaemon(true);
        return t;
    });
    /** A token per send attempt: a late completion after a timeout must not resume draining. */
    private final AtomicLong sendToken = new AtomicLong();

    QuarkusConnection(WebSocketConnection connection) {
        this(connection, SEND_TIMEOUT_MILLIS, PING_INTERVAL_MILLIS);
    }

    /** Test seam: explicit timeouts (the production values are the static constants). */
    QuarkusConnection(WebSocketConnection connection, long sendTimeoutMillis, long pingIntervalMillis) {
        this.connection = connection;
        this.sendTimeoutMillis = sendTimeoutMillis;
        scheduler.scheduleWithFixedDelay(
                this::sendProtocolPing, pingIntervalMillis, pingIntervalMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void send(byte[] bytes) {
        if (failed.get() || !connection.isOpen()) {
            return;
        }
        pending.add(bytes);
        drain();
    }

    @Override
    public boolean isOpen() {
        return !failed.get() && connection.isOpen();
    }

    private void drain() {
        if (!sending.compareAndSet(false, true)) {
            return;
        }
        byte[] next = pending.poll();
        if (next == null) {
            sending.set(false);
            return;
        }
        long token = sendToken.incrementAndGet();
        scheduleSendTimeout(token);
        connection.sendBinary(next).subscribe().with(
                ignored -> onSendComplete(token, null),
                failure -> onSendComplete(token, failure));
    }

    private void onSendComplete(long token, Throwable failure) {
        if (token != sendToken.get()) {
            return; // a timed-out (abandoned) attempt completing late — ignore
        }
        sending.set(false);
        if (failure != null) {
            fail("send failed: " + failure.getMessage());
            return;
        }
        drain();
    }

    /** A send that is still in flight after the timeout window is a dead connection. */
    private void scheduleSendTimeout(long token) {
        scheduler.schedule(() -> {
            if (token == sendToken.get() && sending.get()) {
                fail("send timed out after " + sendTimeoutMillis + " ms");
            }
        }, sendTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    /** WS-protocol ping (browsers auto-pong): keep-alive + detect a dead client.
     *  Serialized with {@code sendBinary} through the same {@code sending} flag —
     *  quarkus-websockets-next allows ONE message in flight per connection, so an
     *  unsynchronized ping would be rejected while a delta send is in flight (and
     *  that rejection would close a healthy connection). When a send is in flight
     *  or data is queued, this cadence is skipped; the next one retries. */
    private void sendProtocolPing() {
        if (failed.get() || !connection.isOpen()) {
            return;
        }
        if (!sending.compareAndSet(false, true)) {
            return; // a sendBinary is in flight — skip (avoids the concurrent-send rejection)
        }
        if (!pending.isEmpty()) {
            sending.set(false);
            return; // queued data will drain — let drain own the wire; skip the ping
        }
        long token = sendToken.incrementAndGet();
        scheduleSendTimeout(token);
        connection.sendPing(Buffer.buffer(8)).subscribe().with(
                ignored -> onSendComplete(token, null),
                failure -> onSendComplete(token, failure));
    }

    /** Surface a failure: mark failed and close so the session drops us and the
     *  client reconnects + re-syncs (recoverable, not a silent stall). */
    private void fail(String reason) {
        if (!failed.compareAndSet(false, true)) {
            return;
        }
        connection.close().subscribe().with(ignored -> { }, ignored -> { });
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        fail("connection closed");
    }
}