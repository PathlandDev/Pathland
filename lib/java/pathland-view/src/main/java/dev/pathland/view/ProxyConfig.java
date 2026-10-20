package dev.pathland.view;

import java.util.Objects;

/**
 * The configuration of a {@link ProxyView} (spec/PROXY.md): the origin server's
 * base endpoint, the route it should render, and the name of the service
 * credential the proxying server presents to the origin.
 *
 * <p>Immutable — snapshotted onto the retained node at render and exposed to the
 * host through {@code RenderResult.proxyViews()} so the proxy machinery can open
 * an origin connection per proxy node.
 *
 * @param endpoint   the origin server's base URL (e.g. {@code https://b.example}) —
 *                   app-configured, never client-supplied (SSRF guard, spec/PROXY.md §7)
 * @param route      the route the origin should render (defaults to {@code "/"})
 * @param credential a service-credential name the proxying server resolves and
 *                   presents on the origin handshake; {@code null} = none
 */
public record ProxyConfig(String endpoint, String route, String credential) {

    public ProxyConfig {
        Objects.requireNonNull(endpoint, "endpoint");
        if (endpoint.isBlank()) {
            throw new IllegalArgumentException("proxy endpoint must not be blank");
        }
        if (route == null || route.isBlank()) {
            route = "/";
        }
    }
}
