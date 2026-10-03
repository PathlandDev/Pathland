package com.pathland.spring;

import com.pathland.server.PathlandRegistry;
import com.pathland.view.transport.FrameCodec;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The live-updates WebSocket endpoint for one mounted app, registered at its framework
 * base ({@code /_pathland/ws} for the root mount, {@code /<path>/_pathland/ws} otherwise).
 * Sessions are 1:1 per connection: each connection is given a fresh {@code uiId} (its UI
 * model — two windows of the same browser never share a session), while the client's
 * per-window id ({@code ?wid=…} on the WS URL, kept in {@code sessionStorage}) is the
 * persisted-state scope. The ids are resolved <strong>once per connection</strong> and
 * memoized, so every message (events, close) routes to the same session. The actual session
 * logic lives in the framework-agnostic {@link PathlandRegistry}.
 */
public class PathlandSocket extends AbstractWebSocketHandler {

    private final PathlandRegistry registry;
    private final String path;
    private final Map<WebSocketSession, ConnectionIds> ids = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, SpringConnection> connections = new ConcurrentHashMap<>();

    public PathlandSocket(PathlandRegistry registry) {
        this.registry = registry;
        this.path = registry.base() + "/ws";
    }

    /** The concrete endpoint path this socket is registered at. */
    public String path() {
        return path;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        ConnectionIds connectionIds = resolveIds(session);
        ids.put(session, connectionIds);
        SpringConnection conn = new SpringConnection(session);
        connections.put(session, conn);
        registry.open(connectionIds.uiId(), connectionIds.windowId(), conn);
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        if (message instanceof BinaryMessage binary) {
            byte[] bytes = toByteArray(binary.getPayload());
            if (FrameCodec.isResync(bytes)) {
                registry.resync(uiId(session));
            } else if (FrameCodec.isPing(bytes)) {
                // A transport-liveness heartbeat probe (guest → host): reply PONG at the
                // transport layer — no actor/session dependency (spec/OPCODE.md §Transport
                // heartbeat). Best-effort: a send on a concurrently-closing session must
                // not throw out of handleMessage (Spring would close the connection).
                SpringConnection conn = connections.get(session);
                if (conn != null) {
                    try {
                        conn.send(FrameCodec.encodePong());
                    } catch (RuntimeException ignored) {
                        // the client is gone — the session's own send path will drop it
                    }
                }
            } else if (FrameCodec.isEnvironment(bytes)) {
                // The DOM client's FIRST message: seeds the session (created lazily) from
                // the ROUTE field; later messages enrich the environment (viewport, …).
                registry.environment(uiId(session), FrameCodec.decodeEnvironment(bytes));
            } else {
                registry.dispatch(uiId(session), bytes);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        close(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        close(session);
    }

    private void close(WebSocketSession session) {
        connections.remove(session);
        ConnectionIds connectionIds = ids.remove(session);
        if (connectionIds != null) {
            registry.close(connectionIds.uiId());
        }
    }

    /** The memoized UI-model id for the connection (falling back to a fresh resolve). */
    private String uiId(WebSocketSession session) {
        ConnectionIds connectionIds = ids.get(session);
        if (connectionIds == null) {
            connectionIds = resolveIds(session);
            ids.put(session, connectionIds);
        }
        return connectionIds.uiId();
    }

    /** Resolve the per-connection identity: a fresh {@code uiId} + the client's {@code wid}. */
    private ConnectionIds resolveIds(WebSocketSession session) {
        String uiId = UUID.randomUUID().toString();
        String wid = queryParam(session.getUri(), "wid");
        return new ConnectionIds(uiId, wid == null || wid.isBlank() ? UUID.randomUUID().toString() : wid);
    }

    /** Read a query parameter from the handshake URI (the client's per-window id), else null. */
    private static String queryParam(URI uri, String name) {
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        for (String pair : uri.getQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && name.equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }

    /** Copy a {@link ByteBuffer} into a {@code byte[]} regardless of backing array. */
    private static byte[] toByteArray(ByteBuffer buffer) {
        ByteBuffer duplicate = buffer.duplicate();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }

    /** The per-connection identity: UI model id + window (state-scope) id. */
    private record ConnectionIds(String uiId, String windowId) {
    }
}