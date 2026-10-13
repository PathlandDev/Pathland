package dev.pathland.demo.quarkus;

import dev.pathland.demo.DemoTheme;
import dev.pathland.demo.music.MusicPlayerView;
import dev.pathland.server.MountedApp;
import dev.pathland.server.PathlandApp;
import dev.pathland.view.ThemeData;
import dev.pathland.view.View;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * The Quarkus demo — the whole app. The Pathland starter provides SSR, the per-app
 * reserved {@code /<path>/_pathland/ws} delta transport, and per-session state; this
 * bean only supplies the root views (and the optional theme). Two apps share the
 * server: the main {@link MusicPlayerView} at {@code /} and a second instance at
 * {@code /app2} — each with its own framework base, WebSocket endpoint, per-window
 * persisted-state scope, and isolated per-connection UI models.
 */
@ApplicationScoped
public class QuarkusDemoApp {

    @Produces
    @Singleton
    PathlandApp mainApp() {
        return demoApp();
    }

    /** A second app mounted at {@code /app2} (the BFF layout: one server, many apps). */
    @Produces
    @Singleton
    MountedApp secondaryApp() {
        return MountedApp.of("/app2", demoApp());
    }

    private static PathlandApp demoApp() {
        return new PathlandApp() {
            @Override
            public View newRoot() {
                return new MusicPlayerView();
            }

            @Override
            public ThemeData theme() {
                return DemoTheme.adaptive();
            }
        };
    }
}