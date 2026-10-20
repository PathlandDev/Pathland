package dev.pathland.demo.quarkus;

import dev.pathland.demo.DemoTheme;
import dev.pathland.demo.ad.AdView;
import dev.pathland.demo.music.MusicPlayerView;
import dev.pathland.server.MountedApp;
import dev.pathland.server.PathlandApp;
import dev.pathland.view.ThemeData;
import dev.pathland.view.View;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.function.Supplier;

/**
 * The Quarkus demo — the whole app. The Pathland starter provides SSR, the per-app
 * reserved {@code /<path>/_pathland/ws} delta transport, and per-session state; this
 * bean only supplies the root views (and the optional theme).
 *
 * <p>Two apps share the server: the {@link MusicPlayerView} at {@code /} and a small
 * {@link AdView} at {@code /ad}. The player's Now Playing pane embeds the {@code /ad}
 * app via a {@code ProxyView} (spec/PROXY.md) — a <b>same-server</b> proxy: the
 * player's session connects (over a loopback WebSocket) to the {@code /ad} app's own
 * {@code /ad/_pathland/ws} endpoint, remaps its node ids into a disjoint range, and
 * splices its subtree in — so the ad is fully interactive in place.
 */
@ApplicationScoped
public class QuarkusDemoApp {

    /**
     * The {@code /ad} app's base URL — the proxy origin. Defaults to this server's own
     * address; override with {@code pathland.demo.ad-url} (e.g. a different host/port).
     */
    @ConfigProperty(name = "pathland.demo.ad-url", defaultValue = "http://localhost:8080/ad")
    String adUrl;

    @Produces
    @Singleton
    PathlandApp mainApp() {
        return demoApp(() -> MusicPlayerView.make(adUrl));
    }

    /** The origin app mounted at {@code /ad}: a small title + button app the player proxies. */
    @Produces
    @Singleton
    MountedApp adApp() {
        return MountedApp.of("/ad", demoApp(AdView::new));
    }

    /** A fresh root per session (1:1 sessions own their own retained tree + state). */
    private static PathlandApp demoApp(Supplier<View> rootFactory) {
        return new PathlandApp() {
            @Override
            public View newRoot() {
                return rootFactory.get();
            }

            @Override
            public ThemeData theme() {
                return DemoTheme.adaptive();
            }
        };
    }
}
