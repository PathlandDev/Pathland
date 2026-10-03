package com.pathland.quarkus;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Observability end to end: the starter wires Micrometer + SmallRye Health, so
 * {@code /q/health} reports the Pathland readiness check UP and {@code /q/metrics}
 * exposes the {@code pathland.*} meter namespace.
 */
@QuarkusTest
class PathlandObservabilityTest {

    @TestHTTPResource("/")
    URL base;

    @Test
    void healthReportsThePathlandCheckUp() throws Exception {
        HttpResponse<String> response = get("/q/health");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"name\": \"pathland\""),
                "the Pathland readiness check is present: " + response.body());
        assertTrue(response.body().contains("UP"), "the health response is UP: " + response.body());
    }

    @Test
    void metricsExposeThePathlandNamespace() throws Exception {
        HttpResponse<String> response = get("/q/metrics");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("pathland_sessions_active"),
                "the pathland.* meters are exposed: " + response.body());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path)).GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
