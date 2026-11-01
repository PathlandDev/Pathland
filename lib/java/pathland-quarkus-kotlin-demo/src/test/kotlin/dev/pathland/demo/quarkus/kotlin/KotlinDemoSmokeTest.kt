package dev.pathland.demo.quarkus.kotlin

import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
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
        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create(base.toString())).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("Pathland Kotlin Demo"), response.body())
        assertTrue(response.body().contains("Count: 0"), response.body())
        assertTrue(response.body().contains("First item"), response.body())
    }
}
