package dev.pathland.quarkus.devui;

import dev.pathland.server.PathlandBuildInfo;
import dev.pathland.server.PathlandHost;
import dev.pathland.server.PathlandRegistry;
import dev.pathland.server.introspection.PathlandIntrospection.HostSnapshot;
import dev.pathland.server.introspection.PathlandIntrospection.RegistrySnapshot;
import dev.pathland.server.introspection.PathlandIntrospection.SessionSnapshot;
import io.quarkus.runtime.annotations.JsonRpcDescription;
import io.quarkus.runtime.annotations.JsonRpcUsage;
import io.quarkus.runtime.annotations.Usage;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

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

    /** For tests / direct construction (CDI uses the no-arg constructor + field injection). */
    PathlandDevUiRpc(PathlandHost host) {
        this(host, new PathlandDevTraceStore());
    }

    PathlandDevUiRpc(PathlandHost host, PathlandDevTraceStore trace) {
        this.host = host;
        this.trace = trace;
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

    private PathlandRegistry registryFor(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
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

    public record ActionResult(boolean ok, String error) {
        static ActionResult success() {
            return new ActionResult(true, null);
        }

        static ActionResult failure(String error) {
            return new ActionResult(false, error);
        }
    }
}
