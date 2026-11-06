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

/** SSR emits the default SEO head (canonical/OG/Twitter/JSON-LD) from the request URL. */
@QuarkusTest
class SeoHeadTest {

    @TestHTTPResource("/")
    URL base;

    @Test
    void ssrHeadCarriesCanonicalAndStructuredDataByDefault() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(base.toString())).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        String html = response.body();
        assertTrue(html.contains(
                "<link rel=\"canonical\" href=\"" + base + "\">"), html);
        assertTrue(html.contains("<meta property=\"og:type\" content=\"website\">"), html);
        assertTrue(html.contains("<meta name=\"twitter:card\" content=\"summary\">"), html);
        assertTrue(html.contains("<script type=\"application/ld+json\">"), html);
        assertTrue(html.contains("\"@type\":\"WebPage\""), html);
    }
}
