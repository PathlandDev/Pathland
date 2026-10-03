package com.pathland.quarkus;

import com.pathland.server.PathlandConnection;
import io.quarkus.websockets.next.WebSocketConnection;
import io.vertx.core.buffer.Buffer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
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
 *
 * <p>Failure handling is deliberately conservative: a transient write hiccup on a
 * connection that still reports open is logged (not a close) — only a confirmed-dead
 * channel (write failure / ping failure / a hung send past the timeout while the
 * channel is closed) warrants closing. This avoids killing a healthy-but-flaky
 * connection on a single momentary failure.
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
    /** Per-attempt timeout tasks, cancelled when the send completes (a completed send
     *  whose callback is delayed must never be false-timed-out and closed). */
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> sendTimeouts = new ConcurrentHashMap<>();

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
        if (failed.get() || !sending.compareAndSet(false, true)) {
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
        ScheduledFuture<?> timeout = sendTimeouts.remove(token);
        if (timeout != null) {
            timeout.cancel(false); // the send completed: never let its timeout fire
        }
        if (token != sendToken.get()) {
            return; // a timed-out (abandoned) attempt completing late — ignore
        }
        sending.set(false);
        if (failed.get()) {
            return;
        }
        if (failure != null) {
            // A write failure while the channel still reports open is a transient
            // hiccup — log and let the next send retry, don't kill the connection.
            if (!connection.isOpen()) {
                fail("send failed on closed connection: " + failure.getMessage());
            } else {
                log("ws send failed (transient, connection open): " + failure.getMessage());
            }
            return;
        }
        drain();
    }

    /** A send that is still in flight after the timeout window is a dead connection. */
    private void scheduleSendTimeout(long token) {
        ScheduledFuture<?> task = scheduler.schedule(() -> {
            sendTimeouts.remove(token);
            if (token == sendToken.get() && sending.get()) {
                fail("send timed out after " + sendTimeoutMillis + " ms");
            }
        }, sendTimeoutMillis, TimeUnit.MILLISECONDS);
        sendTimeouts.put(token, task);
    }

    /** WS-protocol ping (browsers auto-pong): keep-alive + detect a dead client.
     *  Vert.x serializes writes on the event loop, so the ping needs no sync with
     *  {@code sendBinary}. A ping failure is only fatal when the channel is actually
     *  closed; a transient write hiccup on an open connection is logged and retried
     *  on the next cadence (this is the keep-alive probe, not a liveness verdict). */
    private void sendProtocolPing() {
        if (failed.get() || !connection.isOpen()) {
            return;
        }
        connection.sendPing(Buffer.buffer(8)).subscribe().with(
                ignored -> { },
                failure -> {
                    if (!connection.isOpen()) {
                        fail("ping failed on closed connection: " + failure.getMessage());
                    } else {
                        log("ws ping failed (transient, connection open): " + failure.getMessage());
                    }
                });
    }

    /** Surface a failure: mark failed and close so the session drops us and the
     *  client reconnects + re-syncs (recoverable, not a silent stall). */
    private void fail(String reason) {
        if (!failed.compareAndSet(false, true)) {
            return;
        }
        log("ws connection failed: " + reason);
        connection.close().subscribe().with(ignored -> { }, ignored -> { });
    }

    @Override
    public void close() {
        // The socket's @OnClose already closed the underlying connection: just stop
        // the keep-alive scheduler and refuse further sends (no close()/log here —
        // a normal client disconnect is not a failure).
        scheduler.shutdownNow();
        failed.set(true);
    }

    private static void log(String message) {
        System.out.println("[pathland] " + message);
    }
}