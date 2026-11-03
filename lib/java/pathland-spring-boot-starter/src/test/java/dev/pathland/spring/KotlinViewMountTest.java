package dev.pathland.spring;

import dev.pathland.server.PathlandApp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A Java-only Spring Boot app mounts a Kotlin-authored view: the bean below is
 * Java, the root view comes from {@code KotlinFixtures}. No Kotlin authoring or
 * compilation happens in this module's main sources.
 */
@SpringBootTest(classes = KotlinViewMountTest.TestApp.class)
@AutoConfigureMockMvc
class KotlinViewMountTest {

    @SpringBootApplication
    static class TestApp {
        @Bean
        PathlandApp pathlandApp() {
            return () -> KotlinFixtures.counterApp();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void javaHostRendersAKotlinView() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Kotlin-authored view")))
                .andExpect(content().string(containsString("n=0")));
    }
}
