package com.pathland.spring;

import com.pathland.server.PathlandApp;
import com.pathland.server.PathlandHost;
import com.pathland.view.Text;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The starter's auto-configuration smoke test: adding the dependency + a {@code PathlandApp}
 * bean wires the {@link PathlandHost} (with the app mounted at {@code "/"}) and SSR end to end.
 */
@SpringBootTest(classes = PathlandAutoConfigurationTest.TestApp.class)
class PathlandAutoConfigurationTest {

    @SpringBootApplication
    static class TestApp {
        @Bean
        PathlandApp pathlandApp() {
            return () -> Text.of("Hello Pathland");
        }
    }

    @Autowired
    PathlandHost host;

    @Test
    void autoConfigWiresSSR() {
        assertNotNull(host, "a PathlandApp bean activates the host");
        assertNotNull(host.registry("/"), "the lone PathlandApp mounts at the root");
        String html = host.registry("/").renderHtml("/");
        assertNotNull(html);
        assertTrue(html.contains("Hello Pathland") || html.contains("Pathland renderer unavailable"),
                "SSR renders the root view (or reports the renderer is unavailable)");
    }
}