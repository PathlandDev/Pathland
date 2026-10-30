package dev.pathland.server.proxy;

import dev.pathland.render.html.HtmlRenderer;
import dev.pathland.server.PathlandApp;
import dev.pathland.server.PathlandConnection;
import dev.pathland.server.PathlandSession;
import dev.pathland.server.PathlandTelemetry;
import dev.pathland.view.Categories;
import dev.pathland.view.Commands;
import dev.pathland.view.ProxyView;
import dev.pathland.view.Text;
import dev.pathland.view.VStack;
import dev.pathland.view.View;
import dev.pathland.view.emit.Opcode;
import dev.pathland.view.emit.ProtocolFrame;
import dev.pathland.view.signal.Signals;
import dev.pathland.view.signal.WritableSignal;
import dev.pathland.view.state.InMemoryStateStore;
import dev.pathland.view.transport.EnvironmentData;
import dev.pathland.view.transport.Event;
import dev.pathland.view.transport.FrameCodec;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The session-level proxy wiring (spec/PROXY.md): a {@code ProxyView} in the app
 * tree opens an origin connection on the live connection, the origin's snapshot
 * is remapped + forwarded to the client, and client events in the proxy's range
 * route back to the origin.
 */
class ProxyIntegrationTest {

    @Test
    void aProxyViewOpensAnOriginConnectionAndRoutesItsEvents() throws Exception {
        AtomicReference<ProxySessionTest.FakeTransport> originRef = new AtomicReference<>();
        PathlandApp app = () -> VStack.children(
                Text.make("local"),
                ProxyView.make(p -> p.endpoint("http://b.example").route("/x")));

        PathlandSession session = new PathlandSession(
                "s1", new InMemoryStateStore(), app, EnvironmentData.of("/"),
                false, "/_pathland", "scope", PathlandTelemetry.NOOP, "/");
        session.proxyTransportFactory(() -> {
            ProxySessionTest.FakeTransport t = new ProxySessionTest.FakeTransport();
            originRef.set(t);
            return t;
        });

        RecordingConnection client = new RecordingConnection();
        session.connect(client);

        ProxySessionTest.FakeTransport origin = originRef.get();
        assertNotNull(origin, "the PROXY_VIEW opened an origin connection");
        assertTrue(FrameCodec.isEnvironment(origin.sent.get(0)), "origin environment is sent first");
        assertTrue(FrameCodec.isResync(origin.sent.get(1)), "then the snapshot request");

        // The origin's snapshot is remapped into the proxy block and forwarded to the client.
        origin.deliver(FrameCodec.encodeFrame(new ProtocolFrame(List.of(
                new Opcode(Categories.TREE, Commands.Tree.CREATE_NODE, 0, 1, 0x10, 0),
                new Opcode(Categories.TREE, Commands.Tree.CREATE_NODE, 0, 2, 0x01, 0),
                new Opcode(Categories.TREE, Commands.Tree.INSERT_CHILD, 0, 1, 2, -1)), new byte[0])));

        int base = ProxyIdMap.base(0);
        awaitTrue(() -> !client.frames().isEmpty());
        ProtocolFrame forwarded = FrameCodec.decodeFrame(client.frames().get(client.frames().size() - 1));
        assertTrue(forwarded.opcodes().stream().anyMatch(o -> o.category() == Categories.TREE
                        && o.command() == Commands.Tree.CREATE_NODE && o.a() == base + 2),
                "the origin subtree reaches the client remapped into the proxy block");
        assertTrue(forwarded.opcodes().stream().anyMatch(o -> o.category() == Categories.TREE
                        && o.command() == Commands.Tree.INSERT_CHILD && o.b() == base + 1),
                "the origin root is attached under the proxy node");

        // A client event targeting the remapped range routes to the origin (id unmapped back).
        session.dispatch(FrameCodec.encodeEvents(List.of(Event.pointerUp(base + 2, 1f, 2f))));
        List<Event> toOrigin = FrameCodec.decodeEvents(origin.sent.get(origin.sent.size() - 1));
        assertEquals(2, toOrigin.get(0).target(), "the event target is unmapped to the origin id");

        session.close();
    }

