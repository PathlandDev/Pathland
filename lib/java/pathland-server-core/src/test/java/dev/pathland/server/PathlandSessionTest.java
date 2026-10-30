package dev.pathland.server;

import dev.pathland.view.Button;
import dev.pathland.view.MediaController;
import dev.pathland.view.Categories;
import dev.pathland.view.Commands;
import dev.pathland.view.Environment;
import dev.pathland.view.Platform;
import dev.pathland.view.Properties;
import dev.pathland.view.Slider;
import dev.pathland.view.Text;
import dev.pathland.view.VStack;
import dev.pathland.view.View;
import dev.pathland.view.emit.ProtocolFrame;
import dev.pathland.view.emit.Opcode;
import dev.pathland.view.emit.PathlandNode;
import dev.pathland.view.signal.Signal;
import dev.pathland.view.signal.Signals;
import dev.pathland.view.signal.WritableSignal;
import dev.pathland.view.state.InMemoryStateStore;
import dev.pathland.view.state.StateStore;
import dev.pathland.view.transport.EnvironmentData;
import dev.pathland.view.transport.Event;
import dev.pathland.view.transport.FrameCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
        PathlandApp app = () -> Button.make(b -> b.title("Tap").action(taps::incrementAndGet));
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
        PathlandApp app = () -> Button.make(b -> b.title("Tap").action(() -> {}));
        PathlandSession session = new PathlandSession("s1", STORE, app, EnvironmentData.of("/"));

        session.applyEnvironment(EnvironmentData.of("/kitchen"));
        session.resync();
        session.close(); // idempotent
        session.close();
    }

    @Test
    void renderHtmlEmitsTheServiceWorkerFlagOnlyWhenEnabled() {
        // Issue #155: the server opts the DOM client into the per-window-id service
        // worker via a <html> attribute; absent -> the client unregisters (kill switch).
        assumeTrue(dev.pathland.render.html.HtmlRenderer.tryInstance() != null, "the Rust HTML renderer is available");
        PathlandApp app = () -> Button.make(b -> b.title("Tap").action(() -> {}));
        PathlandSession session = new PathlandSession("s1", STORE, app, EnvironmentData.of("/"));

        assertFalse(session.renderHtml(false).contains("data-pathland-service-worker"), "off by default");
        assertTrue(session.renderHtml(true).contains("data-pathland-service-worker=\"true\""), "emitted when enabled");
        assertFalse(session.renderHtml(true, false).contains("data-pathland-ssr-scoped"), "unscoped by default");
        assertTrue(session.renderHtml(true, true).contains("data-pathland-ssr-scoped=\"true\""), "marked when scoped");

        session.close();
    }

    @Test
    void renderHtmlLinksTheContentHashedBaseStylesheet() {
        assumeTrue(dev.pathland.render.html.HtmlRenderer.tryInstance() != null,
                "the Rust HTML renderer is available");
        PathlandApp app = () -> Button.make(b -> b.title("Tap").action(() -> {}));
        PathlandSession session = new PathlandSession(
                "s1", STORE, app, EnvironmentData.of("/"), false, "/app2/_pathland", "s1");
        String name = dev.pathland.render.html.HtmlRenderer.instance().baseCssName();
        assertFalse(name.isEmpty(), "the renderer exposes a content-hashed CSS name");

        String html = session.renderHtml();
        assertTrue(html.contains("<link rel=\"stylesheet\" href=\"/app2/_pathland/" + name + "\">"),
                "SSR links the base stylesheet under the app base: " + html);
        assertFalse(html.contains("<style>"), "the base design is no longer inlined: " + html);

        session.close();
    }

    @Test
    void dropsFailedConnectionAndRecoversOnReconnect() {
        PathlandApp app = () -> Button.make(b -> b.title("Tap").action(() -> {}));
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
        PathlandRegistry registry = new PathlandRegistry(() -> Button.make(b -> b.title("Tap").action(() -> {})), STORE);
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
            public PathlandNode renderNode(Environment env) {
                return Text.make(t -> t.text(env.state().signal("greeting", "hi"))).renderNode(env);
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
                "s1", STORE, () -> Button.make(b -> b.title("Tap").action(() -> {})), EnvironmentData.of("/"), true);
        String html = session.renderHtml();
        if (html.contains("Pathland renderer unavailable")) {
            return; // dylib not on java.library.path in this test JVM — nothing to assert
        }
        assertTrue(html.contains("<!-- #1 Button"), "debug SSR comments on every node: " + html);
        session.close();

        // Default (property off) leaves the SSR output comment-free.
        PathlandSession plain = new PathlandSession(
                "s1", STORE, () -> Button.make(b -> b.title("Tap").action(() -> {})), EnvironmentData.of("/"));
        String plainHtml = plain.renderHtml();
        if (!plainHtml.contains("Pathland renderer unavailable")) {
            assertTrue(!plainHtml.contains("<!--"), "debug comments are opt-in: " + plainHtml);
        }
        plain.close();
    }

    @Test
    void registryPropagatesDebugHtmlFlag() {
        PathlandRegistry debug = new PathlandRegistry(() -> Button.make(b -> b.title("Tap").action(() -> {})), STORE, true);
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
            return Button.make(b -> b.action(() -> signal.set("yes")))
                    .children(Text.make(t -> t.text(signal)));
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
            public PathlandNode renderNode(Environment env) {
                return Text.make(t -> t.text(Environment.value(Platform.ACTIVE_PATH))).renderNode(env);
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

    @Test
    void mediaTimeUpdateReachesTheSeekBarOverTheLiveConnection() {
        // Reproduces the LIVE path (session -> fine-grained emitter -> DeltaBatcher ->
        // connection): a MEDIA_TIME_UPDATED routed to the controller node must produce
        // a seek-bar VALUE delta on the wire. If this passes but the browser's bar
        // doesn't move, the bug is client-side; if it fails, it's server-side.
        WritableSignal<Float> position = Signals.signal(0f);
        MediaController player = new MediaController();
        player.source("/track.mp3");
        player.time(position);
        PathlandApp app = () -> VStack.make().children(
                player.attach(),
                Slider.make(sl -> sl.value(position).in(0f, 100f)));

        PathlandSession session = new PathlandSession("ui", STORE, app, EnvironmentData.of("/"));
        RecordingConnection conn = new RecordingConnection();
        session.connect(conn);

        String html = session.renderHtml();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("data-pathland-id=\"(\\d+)\" aria-hidden=\"true\" style=\"position:absolute")
                .matcher(html);
        assertTrue(matcher.find(), "the controller shell is in the SSR: " + html);
        int controllerId = Integer.parseInt(matcher.group(1));

        session.dispatch(FrameCodec.encodeEvents(List.of(Event.mediaTimeUpdated(controllerId, 30f))));

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!framesHaveValue(conn) && System.nanoTime() < deadline) {
            sleep(10);
        }
        session.close();
        assertTrue(position.get() == 30f, "the media report reached the bound signal");
        assertTrue(framesHaveValue(conn), "a seek-bar VALUE delta is sent: " + describe(conn));
    }

    private static boolean framesHaveValue(RecordingConnection conn) {
        for (byte[] bytes : conn.frames()) {
            try {
                ProtocolFrame frame = FrameCodec.decodeFrame(bytes);
                for (Opcode op : frame.opcodes()) {
                    if (op.category() == Categories.PARAMETER
                            && op.command() == Commands.Parameter.SET_PROPERTY
                            && (op.b() & 0xFFFF) == Properties.VALUE) {
                        return true;
                    }
                }
            } catch (RuntimeException ignored) {
                // a partial/undecodable batch — keep scanning
            }
        }
        return false;
    }

    private static boolean framesContainText(RecordingConnection conn, String text) {
        for (byte[] bytes : conn.frames()) {
            try {
                ProtocolFrame frame = FrameCodec.decodeFrame(bytes);
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