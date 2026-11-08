package dev.pathland.spring;

import dev.pathland.server.PathlandApp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The DOM client bundle ships in the starter jar, so a consuming application
 * needs no static resources of its own. The test module deliberately provides
 * none: the manifest and bundle are resolved from {@code static/_pathland/} on
 * the starter's classpath and served at both the global and a mounted base.
 */
@SpringBootTest(classes = DomRendererBundleTest.TestApp.class)
@AutoConfigureMockMvc
class DomRendererBundleTest {

    @SpringBootApplication
    static class TestApp {
        @Bean
        PathlandApp pathlandApp() {
            return () -> KotlinFixtures.text("Hello Pathland");
        }
    }

    @Autowired
    MockMvc mockMvc;

    private static String bundleName() throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = loader.getResourceAsStream("static/_pathland/dom-renderer.current")) {
            assertNotNull(in, "the starter jar ships the DOM renderer manifest");
            String name = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).readLine();
            assertNotNull(name);
            return name.trim();
        }
    }

    @Test
    void bundleIsServedAtGlobalAndMountedBases() throws Exception {
        String name = bundleName();
        for (String path : new String[] {"/_pathland/" + name, "/app2/_pathland/" + name}) {
            MvcResult result = mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", containsString("javascript")))
                    .andExpect(header().string("Cache-Control", containsString("immutable")))
                    .andReturn();
            assertTrue(result.getResponse().getContentAsByteArray().length > 0,
                    "the bundle bytes are served: " + path);
        }
    }

    @Test
    void ssrReferencesTheBundledScript() throws Exception {
        String name = bundleName();
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/_pathland/" + name)))
                .andExpect(content().string(containsString("integrity=\"sha384-")));
    }
}
