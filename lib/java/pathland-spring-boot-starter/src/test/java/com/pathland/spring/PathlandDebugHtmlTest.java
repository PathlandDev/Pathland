package com.pathland.spring;

import com.pathland.server.PathlandApp;
import com.pathland.server.PathlandRegistry;
import com.pathland.view.Text;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code pathland.debug-html} property gates SSR debug comments: with it set,
 * the auto-configured {@link PathlandRegistry} renders per-node HTML comments; the
 * default stays off.
 */
@SpringBootTest(classes = PathlandDebugHtmlTest.TestApp.class)
@TestPropertySource(properties = "pathland.debug-html=true")
class PathlandDebugHtmlTest {

    @SpringBootApplication
    static class TestApp {
        @Bean
        PathlandApp pathlandApp() {
            return () -> Text.of("Hello Pathland");
        }
    }

    @Autowired
    PathlandRegistry registry;

    @Test
    void debugHtmlPropertyReachesTheRegistry() {
        assertTrue(registry.isDebugHtml(), "pathland.debug-html=true turns debug comments on");
    }

    @Test
    void defaultRegistryHasDebugOff() {
        assertFalse(new PathlandRegistry(
                () -> Text.of("x"), new com.pathland.view.state.InMemoryStateStore()).isDebugHtml());
    }
}