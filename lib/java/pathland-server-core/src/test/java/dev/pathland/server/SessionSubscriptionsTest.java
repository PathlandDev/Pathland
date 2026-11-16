package dev.pathland.server;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionSubscriptionsTest {

    private SessionSubscriptions subscriptions(PathlandSignalHub hub) {
        return new SessionSubscriptions(hub, Runnable::run, PathlandTelemetry.NOOP, "/");
    }

    @Test
    void ssrSessionIsNotLive() {
        SessionSubscriptions subs = subscriptions(null);
        assertFalse(subs.isLive());
        subs.close();
    }

    @Test
    void liveSessionIsLive() {
        PathlandSignalHub hub = new PathlandSignalHub("/", PathlandTelemetry.NOOP);
        SessionSubscriptions subs = subscriptions(hub);
        assertTrue(subs.isLive());
        subs.close();
        hub.clear();
    }

    @Test
    void closeRunsRegisteredTasks() {
        SessionSubscriptions subs = subscriptions(null);
        AtomicBoolean closed = new AtomicBoolean();
        subs.onClose(() -> closed.set(true));
        subs.close();
        assertTrue(closed.get());
    }

    @Test
    void onCloseAfterCloseRunsImmediately() {
        SessionSubscriptions subs = subscriptions(null);
        subs.close();
        AtomicBoolean ran = new AtomicBoolean();
        subs.onClose(() -> ran.set(true));
        assertTrue(ran.get());
    }
}