    @Test
    void ssrEmbedsTheOriginSubtreeIntoOneDocument() {
        byte[] originSnapshot = FrameCodec.encodeFrame(new ProtocolFrame(List.of(
                new Opcode(Categories.TREE, Commands.Tree.CREATE_NODE, 0, 1, 0x10, 0),
                new Opcode(Categories.TREE, Commands.Tree.CREATE_NODE, 0, 2, 0x01, 0),
                new Opcode(Categories.TREE, Commands.Tree.INSERT_CHILD, 0, 1, 2, -1),
                new Opcode(Categories.PARAMETER, Commands.Parameter.SET_TEXT, 0, 2, 0, 0)),
                stringSection("origin-text")));
        assumeTrue(HtmlRenderer.tryInstance() != null, "the Rust HTML renderer is available");

        PathlandApp app = () -> VStack.children(
                Text.make("local"),
                ProxyView.make(p -> p.endpoint("http://b.example")));
        PathlandSession session = new PathlandSession(
                "s1", new InMemoryStateStore(), app, EnvironmentData.of("/"),
                false, "/_pathland", "scope", PathlandTelemetry.NOOP, "/");
        session.proxyTransportFactory(() -> new ReplayingTransport(originSnapshot));

        String html = session.renderHtml();
        session.close();

        int base = ProxyIdMap.base(0);
        assertTrue(html.contains("data-pathland-id=\"" + (base + 2) + "\""),
                "the origin subtree is spliced into the SSR document with a remapped id");
        assertTrue(html.contains("origin-text"), "the origin's text is rendered in the document");
    }

    private static byte[] stringSection(String entry) {
        byte[] bytes = entry.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(bytes.length & 0xFF);
        out.write((bytes.length >>> 8) & 0xFF);
        out.write((bytes.length >>> 16) & 0xFF);
        out.write((bytes.length >>> 24) & 0xFF);
        out.writeBytes(bytes);
        return out.toByteArray();
    }

    @Test
    void aProxyThatLeavesTheRetainedTreeClosesItsOriginAndReturnsOnReAdd() {
        // A PROXY_VIEW inside a structural slot (e.g. the wide candidate of a
        // SizeThatFits slot): when the slot swaps it out the origin connection must
        // close; re-adding it opens a fresh one (spec/PROXY.md §7).
        WritableSignal<Boolean> show = Signals.signal(true);
        PathlandApp app = () -> VStack.children(View.of(() -> show.get()
                ? (View) VStack.children(ProxyView.make(p -> p.endpoint("http://b.example")))
                : (View) Text.make("no ad")));

        PathlandSession session = new PathlandSession(
                "s1", new InMemoryStateStore(), app, EnvironmentData.of("/"),
                false, "/_pathland", "scope", PathlandTelemetry.NOOP, "/");
        List<ProxySessionTest.FakeTransport> transports = new ArrayList<>();
        session.proxyTransportFactory(() -> {
            ProxySessionTest.FakeTransport t = new ProxySessionTest.FakeTransport();
            transports.add(t);
            return t;
        });
        session.connect(new RecordingConnection());

        assertEquals(1, transports.size(), "the proxy node opened an origin connection");

        // The proxy node leaves the tree (a structural reconcile) → the origin closes.
        show.set(false);
        assertFalse(transports.get(0).isOpen(), "the origin connection closed when its node left the tree");

        // Re-added → a fresh origin connection opens.
        show.set(true);
        assertEquals(2, transports.size(), "a fresh origin connection opens when the node returns");
        assertTrue(transports.get(1).isOpen());

        session.close();
    }

    private static void awaitTrue(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }

    /** An origin transport that answers {@code META::RESYNC} with a canned snapshot, synchronously. */
    private static final class ReplayingTransport implements ProxyTransport {

        private final byte[] snapshot;
        volatile ProxyTransport.Listener listener;

        ReplayingTransport(byte[] snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public void connect(URI uri, Map<String, String> headers, Listener listener) {
            this.listener = listener;
            listener.onOpen();
        }

        @Override
        public void send(byte[] bytes) {
            if (FrameCodec.isResync(bytes)) {
                listener.onBinary(snapshot);
            }
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public void close() {
            // no-op
        }
    }

    /** A client connection that records the batches the session sends. */
    private static final class RecordingConnection implements PathlandConnection {
        private final List<byte[]> recorded = new CopyOnWriteArrayList<>();

        List<byte[]> frames() {
            return recorded;
        }

        @Override
        public void send(byte[] bytes) {
            recorded.add(bytes);
        }

        @Override
        public boolean isOpen() {
            return true;
        }
    }
}
