package dev.pathland.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Hosted-friendly default for forwarded headers.
 *
 * <p>TLS-terminating platforms (Render, Fly, Heroku, Cloud Run, …) forward plain
 * HTTP with {@code X-Forwarded-Proto}/{@code X-Forwarded-Host}. Without Spring's
 * forwarded-header support, the WebSocket same-origin check compares the browser's
 * {@code https} origin against the internal {@code http} hop and rejects the
 * handshake (403), and SSR canonical URLs use the internal origin.
 *
 * <p>This contributes {@code server.forward-headers-strategy=framework} as a
 * <b>lowest-precedence</b> property source, so it works with zero configuration
 * while any explicit application setting (including
 * {@code server.forward-headers-strategy=native|none}) still wins. It only fills a
 * default; it never overrides application configuration.
 */
public final class PathlandEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String STRATEGY = "server.forward-headers-strategy";
    static final String SOURCE_NAME = "pathlandForwardedHeadersDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getProperty(STRATEGY) != null) {
            return;
        }
        // Runs after ConfigDataEnvironmentPostProcessor (LOWEST_PRECEDENCE) and appends
        // last, so application.properties/YAML/env/system properties always win.
        environment.getPropertySources().addLast(
                new MapPropertySource(SOURCE_NAME, Map.of(STRATEGY, "framework")));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
