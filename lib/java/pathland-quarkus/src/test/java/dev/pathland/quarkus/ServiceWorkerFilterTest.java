package dev.pathland.quarkus;

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
 * The per-window-id service worker (issue #155) must be served with
 * {@code Service-Worker-Allowed: /} — a script under {@code /_pathland/} can only claim
 * the ROOT scope with it, which is what the DOM client's {@code register(..., {scope: "/"})}
 * needs — and {@code Cache-Control: no-cache} (the browser re-fetches the script to
 * byte-compare updates). Quarkus's static-resource handler sends neither, so a
 * {@code Filters} handler intercepts the path (filters run before routes).
 */
@QuarkusTest
class ServiceWorkerFilterTest {

    @TestHTTPResource("/")
    URL base;

    @Test
    void swJsCarriesTheRootScopeAndNoCacheHeaders() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(base + "_pathland/sw.js")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("/", response.headers().firstValue("Service-Worker-Allowed").orElse(null),
                "the worker may claim the root scope");
        assertEquals("no-cache", response.headers().firstValue("Cache-Control").orElse(null),
                "the worker script is never cached");
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/javascript"),
                "served as JavaScript");
    }
}
