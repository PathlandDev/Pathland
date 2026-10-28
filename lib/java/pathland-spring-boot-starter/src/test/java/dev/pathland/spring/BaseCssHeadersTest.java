package dev.pathland.spring;

import dev.pathland.render.html.HtmlRenderer;
import dev.pathland.server.PathlandApp;
import dev.pathland.view.Text;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The renderer-owned base design is served as a content-hashed asset under the
 * framework base (global and per-app), with immutable caching; the SSR document
 * links it instead of inlining the CSS.
 */
@SpringBootTest(classes = BaseCssHeadersTest.TestApp.class)
@AutoConfigureMockMvc
class BaseCssHeadersTest {

    @SpringBootApplication
    static class TestApp {
        @Bean
        PathlandApp pathlandApp() {
            return () -> Text.make("Hello Pathland");
        }
    }

    @Autowired
    MockMvc mockMvc;

    private static String cssName() {
        HtmlRenderer renderer = HtmlRenderer.tryInstance();
        assumeTrue(renderer != null && !renderer.baseCssName().isEmpty(),
                "the Rust HTML renderer (with the linked CSS export) is available");
        return renderer.baseCssName();
    }

    @Test
    void baseCssIsServedImmutableAtGlobalAndMountedBases() throws Exception {
        String name = cssName();
        for (String path : new String[] {"/_pathland/" + name, "/app2/_pathland/" + name}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("text/css"))
                    .andExpect(header().string("Cache-Control", containsString("max-age=31536000")))
                    .andExpect(header().string("Cache-Control", containsString("immutable")))
                    .andExpect(content().string(containsString("--pl-color-primary")));
        }

        mockMvc.perform(get("/_pathland/base-deadbeef.css"))
                .andExpect(status().isNotFound());
    }

    @Test
    void ssrLinksTheBaseCss() throws Exception {
        String name = cssName();
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "<link rel=\"stylesheet\" href=\"/_pathland/" + name + "\">")));
    }
}
