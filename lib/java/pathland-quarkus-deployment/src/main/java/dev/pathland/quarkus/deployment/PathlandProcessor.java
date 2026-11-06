package dev.pathland.quarkus.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigurationDefaultBuildItem;

import java.util.List;

/**
 * Minimal deployment module for the DSL-neutral Pathland Quarkus extension.
 *
 * <p>The runtime module carries the CDI beans and HTTP/WebSocket endpoints. This
 * build step keeps the extension visible to Quarkus and supplies the framework-native
 * security defaults described in {@code docs/HARDENING.md}. Every default is
 * overridable by application configuration.
 */
public final class PathlandProcessor {

    private static final String FEATURE = "pathland";

    /**
     * A same-origin baseline CSP. Inline styles are unavoidable (the protocol emits
     * style attributes and {@code <style data-pathland-*>} blocks), so only
     * {@code style-src} opts into {@code 'unsafe-inline'}; scripts stay self-only.
     */
    private static final String DEFAULT_CSP = String.join(" ",
            "default-src 'self';",
            "script-src 'self';",
            "style-src 'self' 'unsafe-inline';",
            "img-src 'self' data:;",
            "media-src 'self';",
            "font-src 'self';",
            "connect-src 'self' ws: wss:;",
            "object-src 'none';",
            "base-uri 'self';",
            "form-action 'self';",
            "frame-ancestors 'none';",
            "worker-src 'self'");

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Secure-by-default transport and browser hardening. Quarkus's CORS filter runs
     * before every route (including WebSocket upgrades) and, with no {@code origins}
     * configured, enforces same-origin — the WebSocket CSRF control. The WebSocket
     * message/frame caps match the server-core inbound event-batch bound.
     */
    @BuildStep
    List<RunTimeConfigurationDefaultBuildItem> securityDefaults() {
        return List.of(
                new RunTimeConfigurationDefaultBuildItem("quarkus.http.cors.enabled", "true"),
                new RunTimeConfigurationDefaultBuildItem(
                        "quarkus.websockets-next.server.max-message-size", "65536"),
                new RunTimeConfigurationDefaultBuildItem(
                        "quarkus.websockets-next.server.max-frame-size", "65536"),
                new RunTimeConfigurationDefaultBuildItem(
                        "quarkus.http.header.\"Content-Security-Policy\".value", DEFAULT_CSP),
                new RunTimeConfigurationDefaultBuildItem(
                        "quarkus.http.header.\"X-Content-Type-Options\".value", "nosniff"),
                new RunTimeConfigurationDefaultBuildItem(
                        "quarkus.http.header.\"Referrer-Policy\".value", "no-referrer"),
                new RunTimeConfigurationDefaultBuildItem(
                        "quarkus.http.header.\"X-Frame-Options\".value", "DENY"));
    }
}
