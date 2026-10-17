package dev.pathland.spring;

import dev.pathland.server.PathlandApp;
import dev.pathland.view.Text;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The per-window-id service worker (issue #155) must be served with
 * {@code Service-Worker-Allowed: /} — a script under {@code /_pathland/} can only claim
 * the ROOT scope with it — and {@code Cache-Control: no-cache} (the browser re-fetches
 * the script to byte-compare updates). The framework controller owns the path, so it
 * wins over the static-resource handler and sets both headers.
 */
@SpringBootTest(classes = ServiceWorkerHeadersTest.TestApp.class)
@AutoConfigureMockMvc
class ServiceWorkerHeadersTest {

    @SpringBootApplication
    static class TestApp {
        @Bean
        PathlandApp pathlandApp() {
            return () -> Text.make("Hello Pathland");
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void swJsCarriesTheRootScopeAndNoCacheHeaders() throws Exception {
        mockMvc.perform(get("/_pathland/sw.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Service-Worker-Allowed", "/"))
                .andExpect(header().string("Cache-Control", "no-cache"));
    }
}
