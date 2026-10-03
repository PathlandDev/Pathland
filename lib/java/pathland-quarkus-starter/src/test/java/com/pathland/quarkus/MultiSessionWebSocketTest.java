package com.pathland.quarkus;

import com.pathland.server.PathlandApp;
import com.pathland.server.PathlandHost;
import com.pathland.server.test.MultiSessionProbe;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 100 concurrent real WebSocket sessions against the starter's {@code /_pathland/ws}
 * endpoint (the shared {@link MultiSessionProbe}): every session connects, re-syncs,
 * gets transport-level PONGs, stays isolated (only its own click counts reach its
 * label), and is never closed by the peer — the concurrency / Scheduler / heartbeat
 * regression guard for the Quarkus path.
 */
@QuarkusTest
class MultiSessionWebSocketTest {

    @Inject
    PathlandHost host;

    @TestHTTPResource("/")
    URL base;

    @BeforeEach
    void hostIsMounted() {
        assertNotNull(host, "the PathlandHost is produced");
        assertNotNull(host.registry("/"), "the CounterApp bean mounts the root app");
    }

    @Test
    void hundredSessionsBehaveAsExpected() throws Exception {
        MultiSessionProbe.run("ws://" + base.getHost() + ":" + base.getPort() + "/_pathland/ws");
    }
}