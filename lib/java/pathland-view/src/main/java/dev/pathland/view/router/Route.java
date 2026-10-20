package dev.pathland.view.router;

import java.util.Objects;

/**
 * A navigation route: the absolute path that identifies a destination
 * (spec DSL.md §4.5). Path params (`/users/:id`) and query strings ride the raw
 * path string; the {@link RouteTable} captures params during matching and
 * passes them to the destination factory. A route is an immutable value —
 * routes are compared by path (signal equality suppression reuses it).
 *
 * @param path the absolute path, e.g. {@code /users/42} or {@code /search?q=ada}
 */
public record Route(String path) {

    public Route {
        Objects.requireNonNull(path, "path");
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("route paths must be absolute: " + path);
        }
    }

    /** A route for an absolute path. */
    public static Route of(String path) {
        return new Route(path);
    }

    /** The path with any query/fragment stripped (used to match the route table). */
    public String pathOnly() {
        int end = path.length();
        int query = path.indexOf('?');
        int fragment = path.indexOf('#');
        if (query >= 0) {
            end = Math.min(end, query);
        }
        if (fragment >= 0) {
            end = Math.min(end, fragment);
        }
        return path.substring(0, end);
    }
}