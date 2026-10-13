package dev.pathland.server;

import dev.pathland.view.ThemeData;
import dev.pathland.view.View;

/**
 * The application's root-view factory — the single app-specific piece the server
 * runtime needs. A host (Spring Boot / Quarkus starter) provides one as a bean; the
 * runtime mounts a fresh root per session (views hold per-session {@code State}).
 *
 * <pre>{@code
 * @Bean PathlandApp pathlandApp() { return () -> new MyHomeView(); }
 * }</pre>
 */
@FunctionalInterface
public interface PathlandApp {

    /** A fresh root view for a new session. */
    View newRoot();

    /** The optional global theme (design-token overrides); {@code null} for none. */
    default ThemeData theme() {
        return null;
    }
}