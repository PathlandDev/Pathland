package com.pathland.demo.spring;

import com.pathland.demo.DemoTheme;
import com.pathland.demo.music.MusicPlayerView;
import com.pathland.server.MountedApp;
import com.pathland.server.PathlandApp;
import com.pathland.view.ThemeData;
import com.pathland.view.View;
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