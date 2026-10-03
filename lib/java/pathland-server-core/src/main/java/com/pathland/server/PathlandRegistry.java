package com.pathland.server;

import com.pathland.view.router.Router;
import com.pathland.view.state.StateStore;
import com.pathland.view.transport.EnvironmentData;
import com.pathland.view.transport.Event;
import com.pathland.view.transport.FrameCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
 * with the SSR HTML. {@code open} only registers the pending connection; later
 * environment messages **enrich** the session via {@code applyEnvironment}.
 *
 * <p>All session work is serialized on a single actor thread (signals are single-threaded
 * by contract). Per-session SSR runs on the request thread against the thread-safe
 * {@link StateStore} (a throwaway {@link PathlandSession}).
 */
public final class PathlandRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(PathlandRegistry.class);

    private static final int MAX_EVENT_BATCH = 1 << 16;

    private final String mountPath;
    private final PathlandApp app;
    private final StateStore store;
    private final boolean debugHtml;
    private final PathlandTelemetry telemetry;

    private final ExecutorService actor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pathland-actor");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, PathlandSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, PendingSession> pending = new ConcurrentHashMap<>();

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
        this.mountPath = mountPath == null || mountPath.isBlank() ? "/" : mountPath;
        this.app = app;
        this.store = store;
        this.debugHtml = debugHtml;
        this.telemetry = telemetry == null ? PathlandTelemetry.NOOP : telemetry;
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
        String scope = windowId == null || windowId.isBlank()
                ? stateScope(UUID.randomUUID().toString())
                : stateScope(windowId);
        String sessionId = UUID.randomUUID().toString();
        long start = System.nanoTime();
        PathlandSession session = new PathlandSession(
                sessionId, store, app, EnvironmentData.of(stripRoute(route)),
                debugHtml, base(), scope, telemetry, mountPath);
        try {
            return session.renderHtml();
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
        actor.execute(() -> {
            PathlandSession previous = sessions.remove(uiId);
            if (previous != null) {
                previous.close();
            }
            pending.put(uiId, new PendingSession(stateScope(windowId), connection));
        });
    }

    /** Apply the platform environment: creates the session (seeded from its ROUTE field) on first contact, or enriches it after mount. */
    public void environment(String sessionId, EnvironmentData env) {
        EnvironmentData appEnv = stripRoute(env);
        actor.execute(() -> session(sessionId, appEnv).applyEnvironment(appEnv));
    }

    /** Route an inbound event batch to the owning session (NAVIGATE urls are mount-stripped first). */
    public void dispatch(String sessionId, byte[] message) {
        if (message.length > MAX_EVENT_BATCH) {
            LOG.warn("dropping oversized event batch ({} bytes)", message.length);
            return;
        }
        actor.execute(() -> {
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
        actor.execute(() -> {
            telemetry.resyncRequested(mountPath);
            session(sessionId, EnvironmentData.of("/")).resync();
        });
    }

    /** Close and remove a session (and drop any pending connection). */
    public void close(String sessionId) {
        actor.execute(() -> {
            pending.remove(sessionId);
            PathlandSession session = sessions.remove(sessionId);
            if (session != null) {
                session.close();
                telemetry.sessionClosed(mountPath);
            }
        });
    }

    /** Shut down the registry: close every session and stop the actor. */
    public void shutdown() {
        for (PathlandSession session : sessions.values()) {
            session.close();
            telemetry.sessionClosed(mountPath);
        }
        sessions.clear();
        pending.clear();
        actor.shutdown();
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