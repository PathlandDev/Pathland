package dev.pathland.view.transport;

/**
 * The platform's environment data (spec/OPCODE.md §Environment fields): the launch
 * context a host/renderer hands the application — viewport size and the initial
 * route. Delivered uniformly as a `META::ENVIRONMENT` field batch:
 *
 * <ul>
 *   <li><b>SSR</b> — the host synthesizes it from the HTTP request ({@code ROUTE} =
 *       the request path, the only field an HTTP GET offers);</li>
 *   <li><b>WebSocket</b> — the DOM client sends it (viewport + {@code ROUTE}) as its
 *       first message and re-sends it to <b>enrich</b> the environment after connect
 *       (a window resize re-emits the viewport fields; future platform fields ride
 *       the same message).</li>
 * </ul>
 *
 * @param route          the initial route / deep-link path (never null; defaults to {@code /})
 * @param viewportWidth  logical points; {@code -1} when unknown (SSR)
 * @param viewportHeight logical points; {@code -1} when unknown (SSR)
 */
public record EnvironmentData(String route, float viewportWidth, float viewportHeight) {

    public EnvironmentData {
        route = route == null || route.isBlank() ? "/" : route;
        if (!route.startsWith("/")) {
            route = "/" + route;
        }
    }

    /** An environment with only the initial route (viewport unknown — the SSR case). */
    public static EnvironmentData of(String route) {
        return new EnvironmentData(route, -1f, -1f);
    }
}