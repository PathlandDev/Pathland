package dev.pathland.server.introspection;

import java.util.List;

/**
 * Immutable, read-only snapshots of the live Pathland runtime, used by diagnostic
 * tooling (the Quarkus Dev UI). Snapshots are built on each registry's actor thread,
 * so they observe the same state the emitter does; they never expose the mutable
 * session, tree, or emitter internals.
 *
 * <p>This is a development/diagnostic surface. It carries no control flow and no
 * business state of its own.
 */
public final class PathlandIntrospection {

    private PathlandIntrospection() {
    }

    /** The whole host: every mounted app and its live sessions. */
    public record HostSnapshot(
            boolean running,
            int activeSessions,
            List<RegistrySnapshot> mounts) {
    }

    /** One mounted app (registry): its framework base, flags, and live sessions. */
    public record RegistrySnapshot(
            String mount,
            String base,
            boolean debugHtml,
            boolean serviceWorker,
            int activeSessions,
            List<SessionSnapshot> sessions) {
    }

    /** One live (or mount-only) session. */
    public record SessionSnapshot(
            String id,
            String mount,
            String stateScope,
            String route,
            float viewportWidth,
            float viewportHeight,
            boolean connected,
            boolean closed,
            String colorScheme,
            boolean themeOverrides,
            long openedAtMillis,
            long lastActivityMillis,
            int rootId,
            int treeNodes,
            int proxyCount,
            List<ProxySnapshot> proxies,
            List<SubscriptionSnapshot> subscriptions,
            SessionStats stats) {
    }

    /** One proxy origin connection owned by a session. */
    public record ProxySnapshot(
            int proxyId,
            int block,
            String endpoint,
            String route,
            boolean ready,
            boolean snapshotReceived,
            int attempts,
            int originRootId) {
    }

    /** One topic subscription held by a session. */
    public record SubscriptionSnapshot(String topic, String subject, boolean pending) {
    }

    /** Per-session throughput counters. */
    public record SessionStats(
            long inboundEvents,
            long resyncs,
            long batchesSent,
            int lastFrames,
            int lastOpcodes,
            int lastBytes,
            int sequence) {
    }
}
