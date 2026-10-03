package com.pathland.spring;

import com.pathland.observability.MicrometerTelemetry;
import com.pathland.server.PathlandApp;
import com.pathland.server.PathlandTelemetry;
import com.pathland.view.Text;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Observability end to end: the starter auto-wires the Micrometer telemetry and a
 * health indicator, so {@code /actuator/health} reports Pathland UP and the
 * {@code pathland.*} meters are registered in the actuator {@link MeterRegistry}.
 */
@SpringBootTest(
        classes = PathlandObservabilityTest.TestApp.class,
        properties = "management.endpoint.health.show-details=always")
@AutoConfigureMockMvc
class PathlandObservabilityTest {

    @SpringBootApplication
    static class TestApp {
        @Bean
        PathlandApp pathlandApp() {
            return () -> Text.of("Hello Pathland");
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    MeterRegistry registry;

    @Autowired
    PathlandTelemetry telemetry;

    @Test
    void healthReportsPathlandUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.pathland.status").value("UP"));
    }

    @Test
    void micrometerTelemetryIsWiredAndRegistersMeters() {
        assertInstanceOf(MicrometerTelemetry.class, telemetry, "a Micrometer adapter is auto-configured");
        assertNotNull(registry.find("pathland.sessions.active").gauge(), "active-sessions gauge registered at startup");

        telemetry.sessionOpened("/test");
        assertEquals(1.0, registry.get("pathland.sessions.opened").tag("mount", "/test").counter().count());
    }
}
