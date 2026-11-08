package dev.pathland.core.transport;

/**
 * The platform's environment data: viewport size and the initial route, delivered
 * as {@code META::ENVIRONMENT} fields.
 */
public final class EnvironmentData {
    private final String route;
    private final float viewportWidth;
    private final float viewportHeight;

    public EnvironmentData(String route, float viewportWidth, float viewportHeight) {
        this.route = normalize(route);
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
    }

    public String route() {
        return route;
    }

    public float viewportWidth() {
        return viewportWidth;
    }

    public float viewportHeight() {
        return viewportHeight;
    }

    public String getRoute() {
        return route;
    }

    public float getViewportWidth() {
        return viewportWidth;
    }

    public float getViewportHeight() {
        return viewportHeight;
    }

    public static EnvironmentData of(String route) {
        return new EnvironmentData(route, -1f, -1f);
    }

    private static String normalize(String route) {
        String normalized = (route == null || route.isBlank()) ? "/" : route;
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }
}
