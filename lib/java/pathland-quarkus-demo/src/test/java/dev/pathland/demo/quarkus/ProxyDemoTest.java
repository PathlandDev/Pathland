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
 * The same-server proxy demo (spec/PROXY.md): the music player at {@code /} embeds
 * the small ad app mounted at {@code /ad} on the <b>same server</b>, via a
 * {@code ProxyView} whose origin is this server's own {@code /ad} endpoint.
 *
 * <p>The player's SSR blocks briefly for the ad's snapshot over a loopback
 * WebSocket, remaps the ad app's node ids into the proxy range
 * {@code [0x4000_0000, …)}, and splices its subtree into the one document.
 */
@QuarkusTest
class ProxyDemoTest {

    @TestHTTPResource("/")
    URL base;

    @Test
    void thePlayerEmbedsTheAdAppFromTheSameServer() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(base.toString())).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        String html = response.body();

        assertTrue(html.contains("Pathland Ads"),
                "the proxied ad app's title is embedded in the player's document");
        assertTrue(html.contains("Clicked 0 times"),
                "the ad app's own counter renders through the proxy");
        // The ad app's node ids are remapped into proxy block 0: its root (origin id 1)
        // becomes 0x4000_0000 + 1.
        assertTrue(html.contains("data-pathland-id=\"1073741825\""),
                "the ad subtree carries remapped ids from the proxy range");
    }
}
