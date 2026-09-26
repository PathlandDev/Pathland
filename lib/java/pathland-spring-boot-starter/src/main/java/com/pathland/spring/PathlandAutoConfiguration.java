package com.pathland.spring;

import com.pathland.server.PathlandApp;
import com.pathland.server.PathlandRegistry;
import com.pathland.server.StateStores;
import com.pathland.view.state.InMemoryStateStore;
import com.pathland.view.state.StateStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;

/**
 * Spring Boot auto-configuration for Pathland (spec DSL.md §4.5). Activated when the app
 * provides a {@link PathlandApp} bean — so adding the starter without a root view is a
 * no-op, and providing one wires SSR + live deltas end to end:
 *
 * <pre>{@code
 * @Bean PathlandApp pathlandApp() { return () -> new MyHomeView(); }
 * }</pre>
 *
 * <p>Every bean is {@code @ConditionalOnMissingBean}, so the app can override the
 * {@link StateStore}, the socket, or the controller. {@code @EnableWebSocket} is applied
 * here because Spring Boot 3.5 no longer auto-enables it (it was removed from the
 * WebSocket auto-configurations); it collects this configuration's {@link WebSocketConfigurer}.
 */
@AutoConfiguration
@ConditionalOnBean(PathlandApp.class)
@EnableWebSocket
public class PathlandAutoConfiguration {

    /** Default state store: Redis when reachable, else in-memory. */
    @Bean
    @ConditionalOnMissingBean(StateStore.class)
    public StateStore pathlandStateStore() {
        return StateStores.redisOrFallback(new InMemoryStateStore());
    }

    /** The per-session registry (shut down with the application context). */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public PathlandRegistry pathlandRegistry(
            PathlandApp app,
            StateStore store,
            @org.springframework.beans.factory.annotation.Value("${pathland.debug-html:false}") boolean debugHtml) {
        return new PathlandRegistry(app, store, debugHtml);
    }

    /** The {@code /ws} WebSocket handler. */
    @Bean
    @ConditionalOnMissingBean
    public PathlandSocket pathlandSocket(PathlandRegistry registry) {
        return new PathlandSocket(registry);
    }

    /** Registers the socket at the reserved {@code /_pathland/ws} path. */
    @Bean
    public WebSocketConfigurer pathlandWebSocketConfigurer(PathlandSocket socket) {
        return registry -> registry.addHandler(socket, "/_pathland/ws").setAllowedOrigins("*");
    }

    /** The SSR catch-all + static JS bundle. */
    @Bean
    @ConditionalOnMissingBean
    public PathlandIndexController pathlandIndexController(PathlandRegistry registry) {
        return new PathlandIndexController(registry);
    }
}