package dev.pathland.demo.quarkus.kotlin

import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** The Kotlin app boots, its CDI bean is discovered, and SSR renders the Kotlin tree. */
@QuarkusTest
class KotlinDemoSmokeTest {

    @TestHTTPResource("/")
    lateinit var base: URL

    @Test
    fun ssrRendersTheKotlinDemo() {
        val response = get("/")

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("Library"), response.body())
        assertTrue(response.body().contains("Building on Solid Ground"), response.body())
        assertTrue(response.body().contains("8 songs · 8 albums"), response.body())
        assertTrue(response.body().contains("Pathland · Building on Solid Ground"), response.body())
    }

    @Test
    fun adViewIsMountedAndEmbeddedThroughTheProxy() {
        val html = get("/").body()

        assertTrue(html.contains("Pathland Ads"), "the proxied ad app's title is embedded: $html")
        assertTrue(html.contains("Clicked 0 times"), "the ad app's counter renders through the proxy")
        assertTrue(html.contains("data-pathland-id=\"1073741825\""),
            "the ad subtree carries remapped ids from the proxy range")

        val ad = get("/ad")
        assertEquals(200, ad.statusCode(), "the ad app is mounted at /ad")
        assertTrue(ad.body().contains("Pathland Ads"), "the ad app renders directly at /ad")
    }

    @Test
    fun ssrServesTheContentHashedDomBundle() {
        val html = get("/").body()
        assertFalse(html.contains("/_pathland/dom-renderer.js\""),
                "the pre-hash fallback must not be emitted when the app manifest is visible")

        val src = Regex("""src="(/_pathland/dom-renderer-[^"]+\.js)"""")
            .find(html)?.groupValues?.get(1)
        assertNotNull(src, "the SSR document references the content-hashed DOM bundle: $html")

        val bundle = get(src!!)
        assertEquals(200, bundle.statusCode(), "GET $src")
        assertTrue(bundle.headers().firstValue("Content-Type").orElse("")
                .startsWith("text/javascript"), "served as JavaScript")
    }

    private fun get(path: String): HttpResponse<String> =
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create(base.toString()).resolve(path)).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
}
