package com.pathland.server;

import com.pathland.render.html.HtmlRenderer;
import com.pathland.view.Environment;
import com.pathland.view.Platform;
import com.pathland.view.emit.Emitter;
import com.pathland.view.emit.Frame;
import com.pathland.view.emit.FrameOpcodeSink;
import com.pathland.view.emit.InputDispatcher;
import com.pathland.view.emit.RenderResult;
import com.pathland.view.signal.Signals;
import com.pathland.view.signal.WritableSignal;
import com.pathland.view.state.PersistentState;
import com.pathland.view.state.StateStore;
import com.pathland.view.transport.EnvironmentData;
import com.pathland.view.transport.FrameCodec;

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

    private final PersistentState state;
    private final WritableSignal<String> activePath;

    private final FrameOpcodeSink sink;
    private final Emitter emitter;
    private final InputDispatcher inputDispatcher;
    private final int rootId;
    private final boolean debugHtml;

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
        this.debugHtml = debugHtml;
        this.state = new PersistentState(store, sessionId);
        // The active platform path is a host-provided signal (Platform.ACTIVE_PATH); the
        // app reads it, and a bound Router re-routes guard-aware on external changes.
        this.activePath = Signals.signal(env.route());

        this.sink = new FrameOpcodeSink() {
            @Override
            public void endFrame() {
                super.endFrame();
                Frame frame = frame();
                if (!frame.opcodes().isEmpty()) {
                    // The mount frame (emitted in the constructor, before the session
                    // attaches) is NOT sent — the client already has the whole UI from
                    // the HTML. Only deltas + resync snapshots go out.
                    send(frame);
                }
            }
        };
        this.emitter = new Emitter(sink, app.theme());

        // Mount wires State fields, then renders and emits the structural frame. The
        // active path is injected as a scoped environment value; any root works (with or
        // without navigation).
        RenderResult result = emitter.mount(
                app.newRoot().environment(Platform.ACTIVE_PATH, activePath),
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

    /** Re-send the current tree as a full snapshot (META::RESYNC). */
    public void resync() {
        emitter.renderFull();
    }

    private void send(Frame frame) {
        PathlandConnection conn = connection;
        if (conn == null || !conn.isOpen()) {
            connection = null;
            return;
        }
        byte[] bytes = FrameCodec.encodeFrame(frame);
        try {
            conn.send(bytes);
        } catch (Exception e) {
            connection = null;
        }
    }

    /** Route an inbound event batch (raw host → guest opcodes) into the app's bindings. */
    public void dispatch(byte[] message) {
        try {
            inputDispatcher.dispatch(FrameCodec.decodeEvents(message));
        } catch (RuntimeException e) {
            log("dropping event batch: " + e.getMessage());
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
        return html.replace("</body>", "<script src=\"/pathland-dom-renderer.js\" defer></script></body>");
    }

    /** Tear down: close the persistent state, unsubscribe the emitter, drop the connection. */
    public void close() {
        state.close();
        emitter.destroy();
        connection = null;
    }

    private static void log(String message) {
        System.out.println("[pathland] " + message);
    }
}