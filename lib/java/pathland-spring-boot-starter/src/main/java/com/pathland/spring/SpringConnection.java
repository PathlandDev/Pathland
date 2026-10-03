package com.pathland.spring;

import com.pathland.server.PathlandConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

/** Adapts a Spring {@link WebSocketSession} to the transport-agnostic {@link PathlandConnection}. */
final class SpringConnection implements PathlandConnection, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SpringConnection.class);

    private final WebSocketSession session;
    /** Serializes all sends for this connection: the batcher scheduler thread, the actor
     *  thread (resync) and the WS-handler thread (transport PONG) all write here, and
     *  Tomcat's remote endpoint is not safe for concurrent {@code sendMessage} calls
     *  (a colliding write fails and fires a transport error that closes the session). */
    private final Object sendLock = new Object();

    SpringConnection(WebSocketSession session) {
        this.session = session;
    }

    @Override
    public void send(byte[] bytes) {
        // A send on a closed/closing session is expected during teardown (the client
        // disconnected, or a concurrent transport error closed the session). Skipping it
        // keeps a PONG or a delta from throwing out of a message handler, which Spring's
        // ExceptionWebSocketHandlerDecorator would treat as a handler failure and close
        // the session (killing every subsequent delta for that client).
        if (!session.isOpen()) {
            return;
        }
        synchronized (sendLock) {
            if (!session.isOpen()) {
                return; // closed between the check and the lock
            }
            try {
                session.sendMessage(new BinaryMessage(bytes));
            } catch (Exception e) {
                // A transient write failure on a STILL-OPEN session is logged, not
                // thrown: PathlandSession.sendBatch would otherwise drop the connection
                // and close the WebSocket, permanently severing a healthy session's
                // delta stream over one momentary hiccup (the Quarkus adapter already
                // treats transient failures this way). Only a session that is confirmed
                // closed is surfaced (so sendBatch drops it).
                if (session.isOpen()) {
                    LOG.warn("ws send failed (transient, session open): {}", e.getMessage());
                } else {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Override
    public boolean isOpen() {
        return session.isOpen();
    }

    /** Close the underlying session so a failed send surfaces as a close (the client
     *  reconnects + re-syncs instead of silently stalling on an open socket). */
    @Override
    public void close() {
        try {
            session.close();
        } catch (Exception ignored) {
            // best-effort: the session already dropped this connection
        }
    }
}