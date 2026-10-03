package com.pathland.quarkus;

import com.pathland.server.PathlandHost;
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
 * The root app's live-updates WebSocket endpoint at {@code /_pathland/ws} (the framework
 * base of the app mounted at {@code "/"}). Sessions are 1:1 per connection: each
 * connection gets a fresh {@code uiId} (its UI model — two windows of the same browser
 * never share a session), while the client's per-window id ({@code ?wid=…} on the WS URL,
 * kept in {@code sessionStorage}) is the persisted-state scope. The ids are resolved once
 * per connection and memoized. The session logic lives in the framework-agnostic
 * {@link PathlandRegistry}.
 *
 * <p>Mounted apps (non-root) connect at {@code /<app>/_pathland/ws} — see
 * {@link MountedPathlandSocket}.
 */
@WebSocket(path = "/_pathland/ws")
public class PathlandSocket {

    @Inject
    WebSocketConnection connection;

    @Inject
    PathlandHost host;

    private volatile String uiId;
    private volatile String windowId;
    private volatile QuarkusConnection transport;

    @OnOpen
    void open() {
        PathlandRegistry registry = host.registry("/");
        if (registry == null) {
            return;
        }
        // Resolve the concrete connection while the session context is active: the CDI
        // bean is session-scoped, so its client proxy would fail off-thread. ClientProxy
        // unwraps it to the real connection, whose send methods work from any thread.
        WebSocketConnection resolved =
                ClientProxy.unwrap(Arc.container().instance(WebSocketConnection.class).get());
        QuarkusConnection transport = new QuarkusConnection(resolved);
        this.transport = transport;
        registry.open(uiId(), windowId(), transport);
    }

    @OnClose
    void close() {
        QuarkusConnection transport = this.transport;
        this.transport = null;
        if (transport != null) {
            transport.close(); // stop the keep-alive scheduler + mark failed
        }
        PathlandRegistry registry = host.registry("/");
        if (registry != null) {
            registry.close(uiId());
        }
    }

    @OnBinaryMessage
    void onBinary(byte[] message) {
        PathlandRegistry registry = host.registry("/");
        if (registry == null) {
            return;
        }
        if (FrameCodec.isResync(message)) {
            registry.resync(uiId());
        } else if (FrameCodec.isPing(message)) {
            // A transport-liveness heartbeat probe (guest → host): reply PONG at the
            // transport layer — no actor/session dependency, and a PING never creates
            // or wakes a session (spec/OPCODE.md §Transport heartbeat).
            QuarkusConnection t = transport;
            if (t != null) {
                t.send(FrameCodec.encodePong());
            }
        } else if (FrameCodec.isEnvironment(message)) {
            // The DOM client's FIRST message: seeds the session (created lazily) from the
            // ROUTE field; later messages enrich the environment (viewport, …).
            registry.environment(uiId(), FrameCodec.decodeEnvironment(message));
        } else {
            registry.dispatch(uiId(), message);
        }
    }

    /** The per-connection UI-model id (a fresh id memoized once). */
    private String uiId() {
        String current = uiId;
        if (current == null) {
            synchronized (this) {
                current = uiId;
                if (current == null) {
                    current = UUID.randomUUID().toString();
                    uiId = current;
                }
            }
        }
        return current;
    }

    /** The client's per-window id (state scope) from the {@code wid} query param, or a fresh id. */
    private String windowId() {
        String current = windowId;
        if (current == null) {
            synchronized (this) {
                current = windowId;
                if (current == null) {
                    current = resolveWindowId();
                    windowId = current;
                }
            }
        }
        return current;
    }

    /** The {@code wid} from the handshake query string (the client's per-window id), else a fresh id. */
    private String resolveWindowId() {
        String query = connection.handshakeRequest().query();
        if (query != null) {
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2 && "wid".equals(kv[0]) && !kv[1].isBlank()) {
                    return kv[1];
                }
            }
        }
        return UUID.randomUUID().toString();
    }
}