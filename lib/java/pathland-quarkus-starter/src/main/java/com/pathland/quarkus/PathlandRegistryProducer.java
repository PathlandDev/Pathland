package com.pathland.quarkus;

import com.pathland.server.PathlandApp;
import com.pathland.server.PathlandRegistry;
import com.pathland.server.StateStores;
import com.pathland.view.state.InMemoryStateStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * CDI wiring for Pathland: builds the {@link PathlandRegistry} from the app's
 * {@link PathlandApp} bean and the default state store (Redis when reachable, else
 * in-memory), and shuts it down with the application. The app provides the root view:
 *
 * <pre>{@code
 * @ApplicationScoped
 * public class MyApp implements PathlandApp {
 *     public View newRoot() { return new MyHomeView(); }
 * }
 * }</pre>
 *
 * <p>SSR debug comments are opt-in via the {@code pathland.debug-html} config property
 * (default {@code false}); when enabled every rendered node is prefixed with an HTML
 * comment naming its component type and the modifiers applied.
 */
@ApplicationScoped
public class PathlandRegistryProducer {

    @Inject
    PathlandApp app;

    @Inject
    @ConfigProperty(name = "pathland.debug-html", defaultValue = "false")
    boolean debugHtml;

    @Produces
    @Singleton
    PathlandRegistry registry() {
        return new PathlandRegistry(app, StateStores.redisOrFallback(new InMemoryStateStore()), debugHtml);
    }

    void shutdown(@Disposes PathlandRegistry registry) {
        registry.shutdown();
    }
}