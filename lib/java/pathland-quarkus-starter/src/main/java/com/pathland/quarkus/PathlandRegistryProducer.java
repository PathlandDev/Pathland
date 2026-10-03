package com.pathland.quarkus;

import com.pathland.observability.MicrometerTelemetry;
import com.pathland.observability.TracingTelemetry;
import com.pathland.server.MountedApp;
import com.pathland.server.PathlandApp;
import com.pathland.server.PathlandHost;
import com.pathland.server.PathlandTelemetry;
import com.pathland.server.StateStores;
import com.pathland.view.state.InMemoryStateStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * CDI wiring for Pathland: builds the {@link PathlandHost} from the app's
 * {@link PathlandApp} bean (mounted at {@code "/"}) and any {@link MountedApp} beans
 * (each at its own subpath), and shuts it down with the application. The app provides
 * the root view:
 *
 * <pre>{@code
 * @ApplicationScoped
 * public class MyApp implements PathlandApp {
 *     public View newRoot() { return new MyHomeView(); }
 * }
 * }</pre>
 *
 * <p>Multiple apps share the server the same way (each at its own mount, with its own
 * framework base, WebSocket endpoint, and state scope):
 *
 * <pre>{@code
 * @Produces @Singleton
 * MountedApp support() { return MountedApp.of("/support", new SupportApp()); }
 * }</pre>
 *
 * <p>SSR debug comments are opt-in via the {@code pathland.debug-html} config property
 * (default {@code false}); when enabled every rendered node is prefixed with an HTML
 * comment naming its component type and the modifiers applied.
 */
@ApplicationScoped
public class PathlandRegistryProducer {

    @Inject
    Instance<PathlandApp> apps;

    @Inject
    Instance<MountedApp> mounts;

    @Inject
    MeterRegistry meterRegistry;

    /** Present only when the app adds {@code quarkus-opentelemetry} (opt-in tracing). */
    @Inject
    Instance<Tracer> tracer;

    /** Tracing is opt-in: never touch the OTel API when the extension is disabled. */
    @Inject
    @ConfigProperty(name = "quarkus.otel.enabled", defaultValue = "true")
    boolean otelEnabled;

    @Inject
    @ConfigProperty(name = "pathland.debug-html", defaultValue = "false")
    boolean debugHtml;

    /**
     * The observability seam: a Micrometer adapter over the app's {@link MeterRegistry}
     * (always present via {@code quarkus-micrometer}), decorated with OpenTelemetry spans
     * when a {@link Tracer} is available and tracing is enabled.
     */
    @Produces
    @Singleton
    PathlandTelemetry telemetry() {
        PathlandTelemetry telemetry = new MicrometerTelemetry(meterRegistry);
        if (otelEnabled && tracer.isResolvable()) {
            telemetry = new TracingTelemetry(telemetry, tracer.get());
        }
        return telemetry;
    }

    @Produces
    @Singleton
    PathlandHost host(PathlandTelemetry telemetry) {
        List<MountedApp> list = new ArrayList<>();
        mounts.forEach(list::add);
        PathlandApp lone = apps.stream().findFirst().orElse(null);
        if (lone != null && list.stream().noneMatch(m -> "/".equals(m.path()))) {
            list.add(0, MountedApp.of("/", lone));
        }
        return new PathlandHost(list, StateStores.redisOrFallback(new InMemoryStateStore()), debugHtml, telemetry);
    }

    void shutdown(@Disposes PathlandHost host) {
        host.shutdown();
    }
}