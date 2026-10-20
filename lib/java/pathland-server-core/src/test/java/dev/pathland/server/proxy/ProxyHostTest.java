package dev.pathland.server.proxy;

import dev.pathland.server.PathlandTelemetry;
import dev.pathland.view.ProxyConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The proxy host lifecycle (spec/PROXY.md §7): reconcile the sessions against the
 * live {@code proxyViews} set, per-proxy removal, and id-block reuse.
 */
class ProxyHostTest {

    private final List<ProxySessionTest.FakeTransport> transports = new ArrayList<>();

    private ProxyHost host() {
        return new ProxyHost(
                frame -> { }, "wid", 800f, 600f, PathlandTelemetry.NOOP, "/",
                () -> {
                    ProxySessionTest.FakeTransport transport = new ProxySessionTest.FakeTransport();
                    transports.add(transport);
                    return transport;
                });
    }

    private static ProxyConfig cfg(String endpoint) {
        return new ProxyConfig(endpoint, "/", null);
    }

    @Test
    void reconcileStartsAndStopsSessionsToMatchTheWantedSet() {
        ProxyHost host = host();
        host.reconcile(Map.of(1, cfg("http://b.example")));
        assertEquals(1, host.activeProxies());
        assertEquals(1, transports.size(), "one origin connection opened");
        assertTrue(transports.get(0).isOpen());

        // Add a second proxy.
        host.reconcile(Map.of(1, cfg("http://b.example"), 2, cfg("http://c.example")));
        assertEquals(2, host.activeProxies());

        // Drop the first: its session (and origin connection) closes.
        host.reconcile(Map.of(2, cfg("http://c.example")));
        assertEquals(1, host.activeProxies());
        assertFalse(transports.get(0).isOpen(), "the removed proxy's origin connection closed");

        host.close();
    }

    @Test
    void reusesReleasedBlocksSoRepeatedTogglesDoNotExhaustThePool() {
        ProxyHost host = host();
        int block = host.add(1, cfg("http://b.example")).block();
        host.remove(1);
        assertEquals(block, host.add(2, cfg("http://c.example")).block(),
                "a released block is reused, so repeated add/remove cannot exhaust the pool");
        host.close();
    }

    @Test
    void forwardDropsEventsForAProxyThatIsGone() {
        ProxyHost host = host();
        int block = host.add(1, cfg("http://b.example")).block();
        host.remove(1);

        // A proxy-range target with no live session is dropped (not dispatched locally).
        assertTrue(host.forward(dev.pathland.view.transport.Event.pointerUp(
                ProxyIdMap.toProxyId(block, 2), 0, 0)));
        host.close();
    }
}
