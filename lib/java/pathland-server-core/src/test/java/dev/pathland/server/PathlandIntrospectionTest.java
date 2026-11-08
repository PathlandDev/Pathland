package dev.pathland.server;

import dev.pathland.server.introspection.PathlandIntrospection.HostSnapshot;
import dev.pathland.server.introspection.PathlandIntrospection.RegistrySnapshot;
import dev.pathland.server.introspection.PathlandIntrospection.SessionSnapshot;
import dev.pathland.view.core.state.InMemoryStateStore;
import dev.pathland.view.core.transport.EnvironmentData;
import dev.pathland.view.core.transport.Event;
import dev.pathland.view.core.transport.FrameCodec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The read-only diagnostic snapshots used by the Quarkus Dev UI: they must reflect
 * live session state (route, connection, tree, stats) and run on the registry actor.
 */
class PathlandIntrospectionTest {

    private static final class RecordingConnection implements PathlandConnection {
        boolean open = true;

        @Override
        public void send(byte[] bytes) {
        }

        @Override
        public boolean isOpen() {
            return open;
        }
    }

    @Test
    void snapshotsLiveSessionStateAndCounters() {
        PathlandApp app = () -> ServerFixtures.button("Tap", () -> {
        });
        PathlandRegistry registry = new PathlandRegistry(
                "/", app, new InMemoryStateStore(), false);
        RecordingConnection connection = new RecordingConnection();
        try {
            registry.open("ui-1", "wid-1", connection);
            registry.environment("ui-1", EnvironmentData.of("/home"));

            RegistrySnapshot snapshot = registry.snapshot();
            assertEquals("/", snapshot.mount());
            assertEquals(1, snapshot.activeSessions());
            assertEquals(1, snapshot.sessions().size());

            SessionSnapshot session = snapshot.sessions().get(0);
            assertEquals("ui-1", session.id());
            assertEquals("/home", session.route());
            assertEquals("wid-1", session.stateScope());
            assertTrue(session.connected(), "the pending connection is attached on session creation");
            assertFalse(session.closed());
            assertTrue(session.treeNodes() >= 1, "the retained tree has at least the root");
            assertTrue(session.rootId() > 0);

            // Inbound events are counted, and a resync is visible in the stats.
            registry.dispatch("ui-1", FrameCodec.encodeEvents(List.of(Event.pointerUp(1, 0, 0))));
            registry.resync("ui-1");

            SessionSnapshot after = registry.snapshot().sessions().get(0);
            assertEquals(1, after.stats().inboundEvents());
            assertEquals(1, after.stats().resyncs());
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void hostSnapshotCoversEveryMount() {
        PathlandApp root = () -> ServerFixtures.button("Root", () -> {
        });
        PathlandApp support = () -> ServerFixtures.button("Support", () -> {
        });
        PathlandHost host = new PathlandHost(
                List.of(MountedApp.of("/", root), MountedApp.of("/support", support)),
                new InMemoryStateStore(),
                false);
        try {
            PathlandRegistry rootRegistry = host.registry("/");
            PathlandRegistry supportRegistry = host.registry("/support");
            rootRegistry.open("ui-root", "wid-root", new RecordingConnection());
            rootRegistry.environment("ui-root", EnvironmentData.of("/"));
            supportRegistry.open("ui-support", "wid-support", new RecordingConnection());
            supportRegistry.environment("ui-support", EnvironmentData.of("/tickets"));

            HostSnapshot snapshot = host.snapshot();
            assertTrue(snapshot.running());
            assertEquals(2, snapshot.activeSessions());
            assertEquals(2, snapshot.mounts().size());
            assertEquals(1, snapshot.mounts().stream()
                    .filter(mount -> "/support".equals(mount.mount()))
                    .findFirst()
                    .orElseThrow()
                    .sessions()
                    .size());
        } finally {
            host.shutdown();
        }
    }
}
