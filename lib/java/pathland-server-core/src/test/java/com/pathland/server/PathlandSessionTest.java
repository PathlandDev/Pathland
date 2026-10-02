package com.pathland.server;

import com.pathland.view.Button;
import com.pathland.view.Categories;
import com.pathland.view.Commands;
import com.pathland.view.Environment;
import com.pathland.view.Platform;
import com.pathland.view.Text;
import com.pathland.view.View;
import com.pathland.view.emit.Frame;
import com.pathland.view.emit.Opcode;
import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;
import com.pathland.view.signal.WritableSignal;
import com.pathland.view.state.InMemoryStateStore;
import com.pathland.view.state.StateStore;
import com.pathland.view.transport.EnvironmentData;
import com.pathland.view.transport.Event;
import com.pathland.view.transport.FrameCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The transport-agnostic session runtime: mounts the app's root (with the active-path
 * signal provided), routes inbound events into the app's bindings, applies the platform
 * environment, and renders SSR HTML — all independent of the web framework.
 */
class PathlandSessionTest {

    private static final StateStore STORE = new InMemoryStateStore();

    @Test
    void mountsAndDispatchesTaps() {
        AtomicInteger taps = new AtomicInteger();
        PathlandApp app = () -> Button.of("Tap", taps::incrementAndGet);
        PathlandSession session = new PathlandSession("s1", STORE, app, EnvironmentData.of("/"));

        // The root Button is node id 1; a POINTER_UP routed to it runs its action.
        session.dispatch(FrameCodec.encodeEvents(List.of(Event.pointerUp(1, 0, 0))));
        assertEquals(1, taps.get(), "a routed tap runs the app action");

        String html = session.renderHtml();
        assertNotNull(html);
        assertTrue(html.contains("<!DOCTYPE html>") || html.contains("Pathland renderer unavailable"),
                "SSR renders the tree (or reports the renderer is unavailable)");

        session.close();
    }

    @Test
    void applyEnvironmentAndResyncDoNotThrow() {
        PathlandApp app = () -> Button.of("Tap", () -> {});
        PathlandSession session = new PathlandSession("s1", STORE, app, EnvironmentData.of("/"));

        session.applyEnvironment(EnvironmentData.of("/kitchen"));
        session.resync();
        session.close(); // idempotent
        session.close();
    }

    @Test
    void dropsFailedConnectionAndRecoversOnReconnect() {
        PathlandApp app = () -> Button.of("Tap", () -> {});
        PathlandSession session = new PathlandSession("s2", STORE, app, EnvironmentData.of("/"));

        // A send failure must drop the connection (the client then reconnects +
        // requests a RESYNC) instead of silently stalling on an "open" socket.
        FailingConnection failing = new FailingConnection();
        session.connect(failing);
        session.resync();
        assertEquals(1, failing.attempts, "the first send is attempted");

        session.resync();
        assertEquals(1, failing.attempts, "a dropped connection is never re-sent to");

        // Reconnecting a healthy connection resumes delivery.
        RecordingConnection good = new RecordingConnection();
        session.connect(good);
        session.resync();
        assertTrue(good.sent >= 1, "a reconnected session sends again");

        session.close();
    }

    @Test
    void registryRendersHtmlAndTearsDown() {
        PathlandRegistry registry = new PathlandRegistry(() -> Button.of("Tap", () -> {}), STORE);
        String html = registry.renderHtml("/");
        assertNotNull(html);

        registry.open("ui-1", "win-1", new NoopConnection());
        registry.environment("ui-1", EnvironmentData.of("/kitchen"));
        registry.close("ui-1");
        registry.shutdown();
    }

    @Test
    void renderHtmlWithWindowIdRendersThatWindowsPersistedState() {
        // A reload's SSR carries the wid (the client reflects it into the URL), so the
        // throwaway session renders THIS window's persisted state — the HTML is already
        // the latest UI model state, and no resync is needed.
        StateStore store = new InMemoryStateStore();
        store.save("greeting:w1", "Hello window");
        PathlandApp app = () -> new View() {
            @Override
            public PathlandNode render(Environment env) {
                return Text.of(env.state().signal("greeting", "hi")).render(env);
            }
        };
        PathlandRegistry registry = new PathlandRegistry(app, store);

        String withWid = registry.renderHtml("/", "w1");
        String defaults = registry.renderHtml("/");
        registry.shutdown();
        if (withWid.contains("Pathland renderer unavailable")) {
            return; // dylib not on java.library.path in this test JVM — nothing to assert
        }
        assertTrue(withWid.contains("Hello window"),
                "SSR with the wid renders that window's persisted state: " + withWid);
        assertTrue(!defaults.contains("Hello window") && defaults.contains(">hi<"),
                "SSR without the wid renders defaults: " + defaults);
    }

    @Test
    void debugHtmlSessionEmitsNodeCommentsWhenRendererAvailable() {
        PathlandSession session = new PathlandSession(
                "s1", STORE, () -> Button.of("Tap", () -> {}), EnvironmentData.of("/"), true);
        String html = session.renderHtml();
        if (html.contains("Pathland renderer unavailable")) {
            return; // dylib not on java.library.path in this test JVM — nothing to assert
        }
        assertTrue(html.contains("<!-- #1 Button"), "debug SSR comments on every node: " + html);
        session.close();

        // Default (property off) leaves the SSR output comment-free.
        PathlandSession plain = new PathlandSession(
                "s1", STORE, () -> Button.of("Tap", () -> {}), EnvironmentData.of("/"));
        String plainHtml = plain.renderHtml();
        if (!plainHtml.contains("Pathland renderer unavailable")) {
            assertTrue(!plainHtml.contains("<!--"), "debug comments are opt-in: " + plainHtml);
        }
        plain.close();
    }

