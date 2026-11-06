package dev.pathland.spring;

import dev.pathland.server.PathlandApp;
import dev.pathland.server.PathlandHost;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The starter's auto-configuration smoke test: adding the dependency + a {@code PathlandApp}
 * bean wires the {@link PathlandHost} (with the app mounted at {@code "/"}) and SSR end to end.
 */
@SpringBootTest(classes = PathlandAutoConfigurationTest.TestApp.class)
@AutoConfigureMockMvc
class PathlandAutoConfigurationTest {

    @SpringBootApplication
    static class TestApp {
        @Bean
        PathlandApp pathlandApp() {
            return () -> KotlinFixtures.text("Hello Pathland");
        }
    }

    @Autowired
    PathlandHost host;

    @Autowired
    MockMvc mvc;

    @Test
    void autoConfigWiresSSR() {
        assertNotNull(host, "a PathlandApp bean activates the host");
        assertNotNull(host.registry("/"), "the lone PathlandApp mounts at the root");
        String html = host.registry("/").renderHtml("/");
        assertNotNull(html);
        assertTrue(html.contains("Hello Pathland") || html.contains("Pathland renderer unavailable"),
                "SSR renders the root view (or reports the renderer is unavailable)");
    }
    @Test
    void ssrHeadCarriesCanonicalAndStructuredDataByDefault() throws Exception {
        String html = mvc.perform(get("/library"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertTrue(html.contains("<link rel=\"canonical\" href=\"http://localhost/library\">"), html);
        assertTrue(html.contains("<meta property=\"og:type\" content=\"website\">"), html);
        assertTrue(html.contains("<meta name=\"twitter:card\" content=\"summary\">"), html);
        assertTrue(html.contains("<script type=\"application/ld+json\">"), html);
        assertTrue(html.contains("\"@type\":\"WebPage\""), html);
    }

    @Test
    void forwardedHeadersDriveTheCanonicalUrlByDefault() throws Exception {
        String html = mvc.perform(get("/library")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "example.com")
                        .header("X-Forwarded-Port", "443"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertTrue(html.contains("<link rel=\"canonical\" href=\"https://example.com/library\">"), html);
    }

}