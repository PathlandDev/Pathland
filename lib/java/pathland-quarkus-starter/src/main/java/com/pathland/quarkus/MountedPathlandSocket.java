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
 * A mounted (non-root) app's live-updates WebSocket endpoint at
 * {@code /<app>/_pathland/ws} — the framework base the SSR page emits for that app, which
 * the DOM client joins as {@code {base}/ws}. The mount path is a single path segment
 * ({@code {app}}); {@link PathlandHost#registry} resolves it. Sessions are 1:1 per
 * connection: each connection gets a fresh {@code uiId} (its UI model), while the client's
 * per-window id ({@code ?wid=…} on the WS URL) is the persisted-state scope. The session
 * logic lives in the framework-agnostic {@link PathlandRegistry}.
 */
@WebSocket(path = "/{app}/_pathland/ws")
public class MountedPathlandSocket {

    @Inject
    WebSocketConnection connection;

    @Inject
    PathlandHost host;

    private volatile PathlandRegistry registry;
    private volatile String uiId;
    private volatile String windowId;

    @OnOpen
    void open() {
        PathlandRegistry r = registry();
        if (r == null) {
            return;
        }
        // Resolve the concrete connection while the session context is active: the CDI
        // bean is session-scoped, so its client proxy would fail off-thread. ClientProxy
        // unwraps it to the real connection, whose send methods work from any thread.
        WebSocketConnection resolved =
                ClientProxy.unwrap(Arc.container().instance(WebSocketConnection.class).get());
        r.open(uiId(), windowId(), new QuarkusConnection(resolved));
    }

    @OnClose
    void close() {
        PathlandRegistry r = registry();
        if (r != null) {
            r.close(uiId());
        }
    }

    @OnBinaryMessage
    void onBinary(byte[] message) {
        PathlandRegistry r = registry();
        if (r == null) {
            return;
        }
        if (FrameCodec.isResync(message)) {
            r.resync(uiId());
        } else if (FrameCodec.isEnvironment(message)) {
            // The DOM client's FIRST message: seeds the session (created lazily) from the
            // ROUTE field; later messages enrich the environment (viewport, …).
            r.environment(uiId(), FrameCodec.decodeEnvironment(message));
        } else {
            r.dispatch(uiId(), message);
        }
    }

    /** The app's registry, resolved once per connection from the {@code {app}} path param. */
    private PathlandRegistry registry() {
        PathlandRegistry current = registry;
        if (current == null) {
            synchronized (this) {
                current = registry;
                if (current == null) {
                    current = host.registry("/" + connection.pathParam("app"));
                    registry = current;
                }
            }
        }
        return current;
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