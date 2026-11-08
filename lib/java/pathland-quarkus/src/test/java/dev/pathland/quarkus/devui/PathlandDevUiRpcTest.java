package dev.pathland.quarkus.devui;

import dev.pathland.quarkus.KotlinFixtures;
import dev.pathland.server.MountedApp;
import dev.pathland.server.PathlandApp;
import dev.pathland.server.PathlandConnection;
import dev.pathland.server.PathlandHost;
import dev.pathland.server.PathlandRegistry;
import dev.pathland.server.PathlandTelemetry;
import dev.pathland.server.introspection.PathlandIntrospection.SessionSnapshot;
import dev.pathland.view.core.state.InMemoryStateStore;
import dev.pathland.view.core.transport.EnvironmentData;
import dev.pathland.view.core.transport.Event;
import dev.pathland.view.core.transport.FrameCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Dev UI runtime surface: overview/sessions snapshots and lifecycle actions,
 * exercised against a real host (no Quarkus dev mode required).
 */
class PathlandDevUiRpcTest {

    private static final class RecordingConnection implements PathlandConnection {
        @Override
        public void send(byte[] bytes) {
        }

        @Override
        public boolean isOpen() {
            return true;
        }
    }

    private PathlandHost host;
    private PathlandDevTraceStore trace;
    private PathlandDevUiRpc rpc;

    @BeforeEach
    void setUp() {
        PathlandApp app = KotlinFixtures::counterProbe;
        trace = new PathlandDevTraceStore();
        host = new PathlandHost(
                List.of(MountedApp.of("/", app), MountedApp.of("/support", app)),
                new InMemoryStateStore(),
                false,
                PathlandTelemetry.NOOP,
                false,
                trace);
        rpc = new PathlandDevUiRpc(host, trace);
    }

    @AfterEach
    void tearDown() {
        host.shutdown();
    }

    @Test
    void overviewReportsMountsAndAssets() {
        open(host.registry("/"), "ui-1", "wid-1", "/home");

        PathlandDevUiRpc.Overview overview = rpc.overview();
        assertTrue(overview.running());
        assertEquals(1, overview.activeSessions());
        assertEquals(2, overview.mounts().size());
        assertNotNull(overview.domBundle());
        assertNotNull(overview.baseCss());
        assertNotNull(rpc.overviewSummary());
        assertEquals("1", rpc.sessionsLabel());
    }

    @Test
    void sessionsListLiveSessionsAndActionsWork() {
        PathlandRegistry root = host.registry("/");
        open(root, "ui-1", "wid-1", "/home");

        List<SessionSnapshot> sessions = rpc.sessions();
        assertEquals(1, sessions.size());
        SessionSnapshot session = sessions.get(0);
        assertEquals("ui-1", session.id());
        assertEquals("/home", session.route());
        assertEquals("wid-1", session.stateScope());
        assertTrue(session.connected());
        assertTrue(session.treeNodes() >= 1);

        assertTrue(rpc.resync(session.id()).ok());
        SessionSnapshot afterResync = rpc.sessions().get(0);
        assertEquals(1, afterResync.stats().resyncs());

        assertTrue(rpc.resyncAll().ok());
        assertEquals(2, rpc.sessions().get(0).stats().resyncs());

        assertTrue(rpc.closeSession(session.id()).ok());
        assertTrue(rpc.sessions().isEmpty(), "a closed session disappears from the live list");
    }

    @Test
    void unknownSessionActionsFailCleanly() {
        assertFalse(rpc.resync("missing").ok());
        assertNotNull(rpc.resync("missing").error());
        assertFalse(rpc.closeSession("missing").ok());
    }

    @Test
    void traceCapturesBatchesEventsAndSsr() {
        PathlandRegistry root = host.registry("/");
        open(root, "ui-1", "wid-1", "/home");
        SessionSnapshot session = rpc.sessions().get(0);

        assertTrue(rpc.resync(session.id()).ok());
        rpc.sessions(); // actor barrier: the resync batch is recorded before this returns

        root.dispatch(session.id(), FrameCodec.encodeEvents(List.of(Event.pointerUp(1, 0, 0))));
        rpc.sessions(); // actor barrier: the inbound events are recorded

        PathlandDevUiRpc.SsrPreview preview = rpc.ssr("/", "/home");
        assertNotNull(preview.html(), "SSR preview renders a document (or its unavailable placeholder)");
        assertNull(preview.error());

        PathlandDevTraceStore.Snapshot snapshot = rpc.trace(0, 100);
        List<PathlandDevTraceStore.Kind> kinds =
                snapshot.entries().stream().map(PathlandDevTraceStore.Entry::kind).toList();
        assertTrue(kinds.contains(PathlandDevTraceStore.Kind.SESSION_OPEN));
        assertTrue(kinds.contains(PathlandDevTraceStore.Kind.BATCH));
        assertTrue(kinds.contains(PathlandDevTraceStore.Kind.EVENTS));
        assertTrue(kinds.contains(PathlandDevTraceStore.Kind.SSR));
        PathlandDevTraceStore.Entry batch = snapshot.entries().stream()
                .filter(entry -> entry.kind() == PathlandDevTraceStore.Kind.BATCH)
                .findFirst().orElseThrow();
        assertTrue(batch.opcodes() > 0, "the resync batch has opcodes");
        assertFalse(batch.decoded().isEmpty());

        long lastId = rpc.clearTrace();
        assertEquals(snapshot.nextId(), lastId);
        assertTrue(rpc.trace(0, 100).entries().isEmpty(), "clear drops the retained entries");
    }

    private static void open(PathlandRegistry registry, String uiId, String wid, String route) {
        registry.open(uiId, wid, new RecordingConnection());
        registry.environment(uiId, EnvironmentData.of(route));
    }
}
