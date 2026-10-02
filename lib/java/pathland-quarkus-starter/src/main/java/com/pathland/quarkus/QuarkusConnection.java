package com.pathland.quarkus;

import com.pathland.server.PathlandConnection;
import io.quarkus.websockets.next.WebSocketConnection;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

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
 */
final class QuarkusConnection implements PathlandConnection {

    private final WebSocketConnection connection;
    private final ConcurrentLinkedQueue<byte[]> pending = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean sending = new AtomicBoolean(false);
    private volatile boolean failed = false;

    QuarkusConnection(WebSocketConnection connection) {
        this.connection = connection;
    }

    @Override
    public void send(byte[] bytes) {
        if (failed || !connection.isOpen()) {
            return;
        }
        pending.add(bytes);
        drain();
    }

    @Override
    public boolean isOpen() {
        return !failed && connection.isOpen();
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
        connection.sendBinary(next).subscribe().with(
                ignored -> {
                    sending.set(false);
                    drain();
                },
                failure -> {
                    sending.set(false);
                    failed = true;
                    // Surface the failure: close so the session drops us and the
                    // client reconnects + re-syncs (recoverable, not a silent stall).
                    connection.close().subscribe().with(ignored2 -> {}, ignored2 -> {});
                });
    }
}