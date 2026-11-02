package dev.pathland.quarkus;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.core.buffer.Buffer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.io.IOException;
import java.io.InputStream;

/**
 * Serves the per-window-id service worker (issue #155) with the headers a root-scoped
 * registration requires.
 *
 * <p>The DOM client registers {@code {base}/sw.js} with {@code scope: "/"}; a script
 * served from under {@code /_pathland/} may only claim the root scope when the response
 * carries {@code Service-Worker-Allowed: /}. Quarkus serves {@code /_pathland/**} from
 * static resources, which sends neither that header nor {@code Cache-Control: no-cache}
 * (and a configured static max-age would otherwise cache the script, delaying the
 * browser's update detection) — so this filter intercepts that one path. A
 * {@link Filters} handler always runs before routes (static resources live at
 * {@code RouteConstants.ROUTE_ORDER_DEFAULT = 10000}).
 *
 * <p>Fail-open: when no worker script is on the classpath the request falls through to
 * the static handler unchanged.
 */
@ApplicationScoped
public class ServiceWorkerFilter {

    /** The worker script, as Quarkus serves static resources from {@code META-INF/resources}. */
    private static final String RESOURCE = "/META-INF/resources/_pathland/sw.js";

    /** Matches the root base ({@code /_pathland/sw.js}) and every mounted base. */
    private static final String PATH_SUFFIX = "/_pathland/sw.js";

    void init(@Observes Filters filters) {
        filters.register(context -> {
            if (!context.normalizedPath().endsWith(PATH_SUFFIX)) {
                context.next();
                return;
            }
            byte[] script = readResource();
            if (script == null) {
                context.next(); // fail open: no worker script on this host
                return;
            }
            context.response()
                    .putHeader("Content-Type", "text/javascript;charset=UTF-8")
                    // Let the script claim the ROOT scope (it lives under /_pathland/).
                    .putHeader("Service-Worker-Allowed", "/")
                    // Never cache: the browser re-fetches the script to byte-compare updates.
                    .putHeader("Cache-Control", "no-cache")
                    .end(Buffer.buffer(script));
        }, 100);
    }

    private static byte[] readResource() {
        try (InputStream in = ServiceWorkerFilter.class.getResourceAsStream(RESOURCE)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }
}
