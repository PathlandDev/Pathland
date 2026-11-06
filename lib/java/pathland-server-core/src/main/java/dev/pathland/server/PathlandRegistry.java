package dev.pathland.server;

import dev.pathland.view.core.state.PersistentState;
import dev.pathland.view.core.router.Route;

import dev.pathland.view.core.router.Router;
import dev.pathland.view.core.state.StateStore;
import dev.pathland.view.core.transport.EnvironmentData;
import dev.pathland.view.core.transport.Event;
import dev.pathland.view.core.transport.FrameCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The application registry: 1:1 sessions keyed by a per-connection id. Each WebSocket
 * connection owns one {@link PathlandSession} (its own retained tree + deltas); two
 * windows of the same browser never share a session. Transport-agnostic — the framework
 * starters adapt their connection type to {@link PathlandConnection}.
 *
 * <p>Session <b>identity</b> (the map key) and the <b>persisted-state scope</b> are
 * deliberately separate: the registry is keyed by a fresh per-connection {@code uiId},
 * while the {@link PersistentState} scope comes from a client-provided window id
 * ({@code wid}, carried on the WebSocket query string) so each window keeps its own
 * persisted state across reloads.
 *
 * <p>Sessions are created **lazily on the first inbound message**: the platform
 * environment ({@code META::ENVIRONMENT}, spec/OPCODE.md) arrives as the DOM client's
 * first message, and its {@code ROUTE} field seeds the router before mount — so a
 * deep-linked URL renders the right destination and the WebSocket tree stays consistent
 * with the SSR HTML. {@code open} registers the pending connection, or attaches it
 * to a session already created by a first message that raced ahead; later
 * environment messages **enrich** the session via {@code applyEnvironment}.
 *
 * <p>All session work is serialized on a single actor thread (signals are single-threaded
 * by contract). Per-session SSR runs on the request thread against the thread-safe
 * {@link StateStore} (a throwaway {@link PathlandSession}).
 */
