package dev.pathland.server;

import dev.pathland.view.core.router.Route;
import dev.pathland.view.core.router.Router;

import dev.pathland.render.html.DocumentMeta;
import dev.pathland.render.html.HtmlRenderer;
import dev.pathland.server.proxy.ProxyHost;
import dev.pathland.server.proxy.ProxySession;
import dev.pathland.server.introspection.PathlandIntrospection;
import dev.pathland.view.core.Environment;
import dev.pathland.view.core.EnvironmentBinding;
import dev.pathland.view.core.Platform;
import dev.pathland.view.core.ProxyConfig;
import dev.pathland.view.core.ThemeData;
import dev.pathland.view.core.ThemeMode;
import dev.pathland.view.engine.Emitter;
import dev.pathland.view.core.transport.ProtocolFrame;
import dev.pathland.view.core.transport.FrameOpcodeSink;
import dev.pathland.view.engine.InputDispatcher;
import dev.pathland.view.engine.RenderResult;
import dev.pathland.view.core.signal.Signals;
import dev.pathland.view.core.signal.WritableSignal;
import dev.pathland.view.core.subscription.SessionContext;
import dev.pathland.view.core.state.PersistentState;
import dev.pathland.view.core.state.StateStore;
import dev.pathland.view.core.transport.EnvironmentData;
import dev.pathland.view.core.transport.Event;
import dev.pathland.view.core.transport.FrameCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

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

    /** Default how long the SSR path waits for a proxy's origin snapshot before rendering a placeholder. */
    private static final long DEFAULT_PROXY_SNAPSHOT_TIMEOUT_MILLIS = 2_000;

    /** The effective SSR proxy-snapshot timeout ({@code pathland.proxy.snapshot-timeout-ms}). */
    private static long proxySnapshotTimeoutMillis() {
        return Long.getLong("pathland.proxy.snapshot-timeout-ms", DEFAULT_PROXY_SNAPSHOT_TIMEOUT_MILLIS);
    }

    private final PersistentState state;
    private final WritableSignal<String> activePath;
    private final String stateScope;

    private final String sessionId;
    private final String base;
    private final FrameOpcodeSink sink;
    private final DeltaBatcher batcher;
    private final Emitter emitter;
    private final InputDispatcher inputDispatcher;
    private final int rootId;
    private final boolean debugHtml;
    private final PathlandTelemetry telemetry;
    private final String mount;

    /** Diagnostic lifecycle + throughput counters (read-only Dev UI snapshots). */
    private final long openedAtMillis = System.currentTimeMillis();
    private volatile long lastActivityMillis = openedAtMillis;
    private final AtomicLong inboundEvents = new AtomicLong();
    private final AtomicLong resyncs = new AtomicLong();
    private volatile ThemeData theme;
    private volatile ThemeMode colorScheme = ThemeMode.PLATFORM;

    /** Where actor work runs (the registry's actor; inline for standalone/SSR sessions). */
    private final Executor scheduler;

    /** This session's topic subscriptions (registered with the app's hub when live). */
    private final SessionSubscriptions subscriptions;

    private volatile boolean closed;

    /** node id → proxy config for every {@code PROXY_VIEW} in the mounted tree (spec/PROXY.md). */
    private final Map<Integer, ProxyConfig> proxyViews;

    private float viewportWidth = -1f;
    private float viewportHeight = -1f;

    private volatile PathlandConnection connection;
    private volatile ProxyHost proxyHost;

    /** The outbound transport for proxy origin connections (default: the JDK WebSocket client). */
    private dev.pathland.server.proxy.ProxyTransport.Factory proxyTransportFactory =
            dev.pathland.server.proxy.JdkWebSocketTransport.factory();

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
        this(sessionId, store, app, env, debugHtml, base, stateScope, telemetry, mount, null, null);
    }

    /**
     * The full constructor: [scheduler] is where actor work runs (the registry's
     * actor for a live session; inline for a standalone/SSR session), and [hub] is
     * the app's topic hub (null for SSR, which renders once and is discarded).
     */
    public PathlandSession(
            String sessionId,
            StateStore store,
            PathlandApp app,
            EnvironmentData env,
            boolean debugHtml,
            String base,
            String stateScope,
            PathlandTelemetry telemetry,
            String mount,
            Executor scheduler,
            PathlandSignalHub hub) {
        this.debugHtml = debugHtml;
        this.base = base;
        this.sessionId = sessionId;
        this.telemetry = telemetry == null ? PathlandTelemetry.NOOP : telemetry;
        this.mount = mount == null ? "/" : mount;
        this.scheduler = scheduler == null ? Runnable::run : scheduler;
        this.subscriptions = new SessionSubscriptions(hub, this::submit, this.telemetry, this.mount);
        this.state = new PersistentState(store, stateScope);
        this.stateScope = stateScope;
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
        this.theme = app.theme();
        this.emitter = new Emitter(sink, this.theme);
        // SSR full-snapshot emission: fit slots carry ALL candidates so the web
        // renderer shows every branch (CSS container queries gate the first paint —
        // issue #141). The live reconcile path still emits one selected child after
        // the first FIT_CHANGED; this stays invisible to DSL authors.
        this.emitter.snapshotEmission(true);

        // Mount wires State fields, then renders and emits the structural frame. The
        // active path is injected as a scoped environment value; any root works (with or
        // without navigation). The session context is installed first so a view can
        // declare topic subscriptions as field initializers.
        SessionContext previous = SessionContext.push(subscriptions);
        try {
            RenderResult result = emitter.mount(
                    app.newRoot().modifiers(
                            EnvironmentBinding.with(e -> e.key(Platform.ACTIVE_PATH).value(activePath))),
                    new Environment(state));
            this.inputDispatcher = new InputDispatcher(result, activePath);
            this.rootId = result.rootId();
            this.proxyViews = result.proxyViews();
            // A structural reconcile may add/remove a PROXY_VIEW node (e.g. a SizeThatFits
            // slot dropping the candidate that held it) — reconcile the proxy sessions
            // against the live proxyViews map so a proxy lives exactly while its node is
            // in the retained tree (spec/PROXY.md §7).
            this.emitter.onStructureReconciled(this::reconcileProxies);
            applyEnvironment(env); // records viewport; the router already seeded from env.route()
        } finally {
            SessionContext.restore(previous);
        }
    }

    /**
     * Run [task] on this session's actor. Safe from any thread; non-blocking. Work
     * submitted after close is dropped, and a failing task is logged without
     * stopping the session.
     */
    public void submit(Runnable task) {
        if (task == null) {
            return;
        }
        try {
            scheduler.execute(() -> {
                if (closed) {
                    return;
                }
                runWithContext(() -> {
                    try {
                        task.run();
                    } catch (RuntimeException e) {
                        LOG.warn("session task failed: {}", e.getMessage(), e);
                    }
                });
            });
        } catch (RejectedExecutionException ignored) {
            // the actor is already shut down
        }
    }

    /** Install this session's context for the duration of [task]. */
    private void runWithContext(Runnable task) {
        SessionContext previous = SessionContext.push(subscriptions);
        try {
            task.run();
        } finally {
            SessionContext.restore(previous);
        }
    }

    /** Apply (or enrich) the platform environment after mount (viewport resizes, …). */
    /**
     * Replace the session's global theme at runtime (spec/TOKENS.md §Runtime
     * theming): emits {@code RESET_THEME} + the new overrides to the connected
     * client. The SSR document keeps the mount theme until a resync/reload.
     */
    public void setTheme(ThemeData theme) {
        this.theme = theme;
        runWithContext(() -> emitter.replaceTheme(theme));
    }

    /** Clear all app token overrides (renderer defaults apply). */
    public void resetTheme() {
        this.theme = null;
        runWithContext(emitter::resetTheme);
    }

    /**
     * Force/release the effective color scheme ({@code PLATFORM}/{@code LIGHT}/
     * {@code DARK}); the renderer re-resolves tokens with no node re-emission.
     */
    public void setColorScheme(ThemeMode mode) {
        this.colorScheme = mode;
        runWithContext(() -> emitter.setColorScheme(mode));
    }

    public void applyEnvironment(EnvironmentData env) {
        runWithContext(() -> {
            activePath.set(env.route()); // re-route if the platform moved (guards run in the bound router)
            if (env.viewportWidth() > 0) {
                viewportWidth = env.viewportWidth();
            }
            if (env.viewportHeight() > 0) {
                viewportHeight = env.viewportHeight();
            }
            lastActivityMillis = System.currentTimeMillis();
        });
    }

    /** Wire the live connection AFTER mount (the initial frame was already SSR'd). */
    public void connect(PathlandConnection connection) {
        this.connection = connection;
        reconcileProxies();
    }

    /**
     * Reconcile the proxy sessions against the live {@code proxyViews} map: open an
     * origin connection for every {@code PROXY_VIEW} node in the retained tree, and
     * close the connection of every node that left it (spec/PROXY.md §7). Driven by
     * the emitter's structural-reconcile callback, so a proxy lives exactly while its
     * node is in the tree — e.g. a {@code SizeThatFits} slot dropping the candidate
     * that held it closes the origin connection, and re-adding it opens a fresh one.
     * The origin is given this session's window scope as its {@code wid}, so the
     * origin's persisted state is per-window.
     */
    private void reconcileProxies() {
        if (proxyHost == null) {
            if (proxyViews.isEmpty()) {
                return;
            }
            proxyHost = new ProxyHost(
                    batcher::append, stateScope, viewportWidth, viewportHeight, telemetry, mount,
                    proxyTransportFactory);
        }
        proxyHost.reconcile(proxyViews);
    }

    /**
     * Set the outbound transport for proxy origin connections (spec/PROXY.md §1).
     * Defaults to the JDK WebSocket client; a framework starter may supply a native
     * client (Quarkus websockets-client, Spring {@code StandardWebSocketClient}).
     */
    public void proxyTransportFactory(dev.pathland.server.proxy.ProxyTransport.Factory factory) {
        this.proxyTransportFactory = factory;
    }

    /** Re-send the current tree as a full snapshot (META::RESYNC), flushed immediately. */
    public void resync() {
        runWithContext(() -> {
            resyncs.incrementAndGet();
            lastActivityMillis = System.currentTimeMillis();
            emitter.renderFull();
            // A's own emitter does not carry the origin subtree (it is forwarded), so a
            // client resync must also refresh the proxy regions from their origins.
            ProxyHost host = proxyHost;
            if (host != null) {
                host.resyncAll();
            }
            batcher.flush();
        });
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
        runWithContext(() -> {
            try {
                lastActivityMillis = System.currentTimeMillis();
                List<Event> all;
                if (events instanceof List<Event> list) {
                    all = list;
                    inboundEvents.addAndGet(list.size());
                } else {
                    List<Event> copied = new ArrayList<>();
                    for (Event event : events) {
                        copied.add(event);
                    }
                    all = copied;
                    inboundEvents.addAndGet(copied.size());
                }
                ProxyHost host = proxyHost;
                if (host == null) {
                    inputDispatcher.dispatch(all);
                    return;
                }
                // Events whose target is in a proxy's id range belong to the origin
                // server; everything else dispatches to the app's own bindings.
                List<Event> local = new ArrayList<>();
                for (Event event : all) {
                    if (!host.forward(event)) {
                        local.add(event);
                    }
                }
                if (!local.isEmpty()) {
                    inputDispatcher.dispatch(local);
                }
            } catch (RuntimeException e) {
                LOG.warn("dropping event batch: {}", e.getMessage());
            }
        });
    }

    /** Render the SSR HTML for this session's current tree (request thread). */
    public String renderHtml() {
        return renderHtml(false);
    }

    /**
     * Render the SSR HTML. {@code serviceWorker} opts the DOM client into the
     * per-window-id service worker (issue #155) by emitting
     * {@code data-pathland-service-worker} on {@code <html>}; the client registers
     * from it (and unregisters when it is absent — the kill switch).
     */
    public String renderHtml(boolean serviceWorker) {
        return renderHtml(serviceWorker, false);
    }

    /**
     * Render the SSR HTML. {@code serviceWorker} opts the DOM client into the
     * per-window-id service worker (issue #155) by emitting
     * {@code data-pathland-service-worker} on {@code <html>}; the client registers
     * from it (and unregisters when it is absent — the kill switch).
     *
     * <p>{@code ssrScoped} records that the request carried a real per-window id, so
     * this document was rendered with that window's persisted state — emitted as
     * {@code data-pathland-ssr-scoped} so the client can skip its initial
     * {@code META::RESYNC} (the live session shares the same scope).
     */
    public String renderHtml(boolean serviceWorker, boolean ssrScoped) {
        return renderHtml(serviceWorker, ssrScoped, null);
    }

    /**
     * Render the SSR HTML with per-route document metadata (canonical URL,
     * Open Graph/Twitter cards, JSON-LD). {@code pageMetadata} is the merged
     * host-default + app-override value (see
     * {@link PathlandApp#pageMetadata(PageMetadataRequest)}); {@code null}
     * keeps the legacy head.
     */
    public String renderHtml(
            boolean serviceWorker, boolean ssrScoped, PageMetadata pageMetadata) {
        HtmlRenderer renderer = HtmlRenderer.tryInstance();
        if (renderer == null) {
            return "<!DOCTYPE html><html><body><h1>Pathland renderer unavailable</h1>"
                    + "<p>The pathland-render-html artifact is missing from the classpath.</p>"
                    + "</body></html>";
        }
        ProtocolFrame frame = ssrFrame();
        DocumentMeta meta = documentMeta(pageMetadata);
        // The base design is a content-hashed, immutable stylesheet under the
        // session's framework base (the same base the DOM bundle/WS use). The
        // renderer falls back to the inline document when the linked stylesheet
        // name is unavailable. Token overrides and fit-band rules remain inline.
        String html = debugHtml
                ? renderer.renderLinkedDebug(frame, rootId, base, meta)
                : renderer.renderLinked(frame, rootId, base, meta);
        // The reserved framework path prefix (spec): host system endpoints live
        // under `/{base}/**`. Carried as `data-pathland-base` so the DOM client
        // resolves its WebSocket and the bundle from the same base. Multi-app
        // hosts give each mounted app its own base (e.g. `/app2/_pathland`).
        // The bundle name is content-hashed (see PathlandAssets) so a new build
        // busts the immutable asset cache via a fresh URL.
        String htmlAttrs = " data-pathland-base=\"" + base + "\""
                + (serviceWorker ? " data-pathland-service-worker=\"true\"" : "")
                + (ssrScoped ? " data-pathland-ssr-scoped=\"true\"" : "");
        String integrity = PathlandAssets.bundleIntegrity();
        String integrityAttr = integrity.isEmpty() ? "" : " integrity=\"" + integrity + "\"";
        return injectHtmlAttributes(html, htmlAttrs)
                .replace("</body>", "<script src=\"" + base + "/" + PathlandAssets.bundleName()
                        + "\"" + integrityAttr + " defer></script></body>");
    }

    /** Map the transport-agnostic page metadata onto the renderer option. */
    private static DocumentMeta documentMeta(PageMetadata page) {
        if (page == null) {
            return DocumentMeta.none();
        }
        DocumentMeta meta = DocumentMeta.create()
                .title(page.title())
                .description(page.description())
                .canonicalUrl(page.canonicalUrl())
                .siteName(page.siteName())
                .imageUrl(page.imageUrl())
                .ogType(page.ogType())
                .twitterCard(page.twitterCard())
                .twitterSite(page.twitterSite());
        if (page.openGraph() != null) {
            meta.openGraph(page.openGraph());
        }
        if (page.twitter() != null) {
            meta.twitter(page.twitter());
        }
        if (page.structuredData() != null) {
            meta.structuredData(page.structuredData());
        }
        return meta;
    }

    /**
     * Add host attributes to the renderer's opening {@code <html …>} tag. The
     * renderer may already carry {@code lang} (window metadata), so this must not
     * depend on an exact {@code <html>} string.
     */
    private static String injectHtmlAttributes(String html, String attrs) {
        int start = html.indexOf("<html");
        if (start < 0) {
            return html;
        }
        int end = html.indexOf('>', start);
        if (end < 0) {
            return html;
        }
        return html.substring(0, end) + attrs + html.substring(end);
    }

    /**
     * The frame the SSR document is rendered from: the session's mount frame plus,
     * for every {@code PROXY_VIEW}, the origin's remapped snapshot spliced in
     * (spec/PROXY.md §5) — one document, one render, so the renderer emits the
     * origin's fit band rules and token overrides into the same head. A proxy whose
     * origin does not answer within the timeout renders empty (a placeholder) and
     * hydrates over the live connection.
     */
    private ProtocolFrame ssrFrame() {
        ProtocolFrame frame = sink.frame();
        if (proxyViews.isEmpty()) {
            return frame;
        }
        return FrameMerger.merge(frame, proxySnapshots(proxySnapshotTimeoutMillis()));
    }

    /** Start the proxies (SSR) and block for each origin's snapshot (bounded per proxy). */
    private List<ProtocolFrame> proxySnapshots(long timeoutMillis) {
        reconcileProxies();
        ProxyHost host = proxyHost;
        if (host == null) {
            return List.of();
        }
        List<ProtocolFrame> snapshots = new ArrayList<>();
        for (ProxySession session : host.sessions()) {
            try {
                ProtocolFrame snapshot = session.awaitSnapshot(timeoutMillis);
                if (snapshot != null) {
                    snapshots.add(snapshot);
                } else {
                    LOG.warn("proxy origin {} did not send a snapshot within {} ms during SSR; "
                                    + "rendering a placeholder",
                            session.config().endpoint(), timeoutMillis);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return snapshots;
    }

    /**
     * A read-only snapshot of this session for diagnostic tooling. Package-private:
     * only the owning registry may call it, and only on the session's actor (it
     * reads the active path, the retained tree, and the actor-confined subscription
     * mirror).
     */
    PathlandIntrospection.SessionSnapshot snapshot() {
        DeltaBatcher.Stats batchStats = batcher.stats();
        return new PathlandIntrospection.SessionSnapshot(
                sessionId,
                mount,
                stateScope,
                activePath.get(),
                viewportWidth,
                viewportHeight,
                connection != null && connection.isOpen(),
                closed,
                colorScheme.name(),
                theme != null,
                openedAtMillis,
                lastActivityMillis,
                rootId,
                emitter.treeSize(),
                proxyHost == null ? 0 : proxyHost.activeProxies(),
                subscriptions.snapshot(),
                new PathlandIntrospection.SessionStats(
                        inboundEvents.get(),
                        resyncs.get(),
                        batchStats.batchesFlushed(),
                        batchStats.lastFrames(),
                        batchStats.lastOpcodes(),
                        batchStats.lastBytes(),
                        batchStats.sequence()));
    }

    /** Tear down: stop topic routing, close state, unsubscribe the emitter, stop the batcher, drop the connection. */
    public void close() {
        closed = true;
        subscriptions.close();
        ProxyHost host = proxyHost;
        if (host != null) {
            host.close();
            proxyHost = null;
        }
        state.close();
        emitter.destroy();
        batcher.close();
        connection = null;
    }
}