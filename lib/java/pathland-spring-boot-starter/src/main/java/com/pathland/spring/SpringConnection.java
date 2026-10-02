package com.pathland.spring;

import com.pathland.server.PathlandConnection;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

/** Adapts a Spring {@link WebSocketSession} to the transport-agnostic {@link PathlandConnection}. */
final class SpringConnection implements PathlandConnection, AutoCloseable {

    private final WebSocketSession session;

    SpringConnection(WebSocketSession session) {
        this.session = session;
    }

    @Override
    public void send(byte[] bytes) {
        try {
            session.sendMessage(new BinaryMessage(bytes));
        } catch (Exception e) {
            throw new RuntimeException(e);
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