public final class PathlandRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(PathlandRegistry.class);

    /** Largest inbound WebSocket message accepted (event batches, environment, resync). */
    public static final int MAX_INBOUND_BATCH_BYTES = 1 << 16;

    private final String mountPath;
    private final PathlandApp app;
    private final StateStore store;
    private final boolean debugHtml;
    private final PathlandTelemetry telemetry;
    private final boolean serviceWorker;

    private final ExecutorService actor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pathland-actor");
        t.setDaemon(true);
        return t;
    });

    /** Set once {@link #shutdown()} starts; new actor work is dropped. */
    private final AtomicBoolean shuttingDown = new AtomicBoolean();

    /** True while the current thread is inside an actor task (shutdown re-entry guard). */
    private final ThreadLocal<Boolean> onActor = new ThreadLocal<>();

    private final Map<String, PathlandSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, PendingSession> pending = new ConcurrentHashMap<>();

    /** Per-connection inbound message budget, checked before actor work is queued. */
    private final InboundRateLimiter inbound = new InboundRateLimiter();

    public PathlandRegistry(PathlandApp app, StateStore store) {
        this("/", app, store, false);
    }

    public PathlandRegistry(PathlandApp app, StateStore store, boolean debugHtml) {
        this("/", app, store, debugHtml);
    }

    public PathlandRegistry(String mountPath, PathlandApp app, StateStore store, boolean debugHtml) {
        this(mountPath, app, store, debugHtml, PathlandTelemetry.NOOP);
    }

    public PathlandRegistry(
            String mountPath, PathlandApp app, StateStore store, boolean debugHtml, PathlandTelemetry telemetry) {
        this(mountPath, app, store, debugHtml, telemetry, false);
    }

    public PathlandRegistry(
            String mountPath,
            PathlandApp app,
            StateStore store,
            boolean debugHtml,
            PathlandTelemetry telemetry,
            boolean serviceWorker) {
        this.mountPath = mountPath == null || mountPath.isBlank() ? "/" : mountPath;
        this.app = app;
        this.store = store;
        this.debugHtml = debugHtml;
        this.telemetry = telemetry == null ? PathlandTelemetry.NOOP : telemetry;
        this.serviceWorker = serviceWorker;
    }

    /** Whether the SSR opts the DOM client into the per-window-id service worker (issue #155). */
    public boolean serviceWorker() {
        return serviceWorker;
    }

    /** The reserved framework URL the service worker script is served from (under this app's base). */
    public String serviceWorkerUrl() {
        return base() + "/sw.js";
    }

    /** Whether SSR HTML is rendered with per-node debug comments. */
    public boolean isDebugHtml() {
        return debugHtml;
    }

    /** The normalized subpath prefix this registry's app is mounted at ({@code "/"} for root). */
    public String mountPath() {
        return mountPath;
    }

    /** The reserved framework base this app's host endpoints live under (e.g. {@code "/app2/_pathland"}). */
    public String base() {
        return "/".equals(mountPath) ? PathlandSession.PATHLAND_BASE : mountPath + PathlandSession.PATHLAND_BASE;
    }

    /**
     * The state-store scope for a session of this app: app-qualified so two apps sharing
     * a store never collide on the same window id ({@code "mount:windowId"}; bare
     * {@code windowId} for the root mount).
     */
    public String stateScope(String windowId) {
        return "/".equals(mountPath) ? windowId : mountPath + ":" + windowId;
    }

    /**
     * Strip this app's mount prefix from a route, so the app always sees its own route
     * space ({@code /app2/home} → {@code /home}). Idempotent: routes already outside the
     * mount pass through unchanged.
     */
    public String stripRoute(String route) {
        if ("/".equals(mountPath)) {
            return route;
        }
        if (route == null || route.isBlank()) {
            return "/";
        }
        if (route.equals(mountPath)) {
            return "/";
        }
        if (route.startsWith(mountPath + "/")) {
            return route.substring(mountPath.length());
        }
        return route;
    }

    /**
     * Render the SSR HTML (request thread), seeding the router from the request path. The
     * per-window id is carried only on the WebSocket URL (kept in {@code sessionStorage},
     * never in the page URL), so the SSR request normally has no {@code windowId} and this
     * throwaway session renders <b>defaults</b>; the client's live session (scoped by the
     * WS {@code wid}) re-syncs that window's persisted state over the WebSocket after a
     * same-tab reload. A {@code windowId} is honored when present (a stale {@code wid} a
     * shared/bookmarked URL may carry).
     */
    public String renderHtml(String route, String windowId) {
        boolean scoped = WindowIds.isValid(windowId);
        String scope = scoped ? stateScope(windowId) : stateScope(UUID.randomUUID().toString());
        String sessionId = UUID.randomUUID().toString();
        long start = System.nanoTime();
        PathlandSession session = new PathlandSession(
                sessionId, store, app, EnvironmentData.of(stripRoute(route)),
                debugHtml, base(), scope, telemetry, mountPath);
        try {
            return session.renderHtml(serviceWorker, scoped);
        } finally {
            telemetry.ssrRendered(mountPath, System.nanoTime() - start);
            session.close();
        }
    }

    /** Render SSR defaults (no window id — a first visit without the client running yet). */
    public String renderHtml(String route) {
        return renderHtml(route, null);
    }

    /**
     * Register a connection for a session. {@code uiId} is a fresh per-connection id (the
     * session's identity — two windows never collide); {@code windowId} is the client's
     * per-window id (the persisted-state scope, mount-prefixed). The session is created on
     * its first message.
     */
    public void open(String uiId, String windowId, PathlandConnection connection) {
        execute(() -> {
            // A WebSocket's first messages can reach the actor before this open task (the
            // transport delivers messages while the endpoint is still wiring the
            // connection). If environment/resync already created the session, attach the
            // connection instead of replacing the session, and re-send the snapshot that
            // may have raced ahead of the connection.
            PathlandSession existing = sessions.get(uiId);
            if (existing != null) {
                existing.connect(connection);
                existing.resync();
                return;
            }
            pending.put(uiId, new PendingSession(stateScope(windowId), connection));
        });
    }

    /** Apply the platform environment: creates the session (seeded from its ROUTE field) on first contact, or enriches it after mount. */
    public void environment(String sessionId, EnvironmentData env) {
        EnvironmentData appEnv = stripRoute(env);
        int cost = 64 + appEnv.route().length() * 2;
        if (!allowInbound(sessionId, cost, "environment")) {
            return;
        }
        execute(() -> session(sessionId, appEnv).applyEnvironment(appEnv));
    }

    /** Route an inbound event batch to the owning session (NAVIGATE urls are mount-stripped first). */
    public void dispatch(String sessionId, byte[] message) {
        if (message.length > MAX_INBOUND_BATCH_BYTES) {
            LOG.warn("dropping oversized event batch ({} bytes)", message.length);
            telemetry.inboundDropped(mountPath, "oversized");
            return;
        }
        if (!allowInbound(sessionId, message.length, "event batch")) {
            return;
        }
        execute(() -> {
            List<Event> events;
            try {
                events = stripNavigateUrls(FrameCodec.decodeEvents(message));
            } catch (RuntimeException e) {
                LOG.warn("dropping malformed event batch: {}", e.getMessage());
                return;
            }
            telemetry.eventBatchReceived(mountPath, events.size());
            session(sessionId, EnvironmentData.of("/")).dispatch(events);
        });
    }

    /** Handle a META::RESYNC request: re-send the session's current tree as a snapshot. */
    public void resync(String sessionId) {
        if (!allowInbound(sessionId, 16, "resync")) {
            return;
        }
        execute(() -> {
            telemetry.resyncRequested(mountPath);
            session(sessionId, EnvironmentData.of("/")).resync();
        });
    }

    /** Close and remove a session (and drop any pending connection). */
    public void close(String sessionId) {
        inbound.remove(sessionId);
        execute(() -> {
            pending.remove(sessionId);
            PathlandSession session = sessions.remove(sessionId);
            if (session != null) {
                session.close();
                telemetry.sessionClosed(mountPath);
            }
        });
    }

    /** Check the per-connection inbound budget; drop + report when exceeded. */
    private boolean allowInbound(String sessionId, int bytes, String kind) {
        if (inbound.allow(sessionId, bytes)) {
            return true;
        }
        LOG.warn("dropping {} for {}: inbound rate limit exceeded", kind, sessionId);
        telemetry.inboundDropped(mountPath, kind);
        return false;
    }

    /**
     * Shut down the registry: close every session and stop the actor.
     *
     * <p>Teardown is serialized on the actor, like every other session operation. Closing
     * sessions directly on the caller's thread would race a queued mount/resync that is
     * mutating the same emitter (observed as a {@link java.util.ConcurrentModificationException}
     * during {@code Emitter.destroy}). The call is idempotent and drops any work enqueued
     * after shutdown starts.
     */
    public void shutdown() {
        if (!shuttingDown.compareAndSet(false, true)) {
            return;
        }
        Runnable closeAll = this::closeAllSessions;
        if (Boolean.TRUE.equals(onActor.get())) {
            // Re-entrant shutdown from an actor task: run inline (a submit+get would deadlock).
            closeAll.run();
            actor.shutdown();
            inbound.clear();
            return;
        }
        try {
            actor.submit(() -> runOnActor(closeAll)).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            LOG.warn("registry shutdown failed: {}", e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
        } catch (RejectedExecutionException ignored) {
            // already shut down
        }
        actor.shutdown();
        try {
            if (!actor.awaitTermination(5, TimeUnit.SECONDS)) {
                actor.shutdownNow();
            }
        } catch (InterruptedException e) {
            actor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        inbound.clear();
    }

    /** Enqueue serialized session work; work is dropped once shutdown has started. */
    private void execute(Runnable task) {
        if (shuttingDown.get()) {
            return;
        }
        try {
            actor.execute(() -> {
                // The shutdown task may have run after this task was queued; don't create
                // sessions/emit work after teardown.
                if (shuttingDown.get()) {
                    return;
                }
                runOnActor(task);
            });
        } catch (RejectedExecutionException ignored) {
            // already shut down
        }
    }

    private void runOnActor(Runnable task) {
        onActor.set(Boolean.TRUE);
        try {
            task.run();
        } finally {
            onActor.remove();
        }
    }

    private void closeAllSessions() {
        for (PathlandSession session : sessions.values()) {
            session.close();
            telemetry.sessionClosed(mountPath);
        }
        sessions.clear();
        pending.clear();
    }

    /** The number of live sessions for this mount (a health/metrics gauge). */
    public int activeSessions() {
        return sessions.size();
    }

    /** Create the session on first contact, wired to its pending connection. */
    private PathlandSession session(String uiId, EnvironmentData env) {
        PathlandSession session = sessions.get(uiId);
        if (session == null) {
            PendingSession pending = this.pending.remove(uiId);
            session = new PathlandSession(
                    uiId, store, app, env, debugHtml, base(),
                    pending != null ? pending.stateScope() : stateScope(uiId), telemetry, mountPath);
            if (pending != null) {
                session.connect(pending.connection());
            }
            sessions.put(uiId, session);
            telemetry.sessionOpened(mountPath);
        }
        return session;
    }

    /** Rebuild the environment with the mount prefix stripped from its route. */
    private EnvironmentData stripRoute(EnvironmentData env) {
        return new EnvironmentData(stripRoute(env.route()), env.viewportWidth(), env.viewportHeight());
    }

    /**
     * Strip the mount prefix from a NAVIGATE event's URL (browser back/forward sends the
     * real URL, e.g. {@code http://host/app2/home}): the app's router is mount-relative, so
     * it must see {@code /home}. Non-NAVIGATE events pass through unchanged; the root mount
     * is the identity.
     */
    private List<Event> stripNavigateUrls(List<Event> events) {
        if (events.stream().noneMatch(Event::isNavigate)) {
            return events;
        }
        return events.stream()
                .map(event -> event.isNavigate() && event.url() != null
                        ? Event.navigate(stripRoute(Router.pathOf(event.url())))
                        : event)
                .toList();
    }

    /** A connection awaiting its first message, with the window id's state scope. */
    private record PendingSession(String stateScope, PathlandConnection connection) {
    }
}