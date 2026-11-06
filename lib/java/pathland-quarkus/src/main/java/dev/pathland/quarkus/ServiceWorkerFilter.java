package dev.pathland.quarkus;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.core.buffer.Buffer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

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
 *
 * <p><b>Classloading:</b> extension runtime classes live in Quarkus's base runtime
 * classloader, while the application's {@code META-INF/resources} entries (and the
 * bundled worker copy) are in the application classloader. Resolve through the
 * thread-context classloader first, then this class's loader, then the system loader —
 * resolving only against {@code ServiceWorkerFilter.class} misses the app resource in a
 * packaged app (the static handler then serves the file without the required header).
 */
@ApplicationScoped
public class ServiceWorkerFilter {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceWorkerFilter.class);

    /** The worker script, as Quarkus serves static resources from {@code META-INF/resources}. */
    private static final String RESOURCE = "META-INF/resources/_pathland/sw.js";

    /** Log a missing worker once, not on every request. */
    private static final AtomicBoolean MISSING_LOGGED = new AtomicBoolean();

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
        byte[] script = readResource(contextLoader());
        if (script == null) {
            script = readResource(ServiceWorkerFilter.class.getClassLoader());
        }
        if (script == null) {
            script = readResource(ClassLoader.getSystemClassLoader());
        }
        if (script == null && MISSING_LOGGED.compareAndSet(false, true)) {
            LOG.warn("Pathland service worker script ({}) is not on the classpath; "
                    + "the static handler will serve it without Service-Worker-Allowed",
                    RESOURCE);
        }
        return script;
    }

    private static byte[] readResource(ClassLoader loader) {
        if (loader == null) {
            return null;
        }
        try (InputStream in = loader.getResourceAsStream(RESOURCE)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private static ClassLoader contextLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader != null ? loader : ServiceWorkerFilter.class.getClassLoader();
    }
}
