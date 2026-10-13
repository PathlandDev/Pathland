package dev.pathland.server;

/**
 * The transport seam (spec DSL.md §4.5): the server runtime sends delta frames through
 * this, so the session logic is framework-agnostic. Each starter adapts its WebSocket
 * type (Spring {@code WebSocketSession}, Quarkus {@code WebSocketConnection}) to it.
 */
public interface PathlandConnection {

    /** Send a self-contained delta/snapshot frame to this connection's client. */
    void send(byte[] bytes);

    /** Whether the connection can currently accept a send. */
    boolean isOpen();
}