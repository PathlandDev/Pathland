package dev.pathland.spring;

import dev.pathland.server.PathlandApp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pathland only supplies a <b>default</b>: an application's explicit
 * {@code server.forward-headers-strategy} always wins. With {@code none}, forwarded
 * headers must not affect the SSR canonical URL.
 */
@SpringBootTest(
        classes = ForwardedHeaderOverrideTest.TestApp.class,
        properties = "server.forward-headers-strategy=none")
@AutoConfigureMockMvc
class ForwardedHeaderOverrideTest {

    @SpringBootApplication
    static class TestApp {
        @Bean
        PathlandApp pathlandApp() {
            return () -> KotlinFixtures.text("override");
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void explicitStrategyNoneIsNotOverridden() throws Exception {
        String html = mvc.perform(get("/library")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "example.com")
                        .header("X-Forwarded-Port", "443"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertTrue(html.contains("<link rel=\"canonical\" href=\"http://localhost/library\">"), html);
        assertFalse(html.contains("https://example.com/library"), html);
    }
}
