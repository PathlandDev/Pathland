package com.pathland.server;

/**
 * One Pathland application mounted at a subpath prefix. The server runtime hosts any
 * number of these: each gets its own {@link PathlandRegistry}, its own framework base
 * ({@code /<path>/_pathland}), per-app state-store scoping, and its own WebSocket
 * endpoint. {@code "/"} mounts the app at the root (the classic single-app layout).
 *
 * <pre>{@code
 * MountedApp.of("/", rootApp);   // the main app owns every path not claimed by a mount
 * MountedApp.of("/app2", other); // a second app served under /app2/**
 * }</pre>
 *
 * <p>The mount path is normalized (leading slash, no trailing slash). App routes seen by
 * the mounted app are <b>relative to the mount</b>: a request for {@code /app2/home} is
 * routed to the app mounted at {@code /app2} with the route {@code /home}.
 */
public record MountedApp(String path, PathlandApp app) {

    public MountedApp {
        path = normalize(path);
        if (app == null) {
            throw new IllegalArgumentException("MountedApp requires an app");
        }
    }

    public static MountedApp of(String path, PathlandApp app) {
        return new MountedApp(path, app);
    }

    private static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}