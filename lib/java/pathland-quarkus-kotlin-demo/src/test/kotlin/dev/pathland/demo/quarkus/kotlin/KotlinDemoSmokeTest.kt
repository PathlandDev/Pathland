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
        assertTrue(response.body().contains("Pathland Kotlin Demo"), response.body())
        assertTrue(response.body().contains("Count: 0"), response.body())
        assertTrue(response.body().contains("First item"), response.body())
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