    @Test
    void registryPropagatesDebugHtmlFlag() {
        PathlandRegistry debug = new PathlandRegistry(() -> Button.of("Tap", () -> {}), STORE, true);
        String html = debug.renderHtml("/");
        if (!html.contains("Pathland renderer unavailable")) {
            assertTrue(html.contains("<!-- #1 Button"), "registry flag reaches SSR: " + html);
        }
        debug.shutdown();
    }

    @Test
    void twoWindowsWithTheSameWindowIdNeverShareASession() {
        // Two browser windows of one app present the SAME wid (persisted-state scope) but
        // must get SEPARATE UI models: the registry keys sessions by the per-connection
        // uiId, not the shared window id.
        PathlandApp app = () -> {
            WritableSignal<String> signal = Signals.signal("no");
            return Button.of(Text.of(signal), () -> signal.set("yes"));
        };
        PathlandRegistry registry = new PathlandRegistry(app, STORE);
        RecordingConnection windowA = new RecordingConnection();
        RecordingConnection windowB = new RecordingConnection();
        registry.open("ui-a", "shared-wid", windowA);
        registry.open("ui-b", "shared-wid", windowB);
        registry.environment("ui-a", EnvironmentData.of("/"));
        registry.environment("ui-b", EnvironmentData.of("/"));

        // Tap window A's button (node 1): it re-emits its bound label text — to A only.
        registry.dispatch("ui-a", FrameCodec.encodeEvents(List.of(Event.pointerUp(1, 0, 0))));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (windowA.sent == 0 && System.nanoTime() < deadline) {
            sleep(10);
        }
        registry.shutdown();
        assertTrue(windowA.sent > 0, "window A's session received a delta");
        assertTrue(windowB.sent == 0, "window B's session is untouched by A's events");
    }

    @Test
    void navigateEventUrlsAreMountStrippedBeforeReachingTheApp() {
        // A mounted app's browser back/forward sends the real URL (`http://host/app2/…`);
        // the app's router is mount-relative, so it must see the stripped route.
        PathlandApp app = () -> new View() {
            @Override
            public PathlandNode render(Environment env) {
                return Text.of(Environment.value(Platform.ACTIVE_PATH)).render(env);
            }
        };
        PathlandRegistry registry = new PathlandRegistry("/app2", app, STORE, false);
        RecordingConnection conn = new RecordingConnection();
        registry.open("ui", "w1", conn);
        registry.environment("ui", EnvironmentData.of("/app2/home"));

        registry.dispatch("ui", FrameCodec.encodeEvents(List.of(
                Event.navigate("http://host/app2/settings?wid=w1"))));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!framesContainText(conn, "/settings") && System.nanoTime() < deadline) {
            sleep(10);
        }
        registry.shutdown();
        assertTrue(framesContainText(conn, "/settings"),
                "the app's router sees the mount-stripped NAVIGATE route: " + describe(conn));
        assertFalse(framesContainText(conn, "app2"),
                "the mount prefix never reaches the app's route: " + describe(conn));
    }

    private static boolean framesContainText(RecordingConnection conn, String text) {
        for (byte[] bytes : conn.frames()) {
            try {
                Frame frame = FrameCodec.decodeFrame(bytes);
                for (Opcode op : frame.opcodes()) {
                    if (op.category() == Categories.PARAMETER && op.command() == Commands.Parameter.SET_TEXT) {
                        if (text.equals(frame.stringAt(op.b()))) {
                            return true;
                        }
                    }
                }
            } catch (RuntimeException ignored) {
                // a partial/undecodable batch — keep scanning
            }
        }
        return false;
    }

    private static String describe(RecordingConnection conn) {
        StringBuilder sb = new StringBuilder();
        for (byte[] bytes : conn.frames()) {
            sb.append(FrameCodec.decodeFrame(bytes)).append(" ");
        }
        return sb.toString();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** A connection that records sends (delivered from the actor thread). */
    private static final class RecordingConnection implements PathlandConnection {
        private final List<byte[]> recorded = new java.util.concurrent.CopyOnWriteArrayList<>();
        volatile int sent;

        List<byte[]> frames() {
            return recorded;
        }

        @Override
        public void send(byte[] bytes) {
            recorded.add(bytes);
            sent++;
        }

        @Override
        public boolean isOpen() {
            return true;
        }
    }

    /** A connection that swallows sends (the socket adapters exercise real sends). */
    private static final class NoopConnection implements PathlandConnection {
        @Override
        public void send(byte[] bytes) {
            // no-op
        }

        @Override
        public boolean isOpen() {
            return true;
        }
    }

    /** A connection whose send always fails (an overflowing/remote send path). */
    private static final class FailingConnection implements PathlandConnection {
        int attempts;

        @Override
        public void send(byte[] bytes) {
            attempts++;
            throw new RuntimeException("send failed");
        }

        @Override
        public boolean isOpen() {
            return true;
        }
    }
}