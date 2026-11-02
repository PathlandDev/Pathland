package dev.pathland.quarkus;

import dev.pathland.render.html.HtmlRenderer;
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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The renderer-owned base design is served as a content-hashed asset under the
 * framework base (global and per-app), with immutable caching; the SSR document
 * links it instead of inlining the CSS.
 */
@QuarkusTest
class BaseCssTest {

    @TestHTTPResource("/")
    URL base;

    private static String cssName() {
        HtmlRenderer renderer = HtmlRenderer.tryInstance();
        assumeTrue(renderer != null && !renderer.baseCssName().isEmpty(),
                "the Rust HTML renderer (with the linked CSS export) is available");
        return renderer.baseCssName();
    }

    @Test
    void baseCssIsServedImmutableAtGlobalAndMountedBases() throws Exception {
        String name = cssName();
        HttpClient client = HttpClient.newHttpClient();
        for (String path : new String[] {"_pathland/" + name, "app2/_pathland/" + name}) {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), path);
            assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/css"),
                    "served as CSS: " + path);
            assertEquals("public, max-age=31536000, immutable",
                    response.headers().firstValue("Cache-Control").orElse(null), path);
            assertTrue(response.body().contains("--pl-color-primary"), "base design bytes: " + path);
        }

        HttpResponse<String> missing = client.send(
                HttpRequest.newBuilder(URI.create(base + "_pathland/base-deadbeef.css")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, missing.statusCode());
    }

    @Test
    void ssrLinksTheBaseCss() throws Exception {
        String name = cssName();
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(base.toString())).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains(
                        "<link rel=\"stylesheet\" href=\"/_pathland/" + name + "\">"),
                "SSR links the base stylesheet: " + response.body());
    }
}
