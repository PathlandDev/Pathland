package dev.pathland.quarkus.devui;

import dev.pathland.server.PathlandBuildInfo;
import dev.pathland.server.PathlandHost;
import dev.pathland.server.PathlandRegistry;
import dev.pathland.server.PathlandSignalHub;
import dev.pathland.server.introspection.PathlandIntrospection.HostSnapshot;
import dev.pathland.server.introspection.PathlandIntrospection.RegistrySnapshot;
import dev.pathland.server.introspection.PathlandIntrospection.SessionSnapshot;
import dev.pathland.view.core.ThemeMode;
import dev.pathland.view.core.state.DebugStateStore;
import dev.pathland.view.core.state.StateStore;
import dev.pathland.view.engine.NodeSnapshot;
import io.quarkus.runtime.annotations.JsonRpcDescription;
import io.quarkus.runtime.annotations.JsonRpcUsage;
import io.quarkus.runtime.annotations.Usage;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The runtime side of the Pathland Dev UI: read-only snapshots plus a small set of
 * lifecycle actions (resync, close). Registered as an unremovable dev-mode bean by
 * {@code PathlandDevUiProcessor}; never discovered (or instantiated) outside local
 * development.
 *
 * <p>All reads go through {@link PathlandHost#snapshot()}, which runs on each
 * registry's actor. Actions reuse the registry's own actor-safe lifecycle
 * operations, so the Dev UI never mutates a session off-actor.
 */
public class PathlandDevUiRpc {

    @Inject
    PathlandHost host;

    @Inject
    PathlandDevTraceStore trace;

    @Inject
    StateStore store;

    /** For tests / direct construction (CDI uses the no-arg constructor + field injection). */
    PathlandDevUiRpc(PathlandHost host) {
        this(host, new PathlandDevTraceStore());
    }

    PathlandDevUiRpc(PathlandHost host, PathlandDevTraceStore trace) {
        this(host, trace, null);
    }

    PathlandDevUiRpc(PathlandHost host, PathlandDevTraceStore trace, StateStore store) {
        this.host = host;
        this.trace = trace;
        this.store = store;
    }

    public PathlandDevUiRpc() {
    }

    /** The host overview: mounts, live session count, and the assets the SSR references. */
    @JsonRpcDescription("Pathland host overview: mounted apps, live sessions, and build assets")
    @JsonRpcUsage(Usage.DEV_UI)
    public Overview overview() {
        HostSnapshot snapshot = host.snapshot();
        List<MountSummary> mounts = new ArrayList<>(snapshot.mounts().size());
        for (RegistrySnapshot mount : snapshot.mounts()) {
            mounts.add(new MountSummary(
                    mount.mount(),
                    mount.base(),
                    mount.activeSessions(),
                    mount.debugHtml(),
                    mount.serviceWorker()));
        }
        var renderer = dev.pathland.render.html.HtmlRenderer.tryInstance();
        String baseCss = renderer == null ? "" : renderer.baseCssName();
        return new Overview(
                snapshot.running(),
                snapshot.activeSessions(),
                List.copyOf(mounts),
                PathlandBuildInfo.domBundleName(),
                PathlandBuildInfo.domBundleIntegrity(),
                baseCss);
    }

    /** Every live session across every mount. */
    @JsonRpcDescription("List every live Pathland session across all mounted apps")
    @JsonRpcUsage(Usage.DEV_UI)
    public List<SessionSnapshot> sessions() {
        List<SessionSnapshot> sessions = new ArrayList<>();
        for (RegistrySnapshot mount : host.snapshot().mounts()) {
            sessions.addAll(mount.sessions());
        }
        return List.copyOf(sessions);
    }

    /** A one-line card summary: mounted apps and live sessions. */
    @JsonRpcDescription("A one-line summary of mounted apps and live sessions")
    @JsonRpcUsage(Usage.DEV_UI)
    public String overviewSummary() {
        HostSnapshot snapshot = host.snapshot();
        int apps = snapshot.mounts().size();
        int sessions = snapshot.activeSessions();
        return apps + (apps == 1 ? " app" : " apps") + " · "
                + sessions + (sessions == 1 ? " live session" : " live sessions");
    }

    /** The dynamic card label for the Sessions page. */
    @JsonRpcDescription("The number of live sessions")
    @JsonRpcUsage(Usage.DEV_UI)
    public String sessionsLabel() {
        return String.valueOf(host.activeSessions());
    }

    /** Re-send one session's current tree as a full snapshot ({@code META::RESYNC}). */
    @JsonRpcDescription("Re-send one session's current tree as a full snapshot")
    @JsonRpcUsage(Usage.DEV_UI)
    public ActionResult resync(String sessionId) {
        PathlandRegistry registry = registryFor(sessionId);
        if (registry == null) {
            return ActionResult.failure("unknown session: " + sessionId);
        }
        registry.resync(sessionId);
        return ActionResult.success();
    }

    /** Re-sync every live session. */
    @JsonRpcDescription("Re-send every live session's current tree as a full snapshot")
    @JsonRpcUsage(Usage.DEV_UI)
    public ActionResult resyncAll() {
        for (RegistrySnapshot mount : host.snapshot().mounts()) {
            PathlandRegistry registry = host.registry(mount.mount());
            if (registry == null) {
                continue;
            }
            for (SessionSnapshot session : mount.sessions()) {
                registry.resync(session.id());
            }
        }
        return ActionResult.success();
    }

    /** Close (and remove) one live session. */
    @JsonRpcDescription("Close and remove one live session")
    @JsonRpcUsage(Usage.DEV_UI)
    public ActionResult closeSession(String sessionId) {
        PathlandRegistry registry = registryFor(sessionId);
        if (registry == null) {
            return ActionResult.failure("unknown session: " + sessionId);
        }
        registry.close(sessionId);
        return ActionResult.success();
    }

    /** Trace entries newer than {@code sinceId} (polled by the wire page). */
    @JsonRpcDescription("Read runtime trace entries (batches, events, SSR renders, lifecycle)")
    @JsonRpcUsage(Usage.DEV_UI)
    public PathlandDevTraceStore.Snapshot trace(long sinceId, int limit) {
        return trace.snapshot(sinceId, limit);
    }

    /** Drop retained trace entries; returns the current high-water id. */
    @JsonRpcDescription("Clear the retained runtime trace")
    @JsonRpcUsage(Usage.DEV_UI)
    public long clearTrace() {
        trace.clear();
        return trace.lastId();
    }

    /** Render the SSR document for a route (no window scope — the defaults view). */
    @JsonRpcDescription("Render the SSR document for a route")
    @JsonRpcUsage(Usage.DEV_UI)
    public SsrPreview ssr(String mount, String route) {
        String mountPath = mount == null || mount.isBlank() ? "/" : mount;
        PathlandRegistry registry = host.registry(mountPath);
        if (registry == null) {
            return new SsrPreview(mountPath, route, null, "unknown mount: " + mountPath);
        }
        String requested = route == null || route.isBlank() ? "/" : route;
        String appRoute = registry.stripRoute(requested);
        try {
            return new SsrPreview(registry.mountPath(), appRoute, registry.renderHtml(appRoute), null);
        } catch (RuntimeException e) {
            return new SsrPreview(registry.mountPath(), appRoute, null, e.getMessage());
        }
    }

    /** Live topic subscriptions for a mount (or the default hub when blank). */
    @JsonRpcDescription("List live topic subscriptions per (topic, subject)")
    @JsonRpcUsage(Usage.DEV_UI)
    public List<PathlandSignalHub.SubscriptionCount> topics(String mount) {
        PathlandSignalHub hub = hubFor(mount);
        return hub == null ? List.of() : hub.subscriptions();
    }

    /**
     * Publish an untyped topic value (Dev UI tooling). {@code subjectType}/{@code valueType}
     * are {@code string}, {@code number}, {@code boolean}, or {@code null}.
     */
    @JsonRpcDescription("Publish a value to a (topic, subject) subscription")
    @JsonRpcUsage(Usage.DEV_UI)
    public ActionResult publishTopic(
            String mount,
            String topic,
            String subject,
            String subjectType,
            String valueType,
            String value) {
        PathlandSignalHub hub = hubFor(mount);
        if (hub == null) {
            return ActionResult.failure("unknown mount: " + mount);
        }
        if (topic == null || topic.isBlank()) {
            return ActionResult.failure("topic is required");
        }
        try {
            Object parsedSubject = parseValue(subjectType, subject);
            Object parsedValue = parseValue(valueType, value);
            hub.publishRaw(topic, parsedSubject, parsedValue);
            return ActionResult.success();
        } catch (RuntimeException e) {
            return ActionResult.failure(e.getMessage());
        }
    }

    /** Enumerate persisted state keys (InMemory state store; other stores may be unsupported). */
    @JsonRpcDescription("List persisted session state (keys, types, values)")
    @JsonRpcUsage(Usage.DEV_UI)
    public StateSnapshot state(String query) {
        if (!(store instanceof DebugStateStore debug)) {
            return new StateSnapshot(false, List.of(), "this StateStore does not support enumeration");
        }
        String filter = query == null ? "" : query.toLowerCase();
        List<StateEntry> entries = new ArrayList<>();
        for (String key : debug.keys()) {
            if (!filter.isEmpty() && !key.toLowerCase().contains(filter)) {
                continue;
            }
            Optional<Object> value = debug.get(key);
            Object raw = value.orElse(null);
            entries.add(new StateEntry(
                    key,
                    raw == null ? "null" : raw.getClass().getSimpleName(),
                    preview(raw)));
        }
        return new StateSnapshot(true, List.copyOf(entries), null);
    }

    /** Delete one persisted state key. */
    @JsonRpcDescription("Delete one persisted state key")
    @JsonRpcUsage(Usage.DEV_UI)
    public ActionResult deleteState(String key) {
        if (key == null || key.isBlank()) {
            return ActionResult.failure("key is required");
        }
        if (store == null) {
            return ActionResult.failure("no StateStore is available");
        }
        store.delete(key);
        return ActionResult.success();
    }

    /** Force/release a session's color scheme (platform/light/dark). */
    @JsonRpcDescription("Force a session's color scheme (platform, light, dark)")
    @JsonRpcUsage(Usage.DEV_UI)
    public ActionResult setColorScheme(String sessionId, String mode) {
        PathlandRegistry registry = registryFor(sessionId);
        if (registry == null) {
            return ActionResult.failure("unknown session: " + sessionId);
        }
        ThemeMode themeMode = switch (mode == null ? "" : mode.toLowerCase()) {
            case "light" -> ThemeMode.LIGHT;
            case "dark" -> ThemeMode.DARK;
            default -> ThemeMode.PLATFORM;
        };
        return registry.setColorScheme(sessionId, themeMode)
                ? ActionResult.success()
                : ActionResult.failure("unknown session: " + sessionId);
    }

    /** Clear a session's app token overrides (renderer defaults apply). */
    @JsonRpcDescription("Clear a session's app token overrides")
    @JsonRpcUsage(Usage.DEV_UI)
    public ActionResult resetTheme(String sessionId) {
        PathlandRegistry registry = registryFor(sessionId);
        if (registry == null) {
            return ActionResult.failure("unknown session: " + sessionId);
        }
        return registry.resetTheme(sessionId)
                ? ActionResult.success()
                : ActionResult.failure("unknown session: " + sessionId);
    }

    /** The retained tree of one session (component ids, text, properties, children). */
    @JsonRpcDescription("Inspect one session's retained tree")
    @JsonRpcUsage(Usage.DEV_UI)
    public TreeSnapshot tree(String sessionId) {
        PathlandRegistry registry = registryFor(sessionId);
        if (registry == null) {
            return new TreeSnapshot(sessionId, null, "unknown session: " + sessionId);
        }
        try {
            return new TreeSnapshot(sessionId, registry.treeSnapshot(sessionId), null);
        } catch (RuntimeException e) {
            return new TreeSnapshot(sessionId, null, e.getMessage());
        }
    }

    private PathlandSignalHub hubFor(String mount) {
        if (mount == null || mount.isBlank()) {
            return host.signalHub();
        }
        return host.signalHub(mount);
    }

    private static Object parseValue(String type, String text) {
        String kind = type == null ? "string" : type.toLowerCase();
        return switch (kind) {
            case "number" -> parseNumber(text);
            case "boolean" -> Boolean.valueOf(text);
            case "null" -> null;
            default -> text;
        };
    }

    private static Number parseNumber(String text) {
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            return Double.valueOf(text);
        }
    }

    private static String preview(Object value) {
        if (value == null) {
            return "null";
        }
        String text = String.valueOf(value);
        return text.length() > 500 ? text.substring(0, 500) + "…" : text;
    }

    private PathlandRegistry registryFor(String sessionId) {        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        for (PathlandRegistry registry : host.registries()) {
            if (registry.hasSession(sessionId)) {
                return registry;
            }
        }
        return null;
    }

    public record Overview(
            boolean running,
            int activeSessions,
            List<MountSummary> mounts,
            String domBundle,
            String domIntegrity,
            String baseCss) {
    }

    public record MountSummary(
            String mount,
            String base,
            int activeSessions,
            boolean debugHtml,
            boolean serviceWorker) {
    }

    public record SsrPreview(String mount, String route, String html, String error) {
    }

    public record StateEntry(String key, String type, String value) {
    }

    public record StateSnapshot(boolean supported, List<StateEntry> entries, String error) {
    }

    public record TreeSnapshot(String sessionId, NodeSnapshot root, String error) {
    }

    public record ActionResult(boolean ok, String error) {
        static ActionResult success() {
            return new ActionResult(true, null);
        }

        static ActionResult failure(String error) {
            return new ActionResult(false, error);
        }
    }
}
