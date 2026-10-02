package com.pathland.demo.quarkus;

import com.pathland.demo.assets.DemoAssets;
import io.quarkus.runtime.StartupEvent;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.FileSystemAccess;
import io.vertx.ext.web.handler.StaticHandler;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Path;

/**
 * Serves the demo's media + icon assets from a **temp-dir extraction** of the
 * shared {@code pathland-demo-views} jar — fast disk serving with `Range` support
 * (audio seek), `Content-Length`, and caching — instead of Quarkus's jar-based
 * static handler, which reads the (larger) media entries from inside the jar on
 * every request and is slow on a remote host.
 *
 * <p>Extraction happens once at startup (transient, per boot); the embedded copy
 * stays the single source of truth. The route preempts Quarkus's
 * {@code META-INF/resources} handler for {@code /_pathland/assets/*}.
 */
@Singleton
public class DemoAssetsRoute {

    @Inject
    Router router;

    void onStart(@Observes StartupEvent event) {
        Path root = DemoAssets.extractToTemp();
        router.route("/_pathland/assets/*")
                .order(-10)
                .handler(StaticHandler.create(FileSystemAccess.ROOT, root.resolve("assets").toString()));
    }
}