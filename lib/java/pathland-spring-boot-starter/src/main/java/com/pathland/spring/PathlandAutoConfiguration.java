package com.pathland.spring;

import com.pathland.observability.MicrometerTelemetry;
import com.pathland.observability.TracingTelemetry;
import com.pathland.server.MountedApp;
import com.pathland.server.PathlandApp;
import com.pathland.server.PathlandHost;
import com.pathland.server.PathlandTelemetry;
import com.pathland.server.StateStores;
import com.pathland.view.state.InMemoryStateStore;
import com.pathland.view.state.StateStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring Boot auto-configuration for Pathland (spec DSL.md §4.5). Activated whenever the
 * starter is on the classpath; without any app it stays inert (an empty host — no SSR, no
 * WebSocket handlers). Declaring a root view wires SSR + live deltas end to end:
 *
 * <pre>{@code
 * @Bean PathlandApp pathlandApp() { return () -> new MyHomeView(); }
 * }</pre>
 *
 * <p>Any number of apps can share the server, each at its own subpath (the BFF layout):
 *
 * <pre>{@code
 * @Bean PathlandApp pathlandApp() { return () -> new HomeApp(); }        // owns /
 * @Bean MountedApp support() { return MountedApp.of("/support", ...); }  // serves /support/**
 * }</pre>
 *
 * <p>Each mount gets its own registry, framework base ({@code /<path>/_pathland}), session
 * cookie scope, WebSocket endpoint, and state-store scope. The lone {@link PathlandApp}
 * bean mounts at {@code "/"} unless an explicit {@code MountedApp} claims the root.
 *
 * <p>Every bean is {@code @ConditionalOnMissingBean}, so the app can override the
 * {@link StateStore}, the controller, or the socket wiring. {@code @EnableWebSocket} is
 * applied here because Spring Boot 3.5 no longer auto-enables it (it was removed from
 * the WebSocket auto-configurations); it collects this configuration's {@link WebSocketConfigurer}.
 */
@AutoConfiguration
@EnableWebSocket
public class PathlandAutoConfiguration {

    /** Default state store: Redis when reachable, else in-memory. */
    @Bean
    @ConditionalOnMissingBean(StateStore.class)
    public StateStore pathlandStateStore() {
        return StateStores.redisOrFallback(new InMemoryStateStore());
    }

    /**
     * The observability seam: a Micrometer adapter over the actuator {@link MeterRegistry},
     * decorated with OpenTelemetry spans when an {@link OpenTelemetry} bean is present
     * (the app adds {@code micrometer-tracing-bridge-otel} to opt in).
     */
    @Bean
    @ConditionalOnMissingBean(PathlandTelemetry.class)
    public PathlandTelemetry pathlandTelemetry(
            MeterRegistry registry, ObjectProvider<OpenTelemetry> openTelemetry) {
        PathlandTelemetry telemetry = new MicrometerTelemetry(registry);
        OpenTelemetry otel = openTelemetry.getIfAvailable();
        if (otel != null) {
            telemetry = new TracingTelemetry(telemetry, otel.getTracer("pathland"));
        }
        return telemetry;
    }

    /** Readiness for the Pathland host (exposed at {@code /actuator/health}). */
    @Bean
    @ConditionalOnMissingBean(name = "pathlandHealthIndicator")
    public HealthIndicator pathlandHealthIndicator(PathlandHost host) {
        return () -> {
            Health.Builder builder = host.isRunning() ? Health.up() : Health.down();
            return builder
                    .withDetail("activeSessions", host.activeSessions())
                    .withDetail("mounts", host.registries().size())
                    .build();
        };
    }

    /**
     * The multi-app host (shut down with the application context): every {@link MountedApp}
     * bean plus the lone {@link PathlandApp} bean (mounted at {@code "/"}, unless an
     * explicit {@code MountedApp} claims the root).
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public PathlandHost pathlandHost(
            ObjectProvider<PathlandApp> apps,
            ObjectProvider<MountedApp> mounts,
            StateStore store,
            PathlandTelemetry telemetry,
            @Value("${pathland.debug-html:false}") boolean debugHtml) {
        List<MountedApp> list = new ArrayList<>();
        mounts.orderedStream().forEach(list::add);
        PathlandApp lone = apps.getIfAvailable();
        if (lone != null && list.stream().noneMatch(m -> "/".equals(m.path()))) {
            list.add(0, MountedApp.of("/", lone));
        }
        return new PathlandHost(list, store, debugHtml, telemetry);
    }

    /**
     * Registers one WebSocket handler per mounted app at its framework base
     * ({@code /_pathland/ws} for the root mount, {@code /<path>/_pathland/ws} otherwise).
     */
    @Bean
    @ConditionalOnMissingBean
    public WebSocketConfigurer pathlandWebSocketConfigurer(PathlandHost host) {
        return registry -> host.registries().forEach(r ->
                registry.addHandler(new PathlandSocket(r), r.base() + "/ws").setAllowedOrigins("*"));
    }

    /** The SSR dispatcher (per-app prefix routing) + the shared static JS bundle. */
    @Bean
    @ConditionalOnMissingBean
    public PathlandIndexController pathlandIndexController(PathlandHost host) {
        return new PathlandIndexController(host);
    }
}