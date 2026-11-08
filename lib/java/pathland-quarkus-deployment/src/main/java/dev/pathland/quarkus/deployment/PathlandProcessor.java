package dev.pathland.quarkus.deployment;

import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigurationDefaultBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;

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

    private static final String CSP_KEY = "quarkus.http.header.\"Content-Security-Policy\".value";

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
     * The renderer-owned base design is copied into the {@code pathland-render-html}
     * jar under {@code dev/pathland/render/html/}, outside {@code META-INF/resources},
     * so GraalVM would otherwise drop it from a native image. Register both files
     * here so every Quarkus consumer of the extension gets a working
     * {@code HtmlRenderer.baseCss()} without app-level native-image configuration.
     */
    @BuildStep
    NativeImageResourceBuildItem nativeResources() {
        return new NativeImageResourceBuildItem(
                "dev/pathland/render/html/base.css",
                "dev/pathland/render/html/base.css.name");
    }

    /**
     * Secure-by-default transport and browser hardening. Quarkus's CORS filter runs
     * before every route (including WebSocket upgrades) and, with no {@code origins}
     * configured, enforces same-origin — the WebSocket CSRF control. The WebSocket
     * message/frame caps match the server-core inbound event-batch bound.
     *
     * <p>Forwarded headers are trusted by default so a TLS-terminating PaaS (Render,
     * Fly, Heroku, Cloud Run, …) works with zero configuration: the same-origin
     * check and canonical URLs then use the external {@code https} origin instead of
     * the internal {@code http} hop. These are defaults only — application
     * configuration always wins. A directly exposed deployment can set
     * {@code quarkus.http.proxy.proxy-address-forwarding=false} (or restrict
     * {@code quarkus.http.proxy.trusted-proxies}) to harden it.
     */
    @BuildStep
    List<RunTimeConfigurationDefaultBuildItem> securityDefaults() {
        return List.of(
                new RunTimeConfigurationDefaultBuildItem("quarkus.http.cors.enabled", "true"),
                new RunTimeConfigurationDefaultBuildItem(
                        "quarkus.http.proxy.proxy-address-forwarding", "true"),
                new RunTimeConfigurationDefaultBuildItem(
                        "quarkus.http.proxy.allow-x-forwarded", "true"),
                new RunTimeConfigurationDefaultBuildItem(
                        "quarkus.http.proxy.enable-forwarded-host", "true"),
                new RunTimeConfigurationDefaultBuildItem(
                        "quarkus.websockets-next.server.max-message-size", "65536"),
                new RunTimeConfigurationDefaultBuildItem(
                        "quarkus.websockets-next.server.max-frame-size", "65536"),
                new RunTimeConfigurationDefaultBuildItem(
                        "quarkus.http.header.\"X-Content-Type-Options\".value", "nosniff"),
                new RunTimeConfigurationDefaultBuildItem(
                        "quarkus.http.header.\"Referrer-Policy\".value", "no-referrer"),
                new RunTimeConfigurationDefaultBuildItem(
                        "quarkus.http.header.\"X-Frame-Options\".value", "DENY"));
    }

    /**
     * The strict same-origin CSP for test and production.
     *
     * <p>Deliberately <b>not</b> contributed in local development: the Quarkus Dev UI
     * is part of the served application there, and a strict CSP blocks its inline
     * import map, inline bootstrap scripts, and Google Fonts — leaving the Dev UI
     * stuck on its splash screen. Dev mode is not the production security surface, so
     * it gets Quarkus's normal (no-CSP) dev behavior; an application can still set its
     * own CSP in dev via {@code quarkus.http.header."Content-Security-Policy".value}.
     */
    @BuildStep(onlyIfNot = IsDevelopment.class)
    RunTimeConfigurationDefaultBuildItem contentSecurityPolicy() {
        return new RunTimeConfigurationDefaultBuildItem(CSP_KEY, DEFAULT_CSP);
    }
}
