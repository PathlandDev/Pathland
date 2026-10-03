package com.pathland.server;

import com.pathland.view.state.StateStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hosts any number of {@link MountedApp}s in one server process — the BFF: one
 * process serves several Pathland apps, each at its own subpath. Every mount gets
 * its own {@link PathlandRegistry} (own actor thread, own sessions, own framework
 * base {@code /<path>/_pathland}) and its own state-store scope, so the apps are
 * fully isolated.
 *
 * <p>{@link #match} dispatches an inbound request to the app whose mount path is the
 * longest prefix of the request, and reports the <b>mount-stripped</b> route the app
 * should see ({@code /app2/home} → registry for {@code /app2}, route {@code /home}).
 * A root mount ({@code "/"}) is the fallback and claims every path no longer
 * (or shorter) mount owns.
 */
public final class PathlandHost {

    private final List<PathlandRegistry> registries;
    private final Map<String, PathlandRegistry> byMount;
    private final PathlandTelemetry telemetry;
    private volatile boolean running = true;

    public PathlandHost(List<MountedApp> mounts, StateStore store, boolean debugHtml) {
        this(mounts, store, debugHtml, PathlandTelemetry.NOOP);
    }

    public PathlandHost(List<MountedApp> mounts, StateStore store, boolean debugHtml, PathlandTelemetry telemetry) {
        this.telemetry = telemetry == null ? PathlandTelemetry.NOOP : telemetry;
        List<MountedApp> normalized = new ArrayList<>(mounts == null ? List.of() : mounts);
        this.registries = new ArrayList<>(normalized.size());
        this.byMount = new LinkedHashMap<>();
        for (MountedApp mount : normalized) {
            String path = mount.path();
            if (byMount.putIfAbsent(path, null) != null) {
                throw new IllegalArgumentException("duplicate mount path: " + path);
            }
            PathlandRegistry registry = new PathlandRegistry(path, mount.app(), store, debugHtml, this.telemetry);
            byMount.put(path, registry);
            registries.add(registry);
        }
        registries.sort(Comparator.comparingInt((PathlandRegistry r) -> r.mountPath().length()).reversed());
        // Bind the active-session gauge (a pull-based Micrometer gauge, if any).
        this.telemetry.bindActiveSessions(this::activeSessions);
    }

    public static PathlandHost of(StateStore store, boolean debugHtml, MountedApp... mounts) {
        return new PathlandHost(List.of(mounts), store, debugHtml);
    }

    /** The registry for an exact mount path (e.g. {@code "/app2"}), or {@code null}. */
    public PathlandRegistry registry(String mountPath) {
        return byMount.get(mountPath == null ? "/" : mountPath);
    }

    /** Every registry, longest mount first. */
    public List<PathlandRegistry> registries() {
        return registries;
    }

    /**
     * Resolve the app owning a request URI: the longest matching mount prefix wins; a
     * root mount claims everything else. {@code null} when no mount matches (no root
     * mount and the path is outside every mount).
     *
     * @return the owning registry + the mount-stripped route, or {@code null}
     */
    public MountMatch match(String requestUri) {
        String uri = requestUri == null || requestUri.isBlank() ? "/" : requestUri;
        PathlandRegistry best = null;
        for (PathlandRegistry candidate : registries) { // longest first
            String mount = candidate.mountPath();
            if (matches(mount, uri)) {
                best = candidate;
                break;
            }
        }
        return best == null ? null : new MountMatch(best, strip(best.mountPath(), uri));
    }

    /** The number of live sessions across every mount (a health/metrics gauge). */
    public int activeSessions() {
        int total = 0;
        for (PathlandRegistry registry : registries) {
            total += registry.activeSessions();
        }
        return total;
    }

    /** Whether the host is accepting work (false after {@link #shutdown()}). */
    public boolean isRunning() {
        return running;
    }

    /** Close every registry (every session, every actor). */
    public void shutdown() {
        running = false;
        for (PathlandRegistry registry : registries) {
            registry.shutdown();
        }
    }

    private static boolean matches(String mount, String uri) {
        return "/".equals(mount)
                ? uri.startsWith("/")
                : uri.equals(mount) || uri.startsWith(mount + "/");
    }

    private static String strip(String mount, String uri) {
        if ("/".equals(mount)) {
            return uri;
        }
        if (uri.equals(mount)) {
            return "/";
        }
        return uri.substring(mount.length()); // uri starts with mount + "/"
    }

    /** An inbound request resolved to its owning registry + mount-stripped route. */
    public record MountMatch(PathlandRegistry registry, String route) {
    }
}