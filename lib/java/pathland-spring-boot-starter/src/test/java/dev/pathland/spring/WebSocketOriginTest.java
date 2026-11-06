package dev.pathland.spring;

import dev.pathland.server.PathlandApp;
import dev.pathland.server.test.WsSession;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The starter's WebSocket origin policy: same-origin is the default (Spring's
 * handshake check) and a foreign browser origin is rejected. This is the CSRF
 * control for the live-update stream.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = WebSocketOriginTest.TestApp.class)
class WebSocketOriginTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
        @Bean
        PathlandApp pathlandApp() {
            return () -> KotlinFixtures.text("origin");
        }
    }

    @LocalServerPort
    int port;

    private String wsUrl() {
        return "ws://localhost:" + port + "/_pathland/ws";
    }

    @Test
    void sameOriginIsAccepted() {
        String origin = "http://localhost:" + port;
        assertDoesNotThrow(() -> {
            try (WsSession session = WsSession.connect(wsUrl(), "w1", origin)) {
                session.sendEnvironment(800, 600, "/");
            }
        });
    }

    @Test
    void foreignOriginIsRejected() {
        assertThrows(Exception.class, () -> {
            try (WsSession session = WsSession.connect(wsUrl(), "w1", "https://evil.example")) {
                session.sendEnvironment(800, 600, "/");
            }
        });
    }
    @Test
    void forwardedExternalOriginIsAcceptedByDefault() {
        // A TLS-terminating host platform forwards plain HTTP with these headers; the
        // starter's lowest-precedence default enables Spring's forwarded-header handling.
        String origin = "https://example.com";
        String wsUrl = wsUrl();
        assertDoesNotThrow(() -> {
            try (WsSession session = WsSession.connect(
                    wsUrl, "w-fwd", origin,
                    Map.of("X-Forwarded-Proto", "https", "X-Forwarded-Host", "example.com"))) {
                session.sendEnvironment(800, 600, "/");
            }
        });
    }

}
