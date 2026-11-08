package dev.pathland.quarkus;

import dev.pathland.render.html.HtmlRenderer;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The DOM client bundle ships in the extension jar, so a consuming application
 * needs no static resources of its own. The test module deliberately provides
 * none: Quarkus's static handler serves it at the global base from
 * {@code META-INF/resources}, and the framework catch-all serves it at a
 * mounted base from the same classpath mount.
 */
@QuarkusTest
class DomRendererBundleTest {

    @TestHTTPResource("/")
    URL base;

    private static String bundleName() throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = loader.getResourceAsStream(
                "META-INF/resources/_pathland/dom-renderer.current")) {
            assertNotNull(in, "the extension jar ships the DOM renderer manifest");
            String name = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).readLine();
            assertNotNull(name);
            return name.trim();
        }
    }

    @Test
    void globalBundleIsServed() throws Exception {
        String name = bundleName();
        HttpResponse<byte[]> response = get("_pathland/" + name);
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("javascript"),
                "served as JavaScript: " + response.headers().firstValue("Content-Type"));
        assertTrue(response.body().length > 0, "the bundle bytes are served");
    }

    @Test
    void mountedBundleIsServedImmutable() throws Exception {
        String name = bundleName();
        HttpResponse<byte[]> response = get("app2/_pathland/" + name);
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("javascript"),
                "served as JavaScript: " + response.headers().firstValue("Content-Type"));
        assertEquals("public, max-age=31536000, immutable",
                response.headers().firstValue("Cache-Control").orElse(null));
        assertTrue(response.body().length > 0, "the bundle bytes are served");
    }

    @Test
    void ssrReferencesTheBundledScript() throws Exception {
        assumeTrue(HtmlRenderer.tryInstance() != null, "the Rust HTML renderer is available");
        String name = bundleName();
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(base.toString())).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("/_pathland/" + name),
                "SSR references the shipped bundle: " + response.body());
        assertTrue(response.body().contains("integrity=\"sha384-"),
                "SSR emits Subresource Integrity: " + response.body());
    }

    private HttpResponse<byte[]> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }
}
