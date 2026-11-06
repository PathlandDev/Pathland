package dev.pathland.demo.quarkus;

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
 * The Kotlin-only authored view ({@code KotlinDemoView}) is mounted at
 * {@code /kotlin} alongside the Java-hosted player and ad apps: SSR renders the
 * Kotlin tree through the same extension, with no Java authoring DSL involved.
 */
@QuarkusTest
class KotlinMountDemoTest {

    @TestHTTPResource("/")
    URL base;

    @Test
    void ssrRendersTheKotlinViewAtItsMountPath() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(base.toString()).resolve("/kotlin")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        String html = response.body();
        assertTrue(html.contains("Pathland Kotlin Demo"), "the Kotlin view renders at /kotlin: " + html);
        assertTrue(html.contains("Count: 0"), "persisted state renders in the Kotlin view");
        assertTrue(html.contains("SSR plus live 16-byte opcode deltas"),
                "the Kotlin view's body is rendered");
    }
}
