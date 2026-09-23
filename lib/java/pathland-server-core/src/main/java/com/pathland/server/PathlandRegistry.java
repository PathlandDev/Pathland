package com.pathland.server;

import com.pathland.view.state.StateStore;
import com.pathland.view.transport.EnvironmentData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The application registry: 1:1 sessions keyed by id. Each WebSocket connection owns one
 * {@link PathlandSession}; deltas flow only to that client (never broadcast). Transport-
 * agnostic — the framework starters adapt their connection type to {@link PathlandConnection}.
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

    private static final int MAX_EVENT_BATCH = 1 << 16;

    private final PathlandApp app;
    private final StateStore store;
    private final boolean debugHtml;

    private final ExecutorService actor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pathland-actor");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, PathlandSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, PathlandConnection> pending = new ConcurrentHashMap<>();

    public PathlandRegistry(PathlandApp app, StateStore store) {
        this(app, store, false);
    }

    public PathlandRegistry(PathlandApp app, StateStore store, boolean debugHtml) {
        this.app = app;
        this.store = store;
        this.debugHtml = debugHtml;
    }

    /** Whether SSR HTML is rendered with per-node debug comments. */
    public boolean isDebugHtml() {
        return debugHtml;
    }

    /** Render the SSR HTML for a session (request thread), seeding the router from the request path. */
    public String renderHtml(String sessionId, String route) {
        PathlandSession session = new PathlandSession(sessionId, store, app, EnvironmentData.of(route), debugHtml);
        try {
            return session.renderHtml();
        } finally {
            session.close();
        }
    }

    /** Register the connection for a session id; the session is created on its first message. */
    public void open(String sessionId, PathlandConnection connection) {
        actor.execute(() -> {
            PathlandSession previous = sessions.remove(sessionId);
            if (previous != null) {
                previous.close();
            }
            pending.put(sessionId, connection);
        });
    }

    /** Apply the platform environment: creates the session (seeded from its ROUTE field) on first contact, or enriches it after mount. */
    public void environment(String sessionId, EnvironmentData env) {
        actor.execute(() -> session(sessionId, env).applyEnvironment(env));
    }

    /** Route an inbound event batch to the owning session. */
    public void dispatch(String sessionId, byte[] message) {
        if (message.length > MAX_EVENT_BATCH) {
            return;
        }
        actor.execute(() -> session(sessionId, EnvironmentData.of("/")).dispatch(message));
    }

    /** Handle a META::RESYNC request: re-send the session's current tree as a snapshot. */
    public void resync(String sessionId) {
        actor.execute(() -> session(sessionId, EnvironmentData.of("/")).resync());
    }

    /** Close and remove a session (and drop any pending connection). */
    public void close(String sessionId) {
        actor.execute(() -> {
            pending.remove(sessionId);
            PathlandSession session = sessions.remove(sessionId);
            if (session != null) {
                session.close();
            }
        });
    }

    /** Shut down the registry: close every session and stop the actor. */
    public void shutdown() {
        for (PathlandSession session : sessions.values()) {
            session.close();
        }
        sessions.clear();
        pending.clear();
        actor.shutdown();
    }

    /** Create the session on first contact, wired to its pending connection. */
    private PathlandSession session(String sessionId, EnvironmentData env) {
        PathlandSession session = sessions.get(sessionId);
        if (session == null) {
            PathlandConnection connection = pending.remove(sessionId);
            session = new PathlandSession(sessionId, store, app, env, debugHtml);
            if (connection != null) {
                session.connect(connection);
            }
            sessions.put(sessionId, session);
        }
        return session;
    }
}