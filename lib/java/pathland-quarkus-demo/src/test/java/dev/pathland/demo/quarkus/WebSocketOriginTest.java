package dev.pathland.demo.quarkus;

import dev.pathland.server.test.WsSession;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Quarkus's CORS filter runs before the WebSocket route and, with the extension's
 * default ({@code enabled=true}, no explicit origins), enforces same-origin. A foreign
 * browser origin is rejected — the CSRF control for the live-update stream.
 */
@QuarkusTest
class WebSocketOriginTest {

    @TestHTTPResource("/")
    URL base;

    private String wsUrl() {
        return "ws://" + base.getHost() + ":" + base.getPort() + "/_pathland/ws";
    }

    private String sameOrigin() {
        return "http://" + base.getHost() + ":" + base.getPort();
    }

    @Test
    void sameOriginIsAccepted() {
        assertDoesNotThrow(() -> {
            try (WsSession session = WsSession.connect(wsUrl(), "w1", sameOrigin())) {
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
}
