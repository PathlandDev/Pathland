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
import java.util.concurrent.ConcurrentHashMap;

/**
 * A mounted (non-root) app's live-updates WebSocket endpoint at
 * {@code /<app>/_pathland/ws} — the framework base the SSR page emits for that app, which
 * the DOM client joins as {@code {base}/ws}. The mount path is a single path segment
 * ({@code {app}}); {@link PathlandHost#registry} resolves it. Sessions are 1:1 per
 * connection: each connection gets a fresh {@code uiId} (its UI model), while the client's
 * per-window id ({@code ?wid=…} on the WS URL) is the persisted-state scope. The session
 * logic lives in the framework-agnostic {@link PathlandRegistry}.
 *
 * <p>quarkus-websockets-next endpoints default to {@code @Singleton} — ONE shared
 * instance for all connections — so this endpoint is **stateless**: the app registry,
 * the connection ids, and the {@link QuarkusConnection} adapters are all resolved from
 * the current connection (or a map keyed by connection id), never memoized on the
 * shared instance (which would collapse every connection onto one session/app).
 */
@WebSocket(path = "/{app}/_pathland/ws")
public class MountedPathlandSocket {

    @Inject
    WebSocketConnection connection;

    @Inject
    PathlandHost host;

    /** Per-connection transport adapters (the endpoint instance is shared by all connections). */
    private final ConcurrentHashMap<String, QuarkusConnection> transports = new ConcurrentHashMap<>();

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
        QuarkusConnection transport = new QuarkusConnection(resolved);
        // Key by the injected proxy's id — the SAME id onBinary/close use.
        String id = connection.id();
        transports.put(id, transport);
        r.open(id, resolveWindowId(resolved), transport);
    }

    @OnClose
    void close() {
        String id = connection.id();
        QuarkusConnection transport = transports.remove(id);
        if (transport != null) {
            transport.close(); // stop the keep-alive scheduler + mark failed
        }
        PathlandRegistry r = registry();
        if (r != null) {
            r.close(id);
        }
    }

    @OnBinaryMessage
    void onBinary(byte[] message) {
        String id = connection.id();
        PathlandRegistry r = registry();
        if (r == null) {
            return;
        }
        if (FrameCodec.isResync(message)) {
            r.resync(id);
        } else if (FrameCodec.isPing(message)) {
            // A transport-liveness heartbeat probe (guest → host): reply PONG at the
            // transport layer — no actor/session dependency (spec/OPCODE.md §Transport heartbeat).
            QuarkusConnection t = transports.get(id);
            if (t == null) {
                System.out.println("[pathland] ws ping: no transport for connection " + id);
            } else {
                t.send(FrameCodec.encodePong());
            }
        } else if (FrameCodec.isEnvironment(message)) {
            // The DOM client's FIRST message: seeds the session (created lazily) from the
            // ROUTE field; later messages enrich the environment (viewport, …).
            r.environment(id, FrameCodec.decodeEnvironment(message));
        } else {
            r.dispatch(id, message);
        }
    }

    /** The app's registry from the {@code {app}} path param (resolved per connection). */
    private PathlandRegistry registry() {
        return host.registry("/" + connection.pathParam("app"));
    }

    /** The {@code wid} from the handshake query string (the client's per-window id), else a fresh id. */
    private static String resolveWindowId(WebSocketConnection conn) {
        String query = conn.handshakeRequest().query();
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