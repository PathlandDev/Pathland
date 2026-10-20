package dev.pathland.spring;

import dev.pathland.server.PathlandApp;
import dev.pathland.server.test.MultiSessionProbe;
import dev.pathland.view.Button;
import dev.pathland.view.Text;
import dev.pathland.view.View;
import dev.pathland.view.signal.Signals;
import dev.pathland.view.signal.WritableSignal;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;

/**
 * 100 concurrent real WebSocket sessions against the starter's {@code /_pathland/ws}
 * endpoint (the shared {@link MultiSessionProbe}): every session connects, re-syncs,
 * gets transport-level PONGs, stays isolated, and is never closed by the peer — the
 * concurrency / Scheduler / heartbeat regression guard for the Spring path.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = MultiSessionWebSocketTest.TestApp.class)
class MultiSessionWebSocketTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
        @Bean
        PathlandApp pathlandApp() {
            return () -> {
                WritableSignal<Integer> count = Signals.signal(0);
                return Button.make(b -> b.action(() -> count.update(i -> i + 1)))
                        .children(Text.make(Signals.computed(() -> "n=" + count.get())));
            };
        }
    }

    @LocalServerPort
    int port;

    @Test
    void hundredSessionsBehaveAsExpected() throws Exception {
        MultiSessionProbe.run("ws://localhost:" + port + "/_pathland/ws");
    }
}