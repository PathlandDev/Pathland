package dev.pathland.server;

/**
 * What an app knows when resolving per-route SSR document metadata.
 *
 * @param route the active route with the app's mount prefix stripped
 *              ({@code /app2/library} → {@code /library})
 * @param canonicalUrl the host-derived absolute canonical URL for this request,
 *                     or {@code null} when the host did not supply one
 * @param mountPath the app's mount path ({@code /} for the root app)
 */
public record PageMetadataRequest(String route, String canonicalUrl, String mountPath) {}
