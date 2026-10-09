package com.pathland.server;

import com.pathland.render.html.HtmlRenderer;
import com.pathland.view.Environment;
import com.pathland.view.EnvironmentBinding;
import com.pathland.view.Platform;
import com.pathland.view.emit.Emitter;
import com.pathland.view.emit.ProtocolFrame;
import com.pathland.view.emit.FrameOpcodeSink;
import com.pathland.view.emit.InputDispatcher;
import com.pathland.view.emit.RenderResult;
import com.pathland.view.signal.Signals;
import com.pathland.view.signal.WritableSignal;
import com.pathland.view.state.PersistentState;
import com.pathland.view.state.StateStore;
import com.pathland.view.transport.EnvironmentData;
import com.pathland.view.transport.Event;
import com.pathland.view.transport.FrameCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One Pathland application instance per session (1:1 with a client). Owns the session's
 * {@link PersistentState}, the retained tree, the fine-grained emitter, and the single
 * connection it sends deltas to — all transport-agnostic ({@link PathlandConnection}).
 *
 * <p>The platform environment (spec/OPCODE.md §Environment fields) seeds the app: the
 * initial route comes from its {@code ROUTE} field (a request URL on SSR; the DOM
 * client's first message over the WebSocket), delivered to the tree as the
 * {@code Platform.ACTIVE_PATH} signal. Later environment messages **enrich** the session
 * (viewport resizes, future fields) via {@link #applyEnvironment}.
 *
 * <p>State wiring is automatic: {@code State} fields in the app's views connect to the
 * store at mount. The connection is wired only <em>after</em> mount, so the mount frame
 * (already SSR'd) is never re-sent.
 */
public final class PathlandSession {

    private static final Logger LOG = LoggerFactory.getLogger(PathlandSession.class);

    /** The reserved framework path prefix (spec): host system endpoints (the
     *  WebSocket, the DOM client bundle, the asset mount) live under
     *  {@code /_pathland/**}, never colliding with app routes. */
    public static final String PATHLAND_BASE = "/_pathland";

    private final PersistentState state;
    private final WritableSignal<String> activePath;

    private final String base;
    private final FrameOpcodeSink sink;
    private final DeltaBatcher batcher;
    private final Emitter emitter;
    private final InputDispatcher inputDispatcher;
    private final int rootId;
    private final boolean debugHtml;
    private final PathlandTelemetry telemetry;
    private final String mount;

    private float viewportWidth = -1f;
    private float viewportHeight = -1f;

    private volatile PathlandConnection connection;

    public PathlandSession(String sessionId, StateStore store, PathlandApp app, EnvironmentData env) {
        this(sessionId, store, app, env, false);
    }

    public PathlandSession(
            String sessionId,
            StateStore store,
            PathlandApp app,
            EnvironmentData env,
            boolean debugHtml) {
        this(sessionId, store, app, env, debugHtml, PATHLAND_BASE, sessionId);
    }

    public PathlandSession(
            String sessionId,
            StateStore store,
            PathlandApp app,
            EnvironmentData env,
            boolean debugHtml,
            String base,
            String stateScope) {
        this(sessionId, store, app, env, debugHtml, base, stateScope, PathlandTelemetry.NOOP, "/");
    }

    public PathlandSession(
            String sessionId,
            StateStore store,
            PathlandApp app,
            EnvironmentData env,
            boolean debugHtml,
            String base,
            String stateScope,
            PathlandTelemetry telemetry,
            String mount) {
        this.debugHtml = debugHtml;
        this.base = base;
        this.telemetry = telemetry == null ? PathlandTelemetry.NOOP : telemetry;
        this.mount = mount == null ? "/" : mount;
        this.state = new PersistentState(store, stateScope);
        // The active platform path is a host-provided signal (Platform.ACTIVE_PATH); the
        // app reads it, and a bound Router re-routes guard-aware on external changes.
        this.activePath = Signals.signal(env.route());

        this.sink = new FrameOpcodeSink() {
            @Override
            public void endFrame() {
                super.endFrame();
                // Only live deltas go out: the mount frame (emitted in the
                // constructor, before the session attaches) is NOT sent — the
                // client already has the whole UI from the HTML. A dead
                // connection's frames are dropped (a reconnect re-syncs).
                PathlandConnection conn = connection;
                if (conn == null || !conn.isOpen()) {
                    return;
                }
                ProtocolFrame frame = frame();
                if (!frame.opcodes().isEmpty()) {
                    batcher.append(frame);
                }
            }
        };
        this.batcher = new DeltaBatcher(this::sendBatch, this.telemetry, this.mount);
        this.emitter = new Emitter(sink, app.theme());

        // Mount wires State fields, then renders and emits the structural frame. The
        // active path is injected as a scoped environment value; any root works (with or
        // without navigation).
        RenderResult result = emitter.mount(
                app.newRoot().with(EnvironmentBinding.with(e -> e.key(Platform.ACTIVE_PATH).value(activePath))),
                new Environment(state));
        this.inputDispatcher = new InputDispatcher(result, activePath);
        this.rootId = result.rootId();
        applyEnvironment(env); // records viewport; the router already seeded from env.route()
    }

    /** Apply (or enrich) the platform environment after mount (viewport resizes, …). */
    public void applyEnvironment(EnvironmentData env) {
        activePath.set(env.route()); // re-route if the platform moved (guards run in the bound router)
        if (env.viewportWidth() > 0) {
            viewportWidth = env.viewportWidth();
        }
        if (env.viewportHeight() > 0) {
            viewportHeight = env.viewportHeight();
        }
    }

    /** Wire the live connection AFTER mount (the initial frame was already SSR'd). */
    public void connect(PathlandConnection connection) {
        this.connection = connection;
    }

    /** Re-send the current tree as a full snapshot (META::RESYNC), flushed immediately. */
    public void resync() {
        emitter.renderFull();
        batcher.flush();
    }

    /**
     * Send an encoded batch to the connection. On any send failure (or a closed
     * connection) the connection is dropped and best-effort closed, so the DOM
     * client reconnects and requests a {@code META::RESYNC} instead of silently
     * stalling on an "open" WebSocket that never delivers.
     */
    private void sendBatch(byte[] bytes) {
        PathlandConnection conn = connection;
        if (conn == null || !conn.isOpen()) {
            connection = null;
            return;
        }
        try {
            conn.send(bytes);
        } catch (Exception e) {
            connection = null;
            telemetry.connectionFailed(mount, e.getMessage());
            closeConnection(conn);
        }
    }

    /** Best-effort close of a failed connection (the adapters may expose a close). */
    private static void closeConnection(PathlandConnection conn) {
        if (conn instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // best-effort: the session already dropped the connection
            }
        }
    }

    /** Route an inbound event batch (raw host → guest opcodes) into the app's bindings. */
    public void dispatch(byte[] message) {
        dispatch(FrameCodec.decodeEvents(message));
    }

    /** Route decoded events into the app's bindings (a host may pre-transform them, e.g. mount-strip NAVIGATE urls). */
    public void dispatch(Iterable<Event> events) {
        try {
            inputDispatcher.dispatch(events);
        } catch (RuntimeException e) {
            LOG.warn("dropping event batch: {}", e.getMessage());
        }
    }

    /** Render the SSR HTML for this session's current tree (request thread). */
    public String renderHtml() {
        HtmlRenderer renderer = HtmlRenderer.tryInstance();
        if (renderer == null) {
            return "<!DOCTYPE html><html><body><h1>Pathland renderer unavailable</h1>"
                    + "<p>Build the Rust crate so libpathland_render_html is embedded in the "
                    + "pathland-render-html jar.</p></body></html>";
        }
        String html = debugHtml
                ? renderer.renderDebug(sink.frame(), rootId)
                : renderer.render(sink.frame(), rootId);
        // The reserved framework path prefix (spec): host system endpoints live
        // under `/{base}/**`. Carried as `data-pathland-base` so the DOM client
        // resolves its WebSocket and the bundle from the same base. Multi-app
        // hosts give each mounted app its own base (e.g. `/app2/_pathland`).
        // The bundle name is content-hashed (see PathlandAssets) so a new build
        // busts the immutable asset cache via a fresh URL.
        return html
                .replace("<html>", "<html data-pathland-base=\"" + base + "\">")
                .replace("</body>", "<script src=\"" + base + "/" + PathlandAssets.bundleName()
                        + "\" defer></script></body>");
    }

    /** Tear down: close the persistent state, unsubscribe the emitter, stop the batcher, drop the connection. */
    public void close() {
        state.close();
        emitter.destroy();
        batcher.close();
        connection = null;
    }
}