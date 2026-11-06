package dev.pathland.demo.quarkus;

import dev.pathland.server.test.WsSession;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hosted-default regression: a TLS-terminating proxy forwards plain HTTP with
 * {@code X-Forwarded-Proto}/{@code X-Forwarded-Host}. The extension trusts those
 * headers by default, so the browser's external {@code https} origin matches the
 * request origin (WebSocket handshake succeeds) and the SSR canonical URL is the
 * external URL. Application config can override every one of these defaults.
 */
@QuarkusTest
class ForwardedHeaderTest {

    @TestHTTPResource("/")
    URL base;

    private static final Map<String, String> FORWARDED = Map.of(
            "X-Forwarded-Proto", "https",
            "X-Forwarded-Host", "example.com");

    @Test
    void forwardedExternalOriginAcceptsTheWebSocketHandshake() {
        String wsUrl = "ws://" + base.getHost() + ":" + base.getPort() + "/_pathland/ws";
        assertDoesNotThrow(() -> {
            try (WsSession session =
                    WsSession.connect(wsUrl, "fwd", "https://example.com", FORWARDED)) {
                session.sendEnvironment(800, 600, "/");
            }
        });
    }

    @Test
    void canonicalUrlUsesTheForwardedExternalOrigin() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base.toString()))
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "example.com")
                .GET()
                .build();
        HttpResponse<String> response =
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(
                response.body().contains("<link rel=\"canonical\" href=\"https://example.com/\">"),
                response.body());
    }
}
