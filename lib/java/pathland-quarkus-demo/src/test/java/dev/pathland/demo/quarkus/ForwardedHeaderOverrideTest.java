package dev.pathland.demo.quarkus;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pathland's forwarded-header default is only a default: an application's explicit
 * {@code quarkus.http.proxy.proxy-address-forwarding=false} wins, so the canonical
 * URL must not trust the forwarded host/scheme.
 */
@QuarkusTest
@TestProfile(ForwardedHeaderOverrideTest.NoForwarding.class)
class ForwardedHeaderOverrideTest {

    public static class NoForwarding implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.http.proxy.proxy-address-forwarding", "false");
        }
    }

    @TestHTTPResource("/")
    URL base;

    @Test
    void explicitDisabledProxyForwardingIsNotOverridden() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base.toString()))
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "example.com")
                .GET()
                .build();
        HttpResponse<String> response =
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(
                response.body().contains("<link rel=\"canonical\" href=\"http://localhost:"),
                response.body());
        assertFalse(response.body().contains("https://example.com/"), response.body());
    }
}
