package dev.pathland.server;

import dev.pathland.view.core.transport.Event;

import java.util.List;

/**
 * An optional, development-only observer of the live runtime: emitted batches,
 * inbound event batches, SSR renders, and session lifecycle. The default is
 * {@link #NOOP}, so production pays nothing and carries no Dev UI dependency.
 *
 * <p>Implementations are called on the session actor (events/lifecycle/SSR) and on
 * the delta batcher's flush thread (batches). They MUST be thread-safe, cheap, and
 * MUST NOT throw — a diagnostic hook must never change or break emission.
 */
public interface PathlandDevTrace {

    /** The no-op trace used when no dev observer is installed. */
    PathlandDevTrace NOOP = new PathlandDevTrace() {
    };

    /** A live session was created. */
    default void sessionOpened(String sessionId, String mount) {
    }

    /** A live session was closed. */
    default void sessionClosed(String sessionId, String mount) {
    }

    /**
     * An outbound delta batch was encoded (before it is handed to the connection).
     *
     * @param frames  emit-pass frames coalesced into this batch
     * @param sequence the batch's per-stream sequence
     * @param bytes    the encoded PLPL batch
     */
    default void batchSent(String sessionId, String mount, int sequence, int frames, byte[] bytes) {
    }

    /** An inbound event batch was decoded for a session. */
    default void eventsReceived(String sessionId, String mount, List<Event> events) {
    }

    /** An SSR document was rendered for a route. */
    default void ssrRendered(String mount, String route, long nanos, int bytes) {
    }
}
