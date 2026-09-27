package com.pathland.quarkus;

import com.pathland.server.MountedApp;
import com.pathland.server.PathlandApp;
import com.pathland.server.PathlandHost;
import com.pathland.server.StateStores;
import com.pathland.view.state.InMemoryStateStore;
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
    @ConfigProperty(name = "pathland.debug-html", defaultValue = "false")
    boolean debugHtml;

    @Produces
    @Singleton
    PathlandHost host() {
        List<MountedApp> list = new ArrayList<>();
        mounts.forEach(list::add);
        PathlandApp lone = apps.stream().findFirst().orElse(null);
        if (lone != null && list.stream().noneMatch(m -> "/".equals(m.path()))) {
            list.add(0, MountedApp.of("/", lone));
        }
        return new PathlandHost(list, StateStores.redisOrFallback(new InMemoryStateStore()), debugHtml);
    }

    void shutdown(@Disposes PathlandHost host) {
        host.shutdown();
    }
}