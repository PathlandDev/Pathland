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

/** The extension contributes a same-origin CSP and standard security headers. */
@QuarkusTest
class SecurityHeadersTest {

    @TestHTTPResource("/")
    URL base;

    @Test
    void ssrCarriesCspAndSecurityHeaders() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(base.toString())).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        String csp = response.headers().firstValue("Content-Security-Policy").orElse("");
        assertTrue(csp.contains("script-src 'self'"), "scripts are self-only: " + csp);
        assertTrue(csp.contains("style-src 'self' 'unsafe-inline'"),
                "inline protocol styles are allowed: " + csp);
        assertTrue(csp.contains("frame-ancestors 'none'"), "clickjacking is blocked: " + csp);
        assertEquals("nosniff",
                response.headers().firstValue("X-Content-Type-Options").orElse(""));
        assertEquals("no-referrer",
                response.headers().firstValue("Referrer-Policy").orElse(""));
        assertEquals("DENY",
                response.headers().firstValue("X-Frame-Options").orElse(""));
        String hsts = response.headers().firstValue("Strict-Transport-Security").orElse("");
        assertTrue(hsts.contains("max-age=31536000"), "HSTS is enabled for the demo: " + hsts);
        assertTrue(hsts.contains("includeSubDomains"), "HSTS covers subdomains: " + hsts);
        assertTrue(response.body().contains("integrity=\"sha384-"),
                "the SSR script tag carries the SRI digest: " + response.body());
    }

    @Test
    void serviceWorkerIsServedWithTheRootScopeHeader() throws Exception {
        for (String path : new String[] {"_pathland/sw.js", "ad/_pathland/sw.js"}) {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), path);
            assertEquals("/", response.headers().firstValue("Service-Worker-Allowed").orElse(null),
                    "the worker may claim the root scope: " + path);
            assertEquals("no-cache", response.headers().firstValue("Cache-Control").orElse(null),
                    "the worker script is never cached: " + path);
        }
    }
}
