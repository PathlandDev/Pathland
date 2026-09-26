package com.pathland.quarkus;

import com.pathland.server.PathlandRegistry;
import com.pathland.view.transport.FrameCodec;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.websockets.next.OnBinaryMessage;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Inject;

import java.util.UUID;

/**
 * The live-updates WebSocket endpoint at {@code /ws}. Sessions are 1:1: the session id is
 * carried by the {@code session} cookie set on the SSR page (the browser sends it on the
 * handshake), and each connection owns its own session. The id is resolved once per
 * connection and memoized. The session logic lives in the framework-agnostic
 * {@link PathlandRegistry}.
 */
@WebSocket(path = "/_pathland/ws")
public class PathlandSocket {

    @Inject
    WebSocketConnection connection;

    @Inject
    PathlandRegistry registry;

    private volatile String sessionId;

    @OnOpen
    void open() {
        // Resolve the concrete connection while the session context is active: the CDI
        // bean is session-scoped, so its client proxy would fail off-thread. ClientProxy
        // unwraps it to the real connection, whose send methods work from any thread.
        WebSocketConnection resolved =
                ClientProxy.unwrap(Arc.container().instance(WebSocketConnection.class).get());
        registry.open(sessionId(), new QuarkusConnection(resolved));
    }

    @OnClose
    void close() {
        registry.close(sessionId());
    }

    @OnBinaryMessage
    void onBinary(byte[] message) {
        if (FrameCodec.isResync(message)) {
            registry.resync(sessionId());
        } else if (FrameCodec.isEnvironment(message)) {
            // The DOM client's FIRST message: seeds the session (created lazily) from the
            // ROUTE field; later messages enrich the environment (viewport, …).
            registry.environment(sessionId(), FrameCodec.decodeEnvironment(message));
        } else {
            registry.dispatch(sessionId(), message);
        }
    }

    /** The per-connection session id: the {@code session} cookie, or a fresh id memoized once. */
    private String sessionId() {
        String current = sessionId;
        if (current == null) {
            synchronized (this) {
                current = sessionId;
                if (current == null) {
                    current = resolveSessionId();
                    sessionId = current;
                }
            }
        }
        return current;
    }

    /** The session id from the {@code session} cookie (case-insensitive), else a fresh id. */
    private String resolveSessionId() {
        String cookie = connection.handshakeRequest().header("Cookie");
        if (cookie != null) {
            for (String part : cookie.split(";")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2 && "session".equalsIgnoreCase(kv[0].trim()) && !kv[1].isBlank()) {
                    return kv[1].trim();
                }
            }
        }
        return UUID.randomUUID().toString();
    }
}