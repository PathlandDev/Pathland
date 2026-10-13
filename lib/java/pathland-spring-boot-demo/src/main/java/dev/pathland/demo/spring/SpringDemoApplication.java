package dev.pathland.demo.spring;

import dev.pathland.demo.DemoTheme;
import dev.pathland.demo.assets.DemoAssets;
import dev.pathland.demo.music.MusicPlayerView;
import dev.pathland.server.MountedApp;
import dev.pathland.server.PathlandApp;
import dev.pathland.view.ThemeData;
import dev.pathland.view.View;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * The Spring Boot demo — the whole app. The Pathland starter provides SSR, the per-app
 * reserved {@code /<path>/_pathland/ws} delta transport, and per-session state; this
 * class only supplies the root views (and the optional theme). Two apps share the
 * server: the main {@link MusicPlayerView} at {@code /} and a second instance at
 * {@code /app2} — each with its own framework base, WebSocket endpoint, per-window
 * persisted-state scope, and isolated per-connection UI models.
 */
@SpringBootApplication
public class SpringDemoApplication {

    public static void main(String[] args) {
        // The demo's media + icons are served from a temp-dir extraction of the
        // shared demo-views jar (fast disk serving with Range, spec the starter's
        // PathlandIndexController); extract once, before the app boots.
        System.setProperty("pathland.asset-dir", DemoAssets.extractToTemp().toString());
        SpringApplication.run(SpringDemoApplication.class, args);
    }

    /** The app's root view factory + theme — the only app-specific wiring. */
    @Bean
    PathlandApp pathlandApp() {
        return demoApp();
    }

    /** A second app mounted at {@code /app2} (the BFF layout: one server, many apps). */
    @Bean
    MountedApp pathlandApp2() {
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